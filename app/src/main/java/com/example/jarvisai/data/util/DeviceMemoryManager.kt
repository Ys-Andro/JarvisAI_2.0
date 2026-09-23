package com.example.jarvisai.data.util

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.util.Log

data class MemoryCheckResult(
    val isSafeToLoad: Boolean,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val estimatedRequiredBytes: Long,
    val warningMessage: String? = null,
    val errorMessage: String? = null
)

object DeviceMemoryManager {
    private const val TAG = "DeviceMemoryManager"
    private const val OS_RESERVED_RAM_BYTES = 500L * 1024 * 1024 // 500 MB reserve for OS & apps

    fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    fun checkMemoryForModel(
        context: Context,
        modelSizeBytes: Long,
        contextLength: Int = 2048
    ): MemoryCheckResult {
        val memoryInfo = getMemoryInfo(context)
        val totalRam = memoryInfo.totalMem
        val availableRam = memoryInfo.availMem

        // Estimated RAM needed = Model weight size + KV Cache buffer (~150MB per 2048 ctx)
        val kvCacheEstimate = (contextLength.toLong() * 1024 * 1024 / 2048) * 150L
        val estimatedNeeded = modelSizeBytes + kvCacheEstimate.coerceAtLeast(100L * 1024 * 1024)

        val formattedNeeded = formatBytes(estimatedNeeded)
        val formattedAvail = formatBytes(availableRam)
        val formattedTotal = formatBytes(totalRam)

        Log.i(TAG, "Memory check: Model needs $formattedNeeded, Available RAM: $formattedAvail / Total: $formattedTotal")

        if (memoryInfo.lowMemory) {
            return MemoryCheckResult(
                isSafeToLoad = false,
                totalRamBytes = totalRam,
                availableRamBytes = availableRam,
                estimatedRequiredBytes = estimatedNeeded,
                errorMessage = "Dispositivo en estado de baja memoria crítica (Low RAM flag). No es seguro cargar este modelo."
            )
        }

        // Hard threshold: if estimated model size exceeds available RAM minus OS buffer
        if (estimatedNeeded > (availableRam - OS_RESERVED_RAM_BYTES)) {
            val shortfall = estimatedNeeded - (availableRam - OS_RESERVED_RAM_BYTES)
            val formattedShortfall = formatBytes(shortfall)
            return MemoryCheckResult(
                isSafeToLoad = false,
                totalRamBytes = totalRam,
                availableRamBytes = availableRam,
                estimatedRequiredBytes = estimatedNeeded,
                errorMessage = "Memoria RAM insuficiente: el modelo requiere aprox. $formattedNeeded, pero solo hay $formattedAvail libres (faltan ~$formattedShortfall). Cierra otras aplicaciones o usa un modelo más ligero (ej. 1B o cuantización Q4_K_M)."
            )
        }

        // Warning threshold if model uses more than 75% of currently available RAM
        val isTight = estimatedNeeded > (availableRam * 0.75)
        val warning = if (isTight) {
            "Aviso: El modelo consumirá gran parte de la memoria libre ($formattedNeeded de $formattedAvail). Podría ralentizar el sistema."
        } else null

        return MemoryCheckResult(
            isSafeToLoad = true,
            totalRamBytes = totalRam,
            availableRamBytes = availableRam,
            estimatedRequiredBytes = estimatedNeeded,
            warningMessage = warning
        )
    }

    fun getInternalStorageAvailableBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            context.filesDir.usableSpace
        }
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1.0) {
            String.format("%.2f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024 * 1024)
            String.format("%.1f MB", mb)
        }
    }
}
