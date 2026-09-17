package com.example.jarvisai.domain.model

data class LocalGgufModel(
    val id: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val quantization: String = "Unknown",
    val contextLength: Int = 2048,
    val isLoaded: Boolean = false,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
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
