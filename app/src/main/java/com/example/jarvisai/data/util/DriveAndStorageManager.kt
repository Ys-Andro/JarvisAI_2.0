package com.example.jarvisai.data.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class DocumentDetails(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val isFromCloud: Boolean,
    val mimeType: String?
)

data class CopyProgress(
    val bytesCopied: Long,
    val totalBytes: Long,
    val percentage: Int,
    val isCompleted: Boolean = false
)

object DriveAndStorageManager {
    private const val TAG = "DriveAndStorageManager"
    private const val CACHE_SUBDIR = "models/cache"
    private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer for fast copying of large files

    fun getDocumentDetails(context: Context, uri: Uri): DocumentDetails {
        var displayName = "model_${System.currentTimeMillis()}.gguf"
        var sizeBytes = 0L
        var mimeType: String? = null

        try {
            mimeType = context.contentResolver.getType(uri)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query document details for $uri", e)
        }

        val authority = uri.authority ?: ""
        val isCloud = authority.contains("google", ignoreCase = true) ||
                authority.contains("docs", ignoreCase = true) ||
                authority.contains("drive", ignoreCase = true) ||
                authority.contains("cloud", ignoreCase = true) ||
                authority.contains("dropbox", ignoreCase = true) ||
                authority.contains("onedrive", ignoreCase = true)

        return DocumentDetails(
            uri = uri,
            displayName = displayName,
            sizeBytes = sizeBytes,
            isFromCloud = isCloud,
            mimeType = mimeType
        )
    }

    suspend fun cacheModelFromUri(
        context: Context,
        uri: Uri,
        details: DocumentDetails,
        onProgress: (CopyProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Check storage space before copying
            val availableStorage = DeviceMemoryManager.getInternalStorageAvailableBytes(context)
            val requiredStorage = if (details.sizeBytes > 0) details.sizeBytes else 500L * 1024 * 1024

            if (availableStorage < (requiredStorage + 200L * 1024 * 1024)) {
                val neededStr = DeviceMemoryManager.formatBytes(requiredStorage)
                val availStr = DeviceMemoryManager.formatBytes(availableStorage)
                return@withContext Result.failure(
                    IllegalStateException(
                        "Espacio insuficiente en disco interno. Se requieren $neededStr y solo hay $availStr disponibles."
                    )
                )
            }

            val cacheDir = File(context.filesDir, CACHE_SUBDIR).apply { mkdirs() }
            val sanitizedName = details.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destinationFile = File(cacheDir, sanitizedName)

            // If destination exists and size matches, re-use
            if (destinationFile.exists() && details.sizeBytes > 0 && destinationFile.length() == details.sizeBytes) {
                onProgress(CopyProgress(details.sizeBytes, details.sizeBytes, 100, isCompleted = true))
                return@withContext Result.success(destinationFile)
            }

            val tempFile = File(cacheDir, "$sanitizedName.tmp_${System.currentTimeMillis()}")

            val nonNullStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo desde $uri"))

            nonNullStream.use { input: InputStream ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesCopied = 0L
                    var lastReportTime = 0L
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 150 || bytesCopied == details.sizeBytes) {
                            lastReportTime = now
                            val total = if (details.sizeBytes > 0) details.sizeBytes else bytesCopied
                            val percentage = if (total > 0) ((bytesCopied * 100) / total).toInt().coerceIn(0, 99) else 0
                            onProgress(CopyProgress(bytesCopied, total, percentage))
                        }
                    }
                    output.flush()
                }
            }

            // Rename temp to final
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempFile.renameTo(destinationFile)) {
                // Fallback copy if rename fails
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(CopyProgress(destinationFile.length(), destinationFile.length(), 100, isCompleted = true))
            Result.success(destinationFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error caching model from URI $uri", e)
            Result.failure(e)
        }
    }

    fun getCacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.filesDir, CACHE_SUBDIR)
        if (!cacheDir.exists()) return 0L
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun clearCache(context: Context): Long {
        val cacheDir = File(context.filesDir, CACHE_SUBDIR)
        if (!cacheDir.exists()) return 0L
        var deletedBytes = 0L
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                deletedBytes += file.length()
                file.delete()
            }
        }
        return deletedBytes
    }

    fun deleteCachedFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }
}
