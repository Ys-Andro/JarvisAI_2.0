package com.example.jarvisai.data.util

import android.util.Log
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValidGguf: Boolean,
    val version: Int = 0,
    val architecture: String = "llama",
    val modelName: String = "",
    val tensorCount: Long = 0L,
    val contextLength: Int = 2048,
    val quantization: String = "Unknown",
    val parameterEstimate: String = "",
    val errorMessage: String? = null
)

object GgufMetadataParser {
    private const val TAG = "GgufMetadataParser"
    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little-endian ASCII

    fun parseFromFile(file: File): GgufMetadata {
        if (!file.exists() || !file.canRead()) {
            return GgufMetadata(
                isValidGguf = false,
                errorMessage = "El archivo no existe o no tiene permisos de lectura: ${file.absolutePath}"
            )
        }
        return try {
            file.inputStream().use { parseFromStream(it, file.name) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GGUF from file: ${file.name}", e)
            fallbackFromFileName(file.name, e.localizedMessage)
        }
    }

    fun parseFromStream(inputStream: InputStream, fallbackFileName: String): GgufMetadata {
        return try {
            val headerBytes = ByteArray(32)
            var bytesRead = 0
            while (bytesRead < 24) {
                val read = inputStream.read(headerBytes, bytesRead, 24 - bytesRead)
                if (read == -1) break
                bytesRead += read
            }

            if (bytesRead < 24) {
                return GgufMetadata(
                    isValidGguf = false,
                    errorMessage = "Archivo demasiado corto para ser un encabezado GGUF válido."
                )
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            if (magic != GGUF_MAGIC) {
                return GgufMetadata(
                    isValidGguf = false,
                    errorMessage = "Firma mágica inválida (0x${Integer.toHexString(magic).uppercase()}). Se esperaba GGUF."
                )
            }

            val version = buffer.int
            val tensorCount = buffer.long
            val kvCount = buffer.long

            var architecture = "llama"
            var modelName = ""
            var contextLength = 2048
            var quantization = detectQuantizationFromName(fallbackFileName)

            // Read some initial KV pairs safely (limit to first 256KB to avoid massive reads on stream)
            try {
                val maxKvToRead = kvCount.coerceAtMost(64L).toInt()
                for (i in 0 until maxKvToRead) {
                    val key = readGgufString(inputStream) ?: break
                    val valueType = readUint32(inputStream) ?: break
                    when (key) {
                        "general.architecture" -> {
                            if (valueType == 8) { // string
                                architecture = readGgufString(inputStream) ?: architecture
                            } else skipValue(inputStream, valueType)
                        }
                        "general.name" -> {
                            if (valueType == 8) {
                                modelName = readGgufString(inputStream) ?: modelName
                            } else skipValue(inputStream, valueType)
                        }
                        "general.file_type" -> {
                            val ftype = readUint32(inputStream)
                            if (ftype != null && quantization == "Unknown") {
                                quantization = mapGgufFileTypeToQuant(ftype)
                            }
                        }
                        "$architecture.context_length", "general.context_length" -> {
                            contextLength = when (valueType) {
                                4, 5 -> (readUint32(inputStream) ?: 2048).toInt()
                                10, 11 -> (readUint64(inputStream) ?: 2048L).toInt()
                                else -> {
                                    skipValue(inputStream, valueType)
                                    contextLength
                                }
                            }
                        }
                        else -> skipValue(inputStream, valueType)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Completed partial KV scan: ${e.message}")
            }

            if (modelName.isBlank()) {
                modelName = cleanModelName(fallbackFileName)
            }

            val paramEstimate = estimateParametersFromTensors(tensorCount)

            GgufMetadata(
                isValidGguf = true,
                version = version,
                architecture = architecture,
                modelName = modelName,
                tensorCount = tensorCount,
                contextLength = contextLength.coerceAtLeast(512),
                quantization = quantization,
                parameterEstimate = paramEstimate
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error in GGUF stream parser: ${e.message}", e)
            fallbackFromFileName(fallbackFileName, e.localizedMessage)
        }
    }

    private fun readGgufString(input: InputStream): String? {
        val lenBytes = ByteArray(8)
        if (input.read(lenBytes) != 8) return null
        val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
        if (length < 0 || length > 4096) return null // safety limit
        val strBytes = ByteArray(length.toInt())
        var totalRead = 0
        while (totalRead < length) {
            val r = input.read(strBytes, totalRead, length.toInt() - totalRead)
            if (r == -1) break
            totalRead += r
        }
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readUint32(input: InputStream): Int? {
        val bytes = ByteArray(4)
        if (input.read(bytes) != 4) return null
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUint64(input: InputStream): Long? {
        val bytes = ByteArray(8)
        if (input.read(bytes) != 8) return null
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun skipValue(input: InputStream, type: Int) {
        val bytesToSkip: Long = when (type) {
            0, 1, 7 -> 1 // uint8, int8, bool
            2, 3 -> 2    // uint16, int16
            4, 5, 6 -> 4 // uint32, int32, float32
            10, 11, 12 -> 8 // uint64, int64, float64
            8 -> {       // string
                val strLen = readUint64(input) ?: 0L
                strLen.coerceAtMost(4096L)
            }
            9 -> {       // array
                val itemType = readUint32(input) ?: 0
                val arrayLen = readUint64(input) ?: 0L
                val sampleItemSize: Long = when (itemType) {
                    0, 1, 7 -> 1
                    2, 3 -> 2
                    4, 5, 6 -> 4
                    10, 11, 12 -> 8
                    else -> 0
                }
                sampleItemSize * arrayLen.coerceAtMost(1024L)
            }
            else -> 0
        }
        if (bytesToSkip > 0) {
            input.skip(bytesToSkip)
        }
    }

    fun detectQuantizationFromName(fileName: String): String {
        val upper = fileName.uppercase()
        val quants = listOf(
            "Q4_K_M", "Q4_K_S", "Q4_0", "Q4_1",
            "Q5_K_M", "Q5_K_S", "Q5_0", "Q5_1",
            "Q8_0", "Q8_1", "Q2_K", "Q3_K_M", "Q3_K_S", "Q3_K_L",
            "Q6_K", "IQ4_XS", "IQ4_NL", "IQ3_M", "IQ2_XXS",
            "BF16", "FP16", "FP32"
        )
        return quants.firstOrNull { upper.contains(it) } ?: "Unknown"
    }

    fun cleanModelName(fileName: String): String {
        return fileName
            .removeSuffix(".gguf")
            .removeSuffix(".bin")
            .replace(Regex("[._-]"), " ")
            .trim()
    }

    private fun mapGgufFileTypeToQuant(ftype: Int): String {
        return when (ftype) {
            0 -> "ALL_F32"
            1 -> "MOSTLY_F16"
            2 -> "MOSTLY_Q4_0"
            3 -> "MOSTLY_Q4_1"
            7 -> "MOSTLY_Q8_0"
            8 -> "MOSTLY_Q5_0"
            9 -> "MOSTLY_Q5_1"
            10 -> "MOSTLY_Q2_K"
            11 -> "MOSTLY_Q3_K_S"
            12 -> "MOSTLY_Q3_K_M"
            13 -> "MOSTLY_Q3_K_L"
            14 -> "MOSTLY_Q4_K_S"
            15 -> "MOSTLY_Q4_K_M"
            16 -> "MOSTLY_Q5_K_S"
            17 -> "MOSTLY_Q5_K_M"
            18 -> "MOSTLY_Q6_K"
            19 -> "MOSTLY_IQ2_XXS"
            20 -> "MOSTLY_IQ2_XS"
            21 -> "MOSTLY_Q2_K_S"
            22 -> "MOSTLY_IQ3_XS"
            23 -> "MOSTLY_IQ3_XXS"
            24 -> "MOSTLY_IQ1_S"
            25 -> "MOSTLY_IQ4_NL"
            26 -> "MOSTLY_IQ3_S"
            27 -> "MOSTLY_IQ3_M"
            28 -> "MOSTLY_IQ2_S"
            29 -> "MOSTLY_IQ2_M"
            30 -> "MOSTLY_IQ4_XS"
            else -> "Q4_K_M"
        }
    }

    private fun estimateParametersFromTensors(tensorCount: Long): String {
        return when {
            tensorCount <= 0 -> "Local"
            tensorCount < 150 -> "~0.5B - 1.5B"
            tensorCount < 300 -> "~3B"
            tensorCount < 400 -> "~7B - 8B"
            tensorCount < 600 -> "~13B - 14B"
            else -> ">20B"
        }
    }

    private fun fallbackFromFileName(fileName: String, errorMsg: String?): GgufMetadata {
        val isLikelyGguf = fileName.endsWith(".gguf", ignoreCase = true)
        return GgufMetadata(
            isValidGguf = isLikelyGguf,
            architecture = "llama",
            modelName = cleanModelName(fileName),
            quantization = detectQuantizationFromName(fileName),
            errorMessage = errorMsg
        )
    }
}
