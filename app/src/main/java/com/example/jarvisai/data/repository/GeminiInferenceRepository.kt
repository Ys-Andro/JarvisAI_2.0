package com.example.jarvisai.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.jarvisai.data.api.gemini.GeminiApiClient
import com.example.jarvisai.data.api.gemini.GeminiGenerationConfig
import com.example.jarvisai.data.api.gemini.GeminiMessage
import com.example.jarvisai.data.api.multi.UniversalAiApiClient
import com.example.jarvisai.domain.model.CloudAiModel
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.InferenceState
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.model.Message
import com.example.jarvisai.domain.model.Role
import com.example.jarvisai.domain.repository.IInferenceRepository
import com.example.jarvisai.domain.repository.ISettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Cloud-based Multi-Model AI Inference Repository.
 * Supports Google Gemini, OpenAI (GPT-4o), DeepSeek (V3/R1), Groq (Llama 3.3/Mixtral), Claude 3.5.
 * Reads API key from BuildConfig (via secrets plugin/.env) or user-configured custom key in Settings.
 */
class GeminiInferenceRepository(
    private val geminiApiClient: GeminiApiClient,
    private val universalApiClient: UniversalAiApiClient,
    private val settingsRepository: ISettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IInferenceRepository {

    companion object {
        private const val TAG = "UniversalInferenceRepo"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }

    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Idle)
    override val inferenceState: Flow<InferenceState> = _inferenceState.asStateFlow()

    private val _activeModel = MutableStateFlow<LocalGgufModel?>(
        LocalGgufModel(
            id = "gemini_cloud",
            name = "Gemini 2.5 Flash",
            fileName = "gemini-2.5-flash",
            filePath = "cloud://ai",
            sizeBytes = 0,
            quantization = "Cloud API",
            contextLength = 1048576,
            isLoaded = true,
            isDefault = true
        )
    )
    override val activeModel: Flow<LocalGgufModel?> = _activeModel.asStateFlow()

    private var currentStreamJob: Job? = null

    /**
     * Resolves the active API key for the given provider:
     * 1. Provider-specific key in App Settings (e.g., openai_api_key, groq_api_key, gemini_api_key)
     * 2. For GEMINI: fallback to general getApiKey() or BuildConfig.GEMINI_API_KEY
     */
    suspend fun resolveApiKeyForProvider(provider: com.example.jarvisai.domain.model.ModelProvider): String {
        // 1. Try provider-specific key
        val providerKey = settingsRepository.getProviderApiKey(provider.id).first()
        if (!providerKey.isNullOrBlank()) {
            return providerKey.trim()
        }

        // 2. If Gemini provider, check general apiKey and BuildConfig
        if (provider == com.example.jarvisai.domain.model.ModelProvider.GEMINI) {
            val generalKey = settingsRepository.getApiKey().first()
            if (!generalKey.isNullOrBlank() && generalKey != "DEFAULT_API_KEY") {
                return generalKey.trim()
            }
            return try {
                val buildConfigKey = BuildConfig.GEMINI_API_KEY
                if (buildConfigKey.isNotBlank() && buildConfigKey != "DEFAULT_API_KEY") {
                    buildConfigKey.trim()
                } else {
                    ""
                }
            } catch (_: Throwable) {
                ""
            }
        }

        return ""
    }

    override fun isModelLoaded(): Boolean {
        return _activeModel.value?.isLoaded == true
    }

    override suspend fun loadModel(
        model: LocalGgufModel,
        contextLength: Int,
        threads: Int
    ): Result<Unit> = withContext(dispatcher) {
        val loaded = model.copy(isLoaded = true)
        _activeModel.value = loaded
        _inferenceState.value = InferenceState.ModelReady(loaded)
        Result.success(Unit)
    }

    override suspend fun unloadModel() {
        withContext(dispatcher) {
            _activeModel.value = null
            _inferenceState.value = InferenceState.Idle
        }
    }

    override fun generateCompletionStream(
        prompt: String,
        conversationHistory: List<Message>,
        settings: GenerationSettings,
        imageBase64: String?,
        imageMimeType: String?
    ): Flow<String> = flow {
        val selectedModelId = settingsRepository.getSelectedGeminiModel().first()
        val modelDef = CloudAiModel.findById(selectedModelId)
        val apiKey = resolveApiKeyForProvider(modelDef.provider)
        if (apiKey.isBlank()) {
            val errorMsg = "Por favor ingresa tu API Key para ${modelDef.provider.displayName} en Ajustes."
            _inferenceState.value = InferenceState.Error(errorMsg)
            emit("⚠️ $errorMsg\n\nPuedes ingresar tu API Key en Ajustes o seleccionar Google Gemini en la barra superior.")
            return@flow
        }

        val customBaseUrl = if (modelDef.provider == com.example.jarvisai.domain.model.ModelProvider.CUSTOM_OPENAI) {
            settingsRepository.getCustomOpenAiEndpoint().first()
        } else null

        // Update active model metadata so UI displays correct model info
        _activeModel.value = LocalGgufModel(
            id = modelDef.id,
            name = modelDef.name,
            fileName = modelDef.id,
            filePath = "cloud://${modelDef.provider.id}",
            sizeBytes = 0,
            quantization = modelDef.provider.displayName,
            contextLength = modelDef.defaultContextLength,
            isLoaded = true,
            isDefault = true
        )

        val startTime = System.currentTimeMillis()
        var generatedTokens = 0
        val accumulatedText = StringBuilder()

        _inferenceState.value = InferenceState.Generating(partialText = "", tokensPerSecond = 0f)

        universalApiClient.streamCompletion(
            apiKey = apiKey,
            model = modelDef,
            prompt = prompt,
            history = conversationHistory,
            settings = settings,
            customBaseUrl = customBaseUrl,
            imageBase64 = imageBase64,
            imageMimeType = imageMimeType
        ).collect { tokenChunk ->
            generatedTokens++
            accumulatedText.append(tokenChunk)

            val elapsedSec = (System.currentTimeMillis() - startTime).coerceAtLeast(1L) / 1000.0f
            val tokPerSec = if (elapsedSec > 0f) generatedTokens / elapsedSec else 0f

            _inferenceState.value = InferenceState.Generating(
                partialText = accumulatedText.toString(),
                tokensPerSecond = tokPerSec
            )

            emit(tokenChunk)
        }
    }
        .onStart {
            Log.i(TAG, "Starting multi-model cloud completion stream.")
        }
        .onCompletion { cause ->
            val current = _activeModel.value
            if (cause != null) {
                Log.w(TAG, "AI stream ended with error: ${cause.message}")
                _inferenceState.value = InferenceState.Error(cause.message ?: "Generación interrumpida")
            } else if (current != null) {
                _inferenceState.value = InferenceState.ModelReady(current)
                Log.i(TAG, "AI stream completed successfully.")
            } else {
                _inferenceState.value = InferenceState.Idle
            }
        }
        .catch { e ->
            Log.w(TAG, "AI stream issue: ${e.message}")
            _inferenceState.value = InferenceState.Error(e.message ?: "Error en la llamada a la API")
            emit("❌ Error en la generación: ${e.message ?: "Error de comunicación con el servicio"}")
        }
        .flowOn(dispatcher)

    override suspend fun stopGeneration() {
        withContext(dispatcher) {
            currentStreamJob?.cancel()
            val current = _activeModel.value
            if (current != null) {
                _inferenceState.value = InferenceState.ModelReady(current)
            } else {
                _inferenceState.value = InferenceState.Idle
            }
        }
    }
}
