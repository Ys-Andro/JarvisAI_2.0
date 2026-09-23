package com.example.jarvisai.presentation.bubble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.JarvisApplication
import com.example.jarvisai.di.AppContainer
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.ui.theme.JarvisAiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Foreground Service managing the Jarvis Floating Bubble Overlay via WindowManager.
 * Uses SYSTEM_ALERT_WINDOW permission and TYPE_APPLICATION_OVERLAY window type.
 *
 * Distinct visual states supported:
 * - IDLE: Standby breathing pulse with subtle arc-reactor reticle.
 * - LISTENING: Concentric radar ripples and live speech capture.
 * - THINKING: Dual high-speed rotating cybernetic energy rings during inference.
 * - SPEAKING: Reactive voice synthesizer frequency equalizer bars during TTS playback.
 */
class FloatingBubbleService : Service() {

    companion object {
        const val TAG = "FloatingBubbleService"
        const val ACTION_START = "ACTION_START_BUBBLE"
        const val ACTION_STOP = "ACTION_STOP_BUBBLE"
        const val ACTION_SET_STATE = "ACTION_SET_VISUAL_STATE"
        const val EXTRA_VISUAL_STATE = "EXTRA_VISUAL_STATE"

        private const val NOTIFICATION_ID = 9110
        private const val CHANNEL_ID = "jarvis_floating_bubble_channel"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var lifecycleOwner: OverlayLifecycleOwner

    // Overlay Views
    private var bubbleComposeView: ComposeView? = null
    private var miniChatComposeView: ComposeView? = null
    private var dismissTargetComposeView: ComposeView? = null

    // WindowManager Layout Params
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var miniChatParams: WindowManager.LayoutParams
    private lateinit var dismissTargetParams: WindowManager.LayoutParams

    private val appContainer: AppContainer by lazy {
        (application as? JarvisApplication)?.appContainer ?: AppContainer(applicationContext)
    }

    // Interactive & Visual States
    private var visualState by mutableStateOf(JarvisVisualState.IDLE)
    private var isMuted by mutableStateOf(false)
    private var isHaloExpanded by mutableStateOf(false)
    private var isMiniChatVisible by mutableStateOf(false)
    private var isDraggingBubble by mutableStateOf(false)
    private var isOverDismissTarget by mutableStateOf(false)

    // Chat State for Floating Mini Card
    private val miniChatMessages = mutableStateListOf<MiniChatMessage>()
    private var miniChatInputText by mutableStateOf("")

    // Speech Recognizer for voice input directly from bubble
    private var speechRecognizer: SpeechRecognizer? = null

    // Screen Dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Mandatory immediate foreground start to satisfy Android 8+ foreground service requirements
        startForegroundNotification()

        // 2. Mandatory permission check for SYSTEM_ALERT_WINDOW
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW permission is not granted. Cannot start overlay.")
            FloatingBubbleManager.setServiceActive(false)
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()

        lifecycleOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
            onResume()
        }

        FloatingBubbleManager.setServiceActive(true)

        // Observe global visual state changes to synchronize with bubble
        serviceScope.launch {
            FloatingBubbleManager.visualState.collect { newState ->
                visualState = newState
            }
        }

