package com.example.jarvisai.presentation.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.compositionContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.jarvisai.di.AppContainer
import com.example.jarvisai.presentation.JarvisViewModelFactory
import com.example.jarvisai.presentation.chat.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingBubbleService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    private val TAG = "FloatingBubbleService"
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var isExpanded by mutableStateOf(false)

    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var appContainer: AppContainer
    private lateinit var chatViewModel: ChatViewModel

    companion object {
        private const val CHANNEL_ID = "jarvis_floating_bubble_channel"
        private const val NOTIFICATION_ID = 888
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: starting bubble service")
        savedStateRegistryController.performRestore(null)
        
        appContainer = AppContainer(applicationContext)
        val factory = JarvisViewModelFactory(appContainer, applicationContext)
        chatViewModel = factory.create(ChatViewModel::class.java)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        showBubble()
    }

    private fun showBubble() {
        if (bubbleView != null) {
            Log.d(TAG, "showBubble: bubble already exists")
            return
        }
        
        val canOverlay = android.provider.Settings.canDrawOverlays(this)
        Log.d(TAG, "showBubble: overlay permission = $canOverlay")
        
        if (!canOverlay) {
            Log.e(TAG, "showBubble: cannot show bubble, overlay permission missing")
            stopSelf()
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        val composeView = object : AbstractComposeView(this) {
            @androidx.compose.runtime.Composable
            override fun Content() {
                BubbleContent(
                    isExpanded = isExpanded,
                    chatViewModel = chatViewModel,
                    onToggleExpand = { toggleExpand() },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "updateViewLayout failed", e)
                        }
                    }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        val coroutineContext = AndroidUiDispatcher.CurrentThread
        val runRecomposeScope = CoroutineScope(coroutineContext + Job())
        val recomposer = Recomposer(coroutineContext)
        composeView.compositionContext = recomposer
        runRecomposeScope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }

        bubbleView = composeView
        try {
            windowManager.addView(bubbleView, params)
            Log.d(TAG, "bubble view added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubble view", e)
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        val params = bubbleView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (isExpanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(bubbleView, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Jarvis Floating Bubble"
            val descriptionText = "Permite acceso rápido a Jarvis desde cualquier pantalla"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Flotante")
            .setContentText("Pulsa la burbuja para abrir el mini chat")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: service starting with id $startId")
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: service being destroyed")
        super.onDestroy()
        bubbleView?.let { 
            try {
                windowManager.removeView(it) 
                Log.d(TAG, "bubble view removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubble view", e)
            }
        }
    }
}
