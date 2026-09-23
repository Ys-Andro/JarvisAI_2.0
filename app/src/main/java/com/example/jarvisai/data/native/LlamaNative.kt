package com.example.jarvisai.data.native

import android.util.Log

fun interface NativeTokenCallback {
    fun onToken(token: String): Boolean
}

object LlamaNative {
    private const val TAG = "LlamaNative"
    private const val LIB_NAME = "llama-android"

    var isLoaded: Boolean = false
        private set

    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary(LIB_NAME)
            isLoaded = true
            Log.i(TAG, "Native library '$LIB_NAME' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            loadError = e.message
            Log.w(TAG, "Could not load native library '$LIB_NAME': ${e.message}")
        } catch (e: Exception) {
            isLoaded = false
            loadError = e.message
            Log.e(TAG, "Unexpected error loading native library '$LIB_NAME'", e)
        }
    }

    external fun nativeLoadModel(modelPath: String, contextLength: Int, threads: Int): Long
    external fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        callback: NativeTokenCallback
    ): Int
    external fun nativeFreeModel(handle: Long)
    external fun nativeGetSystemInfo(): String
}