        initSpeechRecognizer()
        setupViews()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "SYSTEM_ALERT_WINDOW permission missing in onStartCommand. Stopping service.")
            FloatingBubbleManager.setServiceActive(false)
            stopSelf()
            return START_NOT_STICKY
        }

        FloatingBubbleManager.setServiceActive(true)

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (bubbleComposeView == null || bubbleComposeView?.isAttachedToWindow != true) {
                    setupViews()
                }
            }
            ACTION_SET_STATE -> {
                val stateName = intent.getStringExtra(EXTRA_VISUAL_STATE)
                stateName?.let {
                    try {
                        val state = JarvisVisualState.valueOf(it)
                        updateVisualState(state)
                    } catch (_: Exception) {}
                }
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenDimensions()
        clampBubblePosition()
    }

    private fun updateScreenDimensions() {
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    private fun clampBubblePosition() {
        if (!::bubbleParams.isInitialized) return
        val margin = (12 * resources.displayMetrics.density).toInt()
        bubbleParams.x = bubbleParams.x.coerceIn(margin, (screenWidth - bubbleParams.width - margin).coerceAtLeast(margin))
        bubbleParams.y = bubbleParams.y.coerceIn(margin, (screenHeight - bubbleParams.height - margin).coerceAtLeast(margin))
        try {
            if (bubbleComposeView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(bubbleComposeView, bubbleParams)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error updating bubble layout on config change", e)
        }
    }

    private fun updateVisualState(state: JarvisVisualState) {
        visualState = state
        FloatingBubbleManager.setVisualState(state)
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Asistente Flotante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio en primer plano para la burbuja flotante y mini chat de Jarvis"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS AI • Activo")
            .setContentText("Burbuja flotante lista. Toca para abrir la aplicación.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        if (bubbleComposeView != null && bubbleComposeView?.isAttachedToWindow == true) {
            return
        }

        val displayMetrics = resources.displayMetrics

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialBubbleSize = (90 * displayMetrics.density).toInt()
        bubbleParams = WindowManager.LayoutParams(
            initialBubbleSize,
            initialBubbleSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * displayMetrics.density).toInt()
            y = screenHeight / 3
        }

        bubbleComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                JarvisAiTheme {
                    FloatingBubbleOrb(
                        visualState = visualState,
                        isMuted = isMuted,
                        isHaloExpanded = isHaloExpanded,
                        onTap = { toggleMiniChat() },
                        onDoubleTap = { launchLiveMode() },
                        onLongPress = { toggleHalo() },
                        onActionClick = { action -> handleBubbleAction(action) },
                        onDismissHalo = {
                            isHaloExpanded = false
                            resizeBubbleWindow(false)
                        }
                    )
                }
            }
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isHaloExpanded) {
                    isHaloExpanded = false
                    resizeBubbleWindow(false)
                } else {
                    toggleMiniChat()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                launchLiveMode()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                toggleHalo()
            }
        })

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMovementSignificant = false

        bubbleComposeView?.setOnTouchListener { _, event ->
            if (isHaloExpanded) {
                // When halo is expanded, permit Compose to handle satellite buttons and backdrop tap
                return@setOnTouchListener false
            }

            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMovementSignificant = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (!isMovementSignificant && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isMovementSignificant = true
                        isDraggingBubble = true
                        if (isHaloExpanded) {
                            isHaloExpanded = false
                            resizeBubbleWindow(false)
                        }
                    }

                    if (isMovementSignificant) {
                        bubbleParams.x = initialX + deltaX
                        bubbleParams.y = initialY + deltaY
                        try {
                            if (bubbleComposeView?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(bubbleComposeView, bubbleParams)
                            }
                        } catch (_: Exception) {}

                        // Calculate proximity to bottom dismiss target
                        val targetCenterX = screenWidth / 2
                        val targetCenterY = screenHeight - (90 * displayMetrics.density).toInt()
                        val currentCenterX = bubbleParams.x + (bubbleParams.width / 2)
                        val currentCenterY = bubbleParams.y + (bubbleParams.height / 2)

                        val distance = hypot(
                            (currentCenterX - targetCenterX).toDouble(),
                            (currentCenterY - targetCenterY).toDouble()
                        )
                        isOverDismissTarget = distance < (95 * displayMetrics.density)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isMovementSignificant) {
                        isDraggingBubble = false

                        // Dropped on dismiss target
                        if (isOverDismissTarget) {
                            stopSelf()
                            return@setOnTouchListener true
                        }

                        // Snap to nearest screen edge (left or right)
                        val currentX = bubbleParams.x
                        val margin = (12 * displayMetrics.density).toInt()
                        val targetX = if (currentX + (bubbleParams.width / 2) < screenWidth / 2) {
                            margin
                        } else {
                            screenWidth - bubbleParams.width - margin
                        }

                        bubbleParams.x = targetX
                        // Keep Y inside screen boundaries
                        val minY = margin
                        val maxY = screenHeight - bubbleParams.height - (margin * 3)
                        bubbleParams.y = bubbleParams.y.coerceIn(minY, maxY)

                        try {
                            if (bubbleComposeView?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(bubbleComposeView, bubbleParams)
                            }
                        } catch (_: Exception) {}
                    }
                    isOverDismissTarget = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(bubbleComposeView, bubbleParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding bubble view to windowManager", e)
        }

        // Setup Dismiss Target View (bottom center)
        val dismissTargetSize = (110 * displayMetrics.density).toInt()
        dismissTargetParams = WindowManager.LayoutParams(
            dismissTargetSize,
            dismissTargetSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * displayMetrics.density).toInt()
        }

        dismissTargetComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                JarvisAiTheme {
                    FloatingBubbleDismissTarget(
                        isVisible = isDraggingBubble,
                        isHovered = isOverDismissTarget
                    )
                }
            }
        }

        try {
            windowManager.addView(dismissTargetComposeView, dismissTargetParams)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dismiss target view", e)
        }

        // Setup Mini Chat Card View
        val cardWidth = (340 * displayMetrics.density).toInt().coerceAtMost(screenWidth - 32)
        val cardHeight = (440 * displayMetrics.density).toInt().coerceAtMost(screenHeight - 80)

        miniChatParams = WindowManager.LayoutParams(
            cardWidth,
            cardHeight,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        miniChatComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                JarvisAiTheme {
                    if (isMiniChatVisible) {
                        MiniChatCard(
                            visualState = visualState,
                            messages = miniChatMessages,
                            inputText = miniChatInputText,
                            onInputChange = { miniChatInputText = it },
                            onSendMessage = { prompt -> askJarvis(prompt) },
                            onStartVoiceDictation = { startVoiceDictation() },
                            onStopVoiceDictation = { stopVoiceDictation() },
                            onOpenLiveMode = { launchLiveMode() },
                            onOpenFullApp = { launchFullApp() },
                            onCloseClick = { closeMiniChat() }
                        )
                    }
                }
            }
        }

        miniChatComposeView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                closeMiniChat()
                true
            } else {
                false
            }
        }
    }

    private fun resizeBubbleWindow(expandHalo: Boolean) {
        val density = resources.displayMetrics.density
        val targetSize = if (expandHalo) (200 * density).toInt() else (90 * density).toInt()
        val oldSize = bubbleParams.width
        val diff = (targetSize - oldSize) / 2

        bubbleParams.width = targetSize
        bubbleParams.height = targetSize
        bubbleParams.x = (bubbleParams.x - diff).coerceAtLeast(0)
        bubbleParams.y = (bubbleParams.y - diff).coerceAtLeast(0)

        try {
            if (bubbleComposeView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(bubbleComposeView, bubbleParams)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resizing bubble window", e)
        }
    }

    private fun toggleHalo() {
        isHaloExpanded = !isHaloExpanded
        resizeBubbleWindow(isHaloExpanded)
    }

    private fun handleBubbleAction(action: FloatingBubbleAction) {
        isHaloExpanded = false
        resizeBubbleWindow(false)

        when (action) {
            FloatingBubbleAction.OPEN_MINI_CHAT -> toggleMiniChat()
            FloatingBubbleAction.OPEN_LIVE_MODE -> launchLiveMode()
            FloatingBubbleAction.TOGGLE_MUTE -> {
                isMuted = !isMuted
                if (isMuted) {
                    serviceScope.launch { appContainer.ttsRepository.stop() }
                }
            }
            FloatingBubbleAction.CLOSE_BUBBLE -> stopSelf()
        }
    }

    private fun toggleMiniChat() {
        if (isMiniChatVisible) {
            closeMiniChat()
        } else {
            openMiniChat()
        }
    }

    private fun openMiniChat() {
        if (!isMiniChatVisible) {
            isMiniChatVisible = true
            try {
                if (miniChatComposeView?.isAttachedToWindow != true) {
                    windowManager.addView(miniChatComposeView, miniChatParams)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error adding mini chat view", e)
            }
        }
    }

    private fun closeMiniChat() {
        if (isMiniChatVisible) {
            isMiniChatVisible = false
            stopVoiceDictation()
            try {
                if (miniChatComposeView?.isAttachedToWindow == true) {
                    windowManager.removeView(miniChatComposeView)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing mini chat view", e)
            }
        }
    }

    private fun launchLiveMode() {
        closeMiniChat()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_LIVE_MODE, true)
        }
        startActivity(intent)
    }

    private fun launchFullApp() {
        closeMiniChat()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun askJarvis(prompt: String) {
        if (prompt.isBlank()) return
        miniChatInputText = ""
        miniChatMessages.add(MiniChatMessage(content = prompt, isUser = true))

        updateVisualState(JarvisVisualState.THINKING)

        serviceScope.launch {
            try {
                val settings = appContainer.settingsRepository.getSettings().first()

                val assistantMessage = MiniChatMessage(content = "", isUser = false)
                withContext(Dispatchers.Main) {
                    miniChatMessages.add(assistantMessage)
                }

                val sb = StringBuilder()
                appContainer.inferenceRepository.generateCompletionStream(
                    prompt = prompt,
                    conversationHistory = emptyList(),
                    settings = settings
                ).collect { chunk ->
                    sb.append(chunk)
                    withContext(Dispatchers.Main) {
                        val index = miniChatMessages.indexOfFirst { it.id == assistantMessage.id }
                        if (index != -1) {
                            miniChatMessages[index] = assistantMessage.copy(content = sb.toString())
                        }
                    }
                }

                val fullResponse = sb.toString()
                withContext(Dispatchers.Main) {
                    if (!isMuted && settings.autoTts) {
                        speakResponse(fullResponse, settings)
                    } else {
                        updateVisualState(JarvisVisualState.IDLE)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateVisualState(JarvisVisualState.ERROR)
                    miniChatMessages.add(
                        MiniChatMessage(
                            content = "Error: ${e.localizedMessage ?: "Fallo al procesar"}",
                            isUser = false
                        )
                    )
                    mainHandler.postDelayed({
                        updateVisualState(JarvisVisualState.IDLE)
                    }, 2500)
                }
            }
        }
    }

    private fun speakResponse(text: String, settings: GenerationSettings) {
        updateVisualState(JarvisVisualState.SPEAKING)

        serviceScope.launch {
            appContainer.ttsRepository.speak(
                text = text,
                pitch = settings.ttsPitch,
                speed = settings.ttsSpeed
            )
            val estimatedDurationMs = ((text.length * 55) / settings.ttsSpeed).toLong().coerceIn(1200L, 8000L)
            mainHandler.postDelayed({
                if (visualState == JarvisVisualState.SPEAKING) {
                    updateVisualState(JarvisVisualState.IDLE)
                }
            }, estimatedDurationMs)
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        updateVisualState(JarvisVisualState.LISTENING)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        updateVisualState(JarvisVisualState.IDLE)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            miniChatInputText = text
                        }
                        updateVisualState(JarvisVisualState.IDLE)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            miniChatInputText = text
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startVoiceDictation() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            updateVisualState(JarvisVisualState.LISTENING)
        } catch (e: Exception) {
            Log.w(TAG, "Error starting voice dictation", e)
            updateVisualState(JarvisVisualState.IDLE)
        }
    }

    private fun stopVoiceDictation() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        if (visualState == JarvisVisualState.LISTENING) {
            updateVisualState(JarvisVisualState.IDLE)
        }
    }

    private fun safeRemoveView(view: View?) {
        if (view != null && view.isAttachedToWindow) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing view", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingBubbleManager.setServiceActive(false)
        FloatingBubbleManager.setVisualState(JarvisVisualState.IDLE)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground notification", e)
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        serviceScope.cancel()

        safeRemoveView(bubbleComposeView)
        safeRemoveView(miniChatComposeView)
        safeRemoveView(dismissTargetComposeView)

        bubbleComposeView = null
        miniChatComposeView = null
        dismissTargetComposeView = null

        lifecycleOwner.onDestroy()
    }
}
