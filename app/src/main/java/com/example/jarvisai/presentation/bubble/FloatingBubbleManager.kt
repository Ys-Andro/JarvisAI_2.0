package com.example.jarvisai.presentation.bubble

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FloatingBubbleManager {

    private val _visualState = MutableStateFlow(JarvisVisualState.IDLE)
    val visualState: StateFlow<JarvisVisualState> = _visualState.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    fun setVisualState(state: JarvisVisualState) {
        _visualState.value = state
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
    }

    /**
     * Checks if the app has permission to draw overlays (SYSTEM_ALERT_WINDOW).
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Creates the Intent to navigate to the system settings for granting overlay permission.
     */
    fun getOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Starts the FloatingBubbleService as a foreground service.
     */
    fun startBubbleService(context: Context) {
        if (!canDrawOverlays(context)) return

        val intent = Intent(context, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Stops the FloatingBubbleService.
     */
    fun stopBubbleService(context: Context) {
        val intent = Intent(context, FloatingBubbleService::class.java).apply {
            action = FloatingBubbleService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Checks whether FloatingBubbleService is currently running.
     */
    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingBubbleService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
