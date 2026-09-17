package com.example.jarvisai.data.repository

import android.util.Log
import com.example.jarvisai.data.native.LlamaEngine
import com.example.jarvisai.data.util.ChatTemplateHelper
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.InferenceState
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.model.Message
import com.example.jarvisai.domain.repository.IInferenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Production implementation of IInferenceRepository wrapping LlamaEngine.
 * Handles model loading, automatic prompt templating (Llama3, ChatML, Gemma),
 * token generation streaming, metrics (tok/s), cancellation and clean error handling.
 */
class LlamaInferenceRepository(
    private val llamaEngine: LlamaEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IInferenceRepository {

    companion object {
        private const val TAG = "LlamaInferenceRepo"
    }

    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    override val inferenceState: Flow<InferenceState> = _inferenceState.asStateFlow()

    private val _activeModel = MutableStateFlow<LocalGgufModel?>(null)
    override val activeModel: Flow<LocalGgufModel?> = _activeModel.asStateFlow()

    override fun isModelLoaded(): Boolean {
        return llamaEngine.isModelLoaded && _activeModel.value != null
    }

    override suspend fun loadModel(
        model: LocalGgufModel,
        contextLength: Int,
        threads: Int
    ): Result<Unit> = withContext(dispatcher) {
        _inferenceState.value = InferenceState.LoadingModel(model.name, 0.1f)

        try {
            val loadResult = llamaEngine.loadModel(
                modelPath = model.filePath,
                contextSize = contextLength,
                threads = threads,
                useMmap = true
            )

            if (loadResult.isSuccess) {
                val loadedModel = model.copy(
                    isLoaded = true,
                    contextLength = contextLength
                )
                _activeModel.value = loadedModel
                _inferenceState.value = InferenceState.ModelReady(loadedModel)
                Log.i(TAG, "Model '${model.name}' loaded successfully into memory.")
                Result.success(Unit)
            } else {
                val error = loadResult.exceptionOrNull()
                    ?: IllegalStateException("Unknown error loading native model")
                _activeModel.value = null
                _inferenceState.value = InferenceState.Error(error.message ?: "Failed to load model")
                Log.e(TAG, "Failed to load model '${model.name}'", error)
                Result.failure(error)
            }
        } catch (t: Throwable) {
            _activeModel.value = null
            _inferenceState.value = InferenceState.Error(t.message ?: "Unexpected error")
            Log.e(TAG, "Error in loadModel", t)
            Result.failure(t)
        }
    }

    override suspend fun unloadModel(): Unit = withContext(dispatcher) {
        try {
            llamaEngine.freeModel()
            _activeModel.value = null
            _inferenceState.value = InferenceState.Idle
            Log.i(TAG, "Model unloaded and RAM released.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error while unloading model", t)
            _inferenceState.value = InferenceState.Error(t.message ?: "Failed to unload model")
        }
    }

    override fun generateCompletionStream(
        prompt: String,
        conversationHistory: List<Message>,
        settings: GenerationSettings
    ): Flow<String> = flow {
        val currentModel = _activeModel.value
        if (currentModel == null || !llamaEngine.isModelLoaded) {
            val errorMsg = "No local model is currently loaded in RAM. Please select or load a model first."
            _inferenceState.value = InferenceState.Error(errorMsg)
            throw IllegalStateException(errorMsg)
        }

        // 1. Detect template format based on model name & file name
        val templateFormat = ChatTemplateHelper.detectFormat(currentModel.name, currentModel.fileName)

        // 2. Format complete prompt with template delimiters
        val formattedPrompt = ChatTemplateHelper.formatPrompt(
            systemPrompt = settings.systemPrompt,
            history = conversationHistory,
            newPrompt = prompt,
            format = templateFormat
        )

        Log.d(TAG, "Using template: $templateFormat for generation.")

        val startTime = System.currentTimeMillis()
        var generatedTokens = 0
        val accumulatedText = StringBuilder()

        _inferenceState.value = InferenceState.Generating(partialText = "", tokensPerSecond = 0f)

        // 3. Collect from the engine's token stream
        llamaEngine.generateStream(
            prompt = formattedPrompt,
            maxTokens = settings.maxTokens,
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK
        ).collect { tokenPiece ->
            generatedTokens++
            accumulatedText.append(tokenPiece)

            val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1L) / 1000.0f
            val tokPerSec = if (elapsedSec > 0f) generatedTokens / elapsedSec else 0f

            _inferenceState.value = InferenceState.Generating(
                partialText = accumulatedText.toString(),
                tokensPerSecond = tokPerSec
            )

            emit(tokenPiece)
        }
    }
        .onStart {
            Log.i(TAG, "Starting completion stream.")
        }
        .onCompletion { cause ->
            val currentModel = _activeModel.value
            if (cause != null) {
                Log.w(TAG, "Stream ended with error: ${cause.message}")
                _inferenceState.value = InferenceState.Error(cause.message ?: "Generation interrupted")
            } else if (currentModel != null) {
                _inferenceState.value = InferenceState.ModelReady(currentModel)
                Log.i(TAG, "Stream completed normally.")
            } else {
                _inferenceState.value = InferenceState.Idle
            }
        }
        .catch { e ->
            Log.e(TAG, "Exception caught during completion flow", e)
            _inferenceState.value = InferenceState.Error(e.message ?: "Generation error")
            throw e
        }
        .flowOn(dispatcher)

    override suspend fun stopGeneration() {
        withContext(dispatcher) {
            Log.i(TAG, "User requested stopping inference generation.")
            llamaEngine.stopGeneration()
            val currentModel = _activeModel.value
            if (currentModel != null) {
                _inferenceState.value = InferenceState.ModelReady(currentModel)
            } else {
                _inferenceState.value = InferenceState.Idle
            }
        }
    }
}
