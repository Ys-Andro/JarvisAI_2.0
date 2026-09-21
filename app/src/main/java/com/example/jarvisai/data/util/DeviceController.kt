package com.example.jarvisai.data.util

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import org.json.JSONObject

object DeviceController {
    private const val TAG = "DeviceController"

    fun executeActionCommand(context: Context, actionJsonString: String): String {
        return try {
            val json = JSONObject(actionJsonString)
            val action = json.optString("action")
            when (action.uppercase()) {
                "FLASHLIGHT" -> {
                    val enable = json.optBoolean("enable", false)
                    toggleFlashlight(context, enable)
                    if (enable) "Linterna encendida, señor." else "Linterna apagada, señor."
                }
                "SET_ALARM" -> {
                    val hour = json.optInt("hour", 7)
                    val minute = json.optInt("minute", 0)
                    val message = json.optString("message", "Alarma de Jarvis")
                    setAlarm(context, hour, minute, message)
                    tagAlarmSet(hour, minute)
                }
                "OPEN_APP" -> {
                    val appName = json.optString("appName", "")
                    openApp(context, appName)
                }
                "VOLUME" -> {
                    val level = json.optInt("level", 50)
                    setVolume(context, level)
                    "Volumen ajustado al $level%."
                }
                "CALL" -> {
                    val number = json.optString("number", "")
                    makeCall(context, number)
                    "Abriendo marcador para $number..."
                }
                "WEB_SEARCH" -> {
                    val query = json.optString("query", "")
                    openWebSearch(context, query)
                    "Buscando '$query' en la web..."
                }
                "HOME" -> {
                    val success = JarvisAccessibilityService.instance?.goHome() == true
                    if (success) "Viendo pantalla de inicio, señor." else "Para usar esta función, active el Servicio de Accesibilidad de Jarvis en Ajustes > Accesibilidad."
                }
                "BACK" -> {
                    val success = JarvisAccessibilityService.instance?.goBack() == true
                    if (success) "Regresando..." else "Active el Servicio de Accesibilidad de Jarvis."
                }
                "NOTIFICATIONS" -> {
                    val success = JarvisAccessibilityService.instance?.openNotifications() == true
                    if (success) "Abriendo barra de notificaciones, señor." else "Active el Servicio de Accesibilidad de Jarvis."
                }
                else -> "Acción de dispositivo no reconocida: $action"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing device action", e)
            "Error ejecutando comando en el dispositivo: ${e.localizedMessage}"
        }
    }

    private fun toggleFlashlight(context: Context, enable: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error", e)
        }
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int, message: String) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Alarm error", e)
        }
    }

    private fun tagAlarmSet(hour: Int, minute: Int): String {
        val formattedTime = String.format("%02d:%02d", hour, minute)
        return "Alarma programada para las $formattedTime, señor."
    }

    private fun openApp(context: Context, appName: String): String {
        val pm = context.packageManager
        val query = appName.lowercase()
        val packages = pm.getInstalledPackages(0)
        
        // Find best match among installed apps
        val match = packages.firstOrNull { pkg ->
            val label = pkg.applicationInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
            label.contains(query) || pkg.packageName.lowercase().contains(query)
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val appTitle = match.applicationInfo?.loadLabel(pm)?.toString() ?: appName
                return "Abriendo $appTitle, señor."
            }
        }

        // Fallback intent by common name
        val intent = when {
            query.contains("whatsapp") -> pm.getLaunchIntentForPackage("com.whatsapp")
            query.contains("youtube") -> pm.getLaunchIntentForPackage("com.google.android.youtube")
            query.contains("spotify") -> pm.getLaunchIntentForPackage("com.spotify.music")
            query.contains("browser") || query.contains("chrome") -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            else -> null
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return "Abriendo $appName, señor."
        }

        return "No pude encontrar la aplicación '$appName' instalada en este dispositivo, señor."
    }

    private fun setVolume(context: Context, levelPercent: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
            val target = (maxVolume * (levelPercent.coerceIn(0, 100)) / 100f).toInt()
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Volume error", e)
        }
    }

    private fun makeCall(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Call error", e)
        }
    }

    private fun openWebSearch(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }
    }
}
