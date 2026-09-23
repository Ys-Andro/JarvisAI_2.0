package com.example.jarvisai.presentation.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvisai.data.util.DeviceMemoryManager
import com.example.jarvisai.data.util.DriveAndStorageManager
import com.example.jarvisai.data.util.GgufMetadataParser
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.GgufModelState
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.repository.IInferenceRepository
import com.example.jarvisai.domain.repository.IModelRepository
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelsViewModel(
    private val modelRepository: IModelRepository,
    private val inferenceRepository: IInferenceRepository,
    private val settingsRepository: ISettingsRepository,
    private val ttsRepository: ITtsRepository,
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ModelsViewModel"
    }

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        observeModels()
        observeActiveModel()
        observeSettings()
        observeTheme()
        observeApiKey()
        observeGeminiModel()
        observeProviderApiKeys()
        observeSelectedAgent()
        observeFloatingBubble()
        refreshRamInfo()
        refreshCacheSize()
    }

    fun refreshRamInfo() {
        try {
            val memInfo = DeviceMemoryManager.getMemoryInfo(context)
            val availStr = DeviceMemoryManager.formatBytes(memInfo.availMem)
            val totalStr = DeviceMemoryManager.formatBytes(memInfo.totalMem)
            _uiState.update {
                it.copy(
                    availableRamBytes = memInfo.availMem,
                    totalRamBytes = memInfo.totalMem,
                    formattedRamStatus = "RAM disponible: $availStr / $totalStr"
                )
            }
        } catch (_: Exception) {}
    }

    fun refreshCacheSize() {
        try {
            val bytes = DriveAndStorageManager.getCacheSizeBytes(context)
            _uiState.update {
                it.copy(
                    cacheSizeBytes = bytes,
                    formattedCacheSize = DeviceMemoryManager.formatBytes(bytes)
                )
            }
        } catch (_: Exception) {}
    }

    fun clearModelCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DriveAndStorageManager.clearCache(context)
            }
            refreshCacheSize()
            _uiState.update { it.copy(statusMessage = "Caché de modelos temporales liberada.") }
        }
    }

    private fun observeFloatingBubble() {
        viewModelScope.launch {
            settingsRepository.isFloatingBubbleEnabled().collect { enabled ->
                _uiState.update { it.copy(isFloatingBubbleEnabled = enabled) }
            }
        }
    }

    private fun observeSelectedAgent() {
        viewModelScope.launch {
            settingsRepository.getSelectedAgentId().collect { agentId ->
                _uiState.update { it.copy(selectedAgentId = agentId) }
            }
        }
    }

    private fun observeProviderApiKeys() {
        viewModelScope.launch {
            settingsRepository.getAllProviderApiKeys().collect { keysMap ->
                _uiState.update { it.copy(providerApiKeys = keysMap) }
            }
        }
        viewModelScope.launch {
            settingsRepository.getCustomOpenAiEndpoint().collect { endpoint ->
                _uiState.update { it.copy(customOpenAiEndpoint = endpoint) }
            }
        }
    }

    private fun observeApiKey() {
        viewModelScope.launch {
            settingsRepository.getApiKey().collect { key ->
                _uiState.update { it.copy(apiKey = key) }
            }
        }
    }

    private fun observeGeminiModel() {
        viewModelScope.launch {
            settingsRepository.getSelectedGeminiModel().collect { model ->
                _uiState.update { it.copy(selectedGeminiModel = model) }
            }
        }
    }

    private fun observeModels() {
        viewModelScope.launch {
            modelRepository.getAllModels().collect { modelsList ->
                _uiState.update { it.copy(models = modelsList) }
            }
        }
    }

    private fun observeActiveModel() {
        viewModelScope.launch {
            inferenceRepository.activeModel.collect { active ->
                _uiState.update { it.copy(activeModel = active) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun observeTheme() {
        viewModelScope.launch {
            settingsRepository.getAppTheme().collect { theme ->
                _uiState.update { it.copy(appTheme = theme) }
            }
        }
    }

    fun loadModel(model: LocalGgufModel) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingModel = true,
                    loadingModelName = model.name,
                    statusMessage = "Cargando ${model.name} en memoria RAM..."
                )
            }
            modelRepository.updateModelState(model.id, GgufModelState.LOADING)

            val settings = _uiState.value.settings
            val result = inferenceRepository.loadModel(
                model = model,
                contextLength = settings.contextWindow,
                threads = settings.cpuThreads
            )

            if (result.isSuccess) {
                modelRepository.updateModelState(model.id, GgufModelState.LOADED)
                modelRepository.setDefaultModel(model.id)
                refreshRamInfo()
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        loadingModelName = null,
                        statusMessage = "Modelo ${model.name} listo para chatear.",
                        errorMessage = null
                    )
                }
            } else {
                modelRepository.updateModelState(model.id, GgufModelState.ERROR)
                refreshRamInfo()
                val err = result.exceptionOrNull()?.message ?: "Error al cargar el modelo"
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        loadingModelName = null,
                        errorMessage = err
                    )
                }
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            val currentActive = _uiState.value.activeModel
            if (currentActive != null) {
                modelRepository.updateModelState(currentActive.id, GgufModelState.UNLOADED)
            }
            inferenceRepository.unloadModel()
            refreshRamInfo()
            _uiState.update {
                it.copy(
                    statusMessage = "Modelo descargado. Memoria RAM liberada.",
                    activeModel = null
                )
            }
        }
    }

    fun importGgufFromUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importProgressPercent = 0,
                    importStatusText = "Inspeccionando encabezado del archivo...",
                    statusMessage = "Preparando importación..."
                )
            }

            try {
                val details = DriveAndStorageManager.getDocumentDetails(context, uri)

                // 1. Verify GGUF header from stream prior to copying multi-GB file
                val isValidGguf = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val meta = GgufMetadataParser.parseFromStream(stream, details.displayName)
                        meta.isValidGguf
                    } ?: false
                }

                if (!isValidGguf && !details.displayName.endsWith(".gguf", ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importProgressPercent = null,
                            importStatusText = null,
                            errorMessage = "El archivo seleccionado no tiene la firma mágica GGUF válida."
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        importStatusText = "Copiando a almacenamiento interno..."
                    )
                }

                // 2. Cache with real-time percentage progress callback
                val copyResult = DriveAndStorageManager.cacheModelFromUri(
                    context = context,
                    uri = uri,
                    details = details
                ) { progress ->
                    val copiedStr = DeviceMemoryManager.formatBytes(progress.bytesCopied)
                    val totalStr = DeviceMemoryManager.formatBytes(progress.totalBytes)
                    _uiState.update {
                        it.copy(
                            importProgressPercent = progress.percentage,
                            importStatusText = "Copiando: ${progress.percentage}% ($copiedStr / $totalStr)"
                        )
                    }
                }

                if (copyResult.isFailure) {
                    val err = copyResult.exceptionOrNull()?.message ?: "Error al copiar el archivo"
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            importProgressPercent = null,
                            importStatusText = null,
                            errorMessage = err
                        )
                    }
                    return@launch
                }

                val cachedFile = copyResult.getOrThrow()

                // 3. Parse complete GGUF metadata from the cached file
                val metadata = withContext(Dispatchers.IO) {
                    GgufMetadataParser.parseFromFile(cachedFile)
                }

                val modelName = if (metadata.modelName.isNotBlank()) metadata.modelName else GgufMetadataParser.cleanModelName(cachedFile.name)

                // 4. Register in database with metadata
                modelRepository.registerImportedModel(
                    name = modelName,
                    fileName = cachedFile.name,
                    filePath = cachedFile.absolutePath,
                    sizeBytes = cachedFile.length(),
                    quantization = metadata.quantization,
                    architecture = metadata.architecture,
                    contextLength = metadata.contextLength,
                    sourceUri = uri.toString(),
                    isCachedFromDrive = details.isFromCloud
                )

                refreshCacheSize()
                refreshRamInfo()

                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgressPercent = null,
                        importStatusText = null,
                        statusMessage = "Modelo '$modelName' importado y registrado con éxito. Toca 'Cargar en RAM' cuando desees activarlo."
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error importing GGUF file", e)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importProgressPercent = null,
                        importStatusText = null,
                        errorMessage = "Error importando GGUF: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun deleteModel(model: LocalGgufModel) {
        viewModelScope.launch {
            if (_uiState.value.activeModel?.id == model.id) {
                unloadModel()
            }
            modelRepository.deleteModel(model.id)
            withContext(Dispatchers.IO) {
                DriveAndStorageManager.deleteCachedFile(model.filePath)
            }
            refreshCacheSize()
            refreshRamInfo()
            _uiState.update { it.copy(statusMessage = "Modelo eliminado.") }
        }
    }

    fun updateSettings(newSettings: GenerationSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
            _uiState.update { it.copy(settings = newSettings) }
        }
    }

    fun updateTemperature(temp: Float) {
        val current = _uiState.value.settings
        updateSettings(current.copy(temperature = temp))
    }

    fun updateTopP(topP: Float) {
        val current = _uiState.value.settings
        updateSettings(current.copy(topP = topP))
    }

    fun updateTopK(topK: Int) {
        val current = _uiState.value.settings
        updateSettings(current.copy(topK = topK))
    }

    fun updateContextWindow(contextWindow: Int) {
        val current = _uiState.value.settings
        updateSettings(current.copy(contextWindow = contextWindow))
    }

    fun updateCpuThreads(threads: Int) {
        val current = _uiState.value.settings
        updateSettings(current.copy(cpuThreads = threads))
    }

    fun updateSystemPrompt(prompt: String) {
        val current = _uiState.value.settings
        updateSettings(current.copy(systemPrompt = prompt))
    }

    fun updateAutoTts(enabled: Boolean) {
        val current = _uiState.value.settings
        updateSettings(current.copy(autoTts = enabled))
    }

    fun updateTtsSpeed(speed: Float) {
        val current = _uiState.value.settings
        updateSettings(current.copy(ttsSpeed = speed))
    }

    fun updateTtsPitch(pitch: Float) {
        val current = _uiState.value.settings
        updateSettings(current.copy(ttsPitch = pitch))
    }

    fun updateAndroidVoiceName(voiceName: String) {
        val current = _uiState.value.settings
        updateSettings(current.copy(androidVoiceName = voiceName))
    }

    fun setAppTheme(theme: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
            _uiState.update { it.copy(appTheme = theme) }
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFloatingBubbleEnabled(enabled)
            _uiState.update { it.copy(isFloatingBubbleEnabled = enabled) }
        }
    }

    fun verifyApiKey(providerId: String, apiKey: String): Pair<Boolean, String> {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            val msg = "La clave API está vacía"
            _uiState.update { it.copy(statusMessage = msg) }
            return Pair(false, msg)
        }
        val isValid = when (providerId.lowercase()) {
            "gemini" -> trimmed.startsWith("AIza") && trimmed.length >= 20
            "openai" -> trimmed.startsWith("sk-") && trimmed.length >= 20
            "openrouter" -> trimmed.startsWith("sk-or-v1-") && trimmed.length >= 20
            "deepseek" -> trimmed.startsWith("sk-") && trimmed.length >= 20
            "groq" -> trimmed.startsWith("gsk_") && trimmed.length >= 20
            "anthropic" -> trimmed.startsWith("sk-ant-") && trimmed.length >= 20
            else -> trimmed.length >= 10
        }
        val message = if (isValid) "¡Clave API de $providerId válida y verificada! ✓" else "Formato de clave inválido para $providerId ❌"
        _uiState.update { it.copy(statusMessage = message) }
        return Pair(isValid, message)
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateApiKey(apiKey)
            _uiState.update {
                it.copy(
                    apiKey = apiKey.ifBlank { null },
                    statusMessage = if (apiKey.isNotBlank()) "Clave API de Gemini guardada." else "Clave API eliminada."
                )
            }
        }
    }

    fun updateProviderApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateProviderApiKey(providerId, apiKey)
            _uiState.update {
                val updatedKeys = it.providerApiKeys.toMutableMap()
                if (apiKey.isBlank()) {
                    updatedKeys.remove(providerId.lowercase())
                } else {
                    updatedKeys[providerId.lowercase()] = apiKey.trim()
                }
                it.copy(
                    providerApiKeys = updatedKeys,
                    statusMessage = if (apiKey.isNotBlank()) "Clave API de $providerId guardada." else "Clave API de $providerId eliminada."
                )
            }
        }
    }

    fun updateCustomOpenAiEndpoint(endpoint: String) {
        viewModelScope.launch {
            settingsRepository.updateCustomOpenAiEndpoint(endpoint)
            _uiState.update {
                it.copy(
                    customOpenAiEndpoint = endpoint.ifBlank { null },
                    statusMessage = "Endpoint personalizado actualizado."
                )
            }
        }
    }

    fun updateSelectedGeminiModel(model: String) {
        viewModelScope.launch {
            settingsRepository.updateSelectedGeminiModel(model)
            _uiState.update {
                it.copy(
                    selectedGeminiModel = model,
                    statusMessage = "Modelo activo: $model"
                )
            }
        }
    }

    fun setSelectedAgent(agentId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedAgentId(agentId)
            val agent = com.example.jarvisai.domain.model.Agent.findById(agentId)
            _uiState.update {
                it.copy(
                    selectedAgentId = agentId,
                    statusMessage = "Agente activo: ${agent.name}"
                )
            }
        }
    }

    fun testVoice(text: String = "Buenas noches, señor. Todos los sistemas están en línea.") {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                ttsRepository.speak(text, settings.ttsPitch, settings.ttsSpeed)
            } catch (e: Exception) {
                Log.e(TAG, "Error testing voice", e)
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
