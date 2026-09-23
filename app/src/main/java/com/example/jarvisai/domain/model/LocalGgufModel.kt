package com.example.jarvisai.domain.model

data class LocalGgufModel(
    val id: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val architecture: String = "llama",
    val quantization: String = "Unknown",
    val contextLength: Int = 2048,
    val state: GgufModelState = GgufModelState.REGISTERED,
    val isLoaded: Boolean = false,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val sourceUri: String? = null,
    val isCachedFromDrive: Boolean = false
) {
    val formattedSize: String
        get() {
            val gb = sizeBytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format("%.2f GB", gb)
            } else {
                val mb = sizeBytes.toDouble() / (1024 * 1024)
                String.format("%.1f MB", mb)
            }
        }
}
