package com.example.jarvisai.presentation.models

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.repository.IInferenceRepository
import com.example.jarvisai.domain.repository.IModelRepository
import com.example.jarvisai.domain.repository.ISettingsRepository
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

            val settings = _uiState.value.settings
            val result = inferenceRepository.loadModel(
                model = model,
                contextLength = settings.contextWindow,
                threads = settings.cpuThreads
            )

            if (result.isSuccess) {
                modelRepository.setDefaultModel(model.id)
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        loadingModelName = null,
                        statusMessage = "Modelo ${model.name} listo para chatear.",
                        errorMessage = null
                    )
                }
            } else {
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
            inferenceRepository.unloadModel()
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
                    isLoadingModel = true,
                    statusMessage = "Importando archivo GGUF..."
                )
            }

            try {
                val (fileName, sizeBytes) = getUriDetails(uri)
                val cleanModelName = fileName.removeSuffix(".gguf").replace("_", " ").replace("-", " ")

                val destinationFile = File(context.filesDir, "models/$fileName")
                destinationFile.parentFile?.mkdirs()

                // Copy stream to internal app storage for reliable mmap
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Detect quantization from filename (e.g. Q4_K_M, Q8_0, etc.)
                val quant = detectQuantization(fileName)

                val registeredModel = modelRepository.registerImportedModel(
                    name = cleanModelName,
                    fileName = fileName,
                    filePath = destinationFile.absolutePath,
                    sizeBytes = destinationFile.length(),
                    quantization = quant
                )

                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        statusMessage = "Modelo '$cleanModelName' importado con éxito."
                    )
                }

                // Auto-load if no model is active
                if (_uiState.value.activeModel == null) {
                    loadModel(registeredModel)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error importing GGUF file", e)
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        errorMessage = "Error importando GGUF: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun getUriDetails(uri: Uri): Pair<String, Long> {
        var name = "model_${System.currentTimeMillis()}.gguf"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }

    private fun detectQuantization(fileName: String): String {
        val uppercase = fileName.uppercase()
        val quants = listOf(
            "Q4_K_M", "Q4_K_S", "Q4_0", "Q4_1",
            "Q5_K_M", "Q5_K_S", "Q5_0", "Q5_1",
            "Q8_0", "Q6_K", "Q2_K", "Q3_K_M", "F16", "BF16"
        )
        return quants.firstOrNull { uppercase.contains(it) } ?: "GGUF"
    }

    fun deleteModel(model: LocalGgufModel) {
        viewModelScope.launch {
            if (_uiState.value.activeModel?.id == model.id) {
                inferenceRepository.unloadModel()
            }
            modelRepository.deleteModel(model.id)
            withContext(Dispatchers.IO) {
                val file = File(model.filePath)
                if (file.exists()) file.delete()
            }
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

    fun setAppTheme(theme: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
            _uiState.update { it.copy(appTheme = theme) }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
