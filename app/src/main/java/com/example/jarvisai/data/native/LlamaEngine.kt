package com.example.jarvisai.data.native

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, offline JNI bridge for llama.cpp.
 *
 * Handles:
 * - Loading native shared libraries (libllama.so, libggml.so, etc.)
 * - Native pointer lifecycle management (model + context)
 * - Safe memory-mapped file loading (mmap)
 * - Cancellation flag coordination across JNI boundary
 * - Streaming tokens as a reactive Kotlin Coroutine Flow
 */
class LlamaEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "LlamaEngine"
        private const val LIB_NAME = "llama_android"

        @Volatile
        private var isLibraryLoaded = false

        init {
            loadLibrary()
        }

        fun loadLibrary(): Boolean {
            if (isLibraryLoaded) return true
            return try {
                System.loadLibrary(LIB_NAME)
                isLibraryLoaded = true
                Log.i(TAG, "Native library '$LIB_NAME' loaded successfully.")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(
                    TAG,
                    "Native library '$LIB_NAME' not found or failed to load. Simulated/Fallback mode can be used if building without NDK binaries.",
                    e
                )
                isLibraryLoaded = false
                false
            }
        }
    }

    // Native pointer address to the C++ LlamaContextContainer struct
    @Volatile
    private var nativeHandle: Long = 0L

    // Atomic cancellation flag to stop running inference immediately
    private val isInterrupted = AtomicBoolean(false)

    val isModelLoaded: Boolean
        get() = nativeHandle != 0L

    /**
     * Loads a GGUF model from a local file path into memory.
     * Uses mmap for zero-copy memory mapping whenever possible.
     *
     * @param modelPath Absolute file system path to the .gguf model file.
     * @param contextSize Maximum context window in tokens (KV cache size).
     * @param threads Number of CPU threads dedicated to compute.
     * @param useMmap Enable mmap for fast load and lower physical RAM commitment.
     * @return Result.success if loaded, Result.failure with error message otherwise.
     */
    suspend fun loadModel(
        modelPath: String,
        contextSize: Int = 2048,
        threads: Int = 4,
        useMmap: Boolean = true
    ): Result<Unit> = withContext(dispatcher) {
        if (!isLibraryLoaded && !loadLibrary()) {
            return@withContext Result.failure(
                IllegalStateException("Native library '$LIB_NAME' is not loaded.")
            )
        }

        // Release any existing model first
        if (nativeHandle != 0L) {
            freeModel()
        }

        try {
            val handle = loadModelNative(
                modelPath = modelPath,
                contextSize = contextSize,
                threads = threads,
                useMmap = useMmap
            )

            if (handle != 0L) {
                nativeHandle = handle
                Log.i(TAG, "Model loaded successfully at native address: 0x${java.lang.Long.toHexString(handle)}")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to initialize llama.cpp model context from: $modelPath"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during native model load", t)
            Result.failure(t)
        }
    }

    /**
     * Streams generated tokens asynchronously via Kotlin Flow.
     *
     * @param prompt Full prompt with formatted chat template.
     * @param maxTokens Maximum new tokens to sample.
     * @param temperature Sampling temperature.
     * @param topP Top-p nucleus sampling.
     * @param topK Top-k sampling.
     * @return A Flow emitting individual generated token pieces in real time.
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int = 1024,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): Flow<String> = callbackFlow {
        val currentHandle = nativeHandle
        if (currentHandle == 0L) {
            close(IllegalStateException("No GGUF model is loaded in memory."))
            return@callbackFlow
        }

        isInterrupted.set(false)

        val callback = TokenCallback { tokenPiece ->
            if (isInterrupted.get()) {
                false // Tell C++ loop to break immediately
            } else {
                trySend(tokenPiece)
                true
            }
        }

        try {
            generateNative(
                handle = currentHandle,
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                topK = topK,
                callback = callback
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Error in native generation loop", t)
            close(t)
        } finally {
            close()
        }

        awaitClose {
            stopGeneration()
        }
    }.flowOn(dispatcher)

    /**
     * Flags the native loop to stop generating immediately.
     */
    fun stopGeneration() {
        isInterrupted.set(true)
        val currentHandle = nativeHandle
        if (currentHandle != 0L && isLibraryLoaded) {
            try {
                stopNative(currentHandle)
            } catch (e: Exception) {
                Log.w(TAG, "Error requesting native stop", e)
            }
        }
    }

    /**
     * Releases model and context native memory (KV cache, tensors, mmap buffers).
     */
    suspend fun freeModel() = withContext(dispatcher) {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            if (isLibraryLoaded) {
                try {
                    freeModelNative(handle)
                    Log.i(TAG, "Native model resources freed successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error freeing native model", e)
                }
            }
        }
    }

    // =========================================================================
    // JNI Native Declarations
    // These link to functions exported by llama_android.cpp
    // =========================================================================

    private external fun loadModelNative(
        modelPath: String,
        contextSize: Int,
        threads: Int,
        useMmap: Boolean
    ): Long

    private external fun generateNative(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        callback: TokenCallback
    )

    private external fun stopNative(handle: Long)

    private external fun freeModelNative(handle: Long)
}
