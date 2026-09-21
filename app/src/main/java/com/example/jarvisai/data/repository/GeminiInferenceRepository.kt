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
    private val context: android.content.Context,
    private val geminiApiClient: GeminiApiClient,
    private val universalApiClient: UniversalAiApiClient,
    private val settingsRepository: ISettingsRepository,
    private val memoryRepository: com.example.jarvisai.domain.repository.IMemoryRepository,
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

        // Fetch active agent personality and long-term memories
        val agentId = settingsRepository.getSelectedAgentId().first()
        val agent = com.example.jarvisai.domain.model.Agent.findById(agentId)
        val memories = memoryRepository.getAllMemories().first()

        val memoryContext = if (memories.isNotEmpty()) {
            buildString {
                append("\n\n[MEMORIA A LARGO PLAZO Y DATOS RELEVANTES DEL USUARIO]:\n")
                for (m in memories) {
                    append("- ${m.key}: ${m.value} (${m.category})\n")
                }
            }
        } else ""

        val combinedSystemPrompt = buildString {
            append(agent.systemPrompt)
            val userPrompt = settings.systemPrompt
            if (userPrompt.isNotBlank() && !userPrompt.contains("You are Jarvis") && userPrompt != "Eres Jarvis, un asistente de IA avanzado, eficiente, sofisticado y servicial inspirado en el asistente de Iron Man.") {
                append("\n\n$userPrompt")
            }
            append(memoryContext)
            append("""
                
                [CAPACIDAD DE CONTROL TOTAL DEL DISPOSITIVO - JARVIS DEVICE AGENT]:
                Eres Jarvis, un asistente de IA con capacidad avanzada para controlar el teléfono del usuario en tiempo real en respuesta a comandos de voz o texto en lenguaje natural (similar a Gemini Live). Cuando el usuario pida realizar una acción física o del sistema en el dispositivo (ej. encender linterna, poner alarma, abrir una aplicación, ajustar volumen, hacer llamada, buscar en la web), debes interpretar la orden de forma flexible y emitir al final de tu respuesta un bloque JSON estructurado con este formato exacto:
                [JARVIS_ACTION: {"action":"FLASHLIGHT","enable":true}]
                
                Acciones soportadas:
                1. {"action":"FLASHLIGHT", "enable": true/false} (Encender o apagar la linterna)
                2. {"action":"SET_ALARM", "hour": int, "minute": int, "message": "..."} (Programar alarma)
                3. {"action":"OPEN_APP", "appName": "..."} (Abrir una aplicación instalada como WhatsApp, YouTube, Spotify, Ajustes, etc.)
                4. {"action":"VOLUME", "level": int 0-100} (Ajustar volumen multimedia)
                5. {"action":"CALL", "number": "..."} (Abrir marcador telefónico)
                6. {"action":"WEB_SEARCH", "query": "..."} (Buscar en la web)
                7. {"action":"HOME"} (Ir a la pantalla de inicio)
                8. {"action":"BACK"} (Regresar / atrás)
                9. {"action":"NOTIFICATIONS"} (Abrir barra de notificaciones)
                
                Responde con sofisticación, naturalidad y ejecuta la acción emitiendo el bloque JSON correspondiente.
            """.trimIndent())
        }

        val effectiveSettings = settings.copy(systemPrompt = combinedSystemPrompt)

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
            settings = effectiveSettings,
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

        val finalResponse = accumulatedText.toString()
        val actionRegex = "\\[JARVIS_ACTION:\\s*(\\{[^}]+\\})\\]".toRegex()
        val matchResult = actionRegex.find(finalResponse)
        if (matchResult != null) {
            val jsonPayload = matchResult.groupValues[1]
            val actionResultMsg = com.example.jarvisai.data.util.DeviceController.executeActionCommand(context, jsonPayload)
            val confirmation = "\n\n✓ $actionResultMsg"
            emit(confirmation)
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
            val errorMsg = e.message ?: ""
            val friendlyMsg = if (errorMsg.contains("429") || errorMsg.contains("503") || errorMsg.contains("overloaded") || errorMsg.contains("quota") || errorMsg.contains("resource_exhausted")) {
                "⚠️ La API del modelo está temporalmente sobrecargada o sin cuota disponible (Límite de peticiones excedido). Puedes consultar tu historial de conversaciones, notas de memoria y documentos analizados sin conexión (Modo Offline) mientras se restablece el servicio."
            } else {
                "❌ Error en la generación: ${e.message ?: "Error de comunicación con el servicio"}"
            }
            _inferenceState.value = InferenceState.Error(friendlyMsg)
            emit(friendlyMsg)
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
