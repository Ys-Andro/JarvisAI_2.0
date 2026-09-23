package com.example.jarvisai.presentation.models

import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.LocalGgufModel

data class ModelsUiState(
    val models: List<LocalGgufModel> = emptyList(),
    val activeModel: LocalGgufModel? = null,
    val isLoadingModel: Boolean = false,
    val loadingModelName: String? = null,
    val apiKey: String? = null,
    val providerApiKeys: Map<String, String> = emptyMap(),
    val customOpenAiEndpoint: String? = null,
    val selectedGeminiModel: String = "gemini-2.5-flash",
    val selectedAgentId: String = "jarvis_prime",
    val isValidatingApiKey: Boolean = false,
    val settings: GenerationSettings = GenerationSettings(),
    val appTheme: AppThemeMode = AppThemeMode.DARK_JARVIS,
    val availableCpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val isFloatingBubbleEnabled: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
