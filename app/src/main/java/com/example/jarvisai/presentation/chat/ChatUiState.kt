package com.example.jarvisai.presentation.chat

import com.example.jarvisai.domain.model.Conversation
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.model.Message

/**
 * UI State for ChatScreen.
 */
data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val activeModel: LocalGgufModel? = null,
    val selectedModelId: String = "gemini-2.5-flash",
    val providerApiKeys: Map<String, String> = emptyMap(),
    val isModelLoaded: Boolean = false,
    val inputPrompt: String = "",
    val attachedImageUri: String? = null,
    val attachedImageBase64: String? = null,
    val attachedImageMimeType: String? = null,
    val attachedDocumentTitle: String? = null,
    val attachedDocumentType: String? = null,
    val attachedDocumentContent: String? = null,
    val inferenceStatus: ChatInferenceStatus = ChatInferenceStatus.Idle,
    val isSpeakingTts: Boolean = false,
    val speakingMessageId: String? = null,
    val streamingMessageId: String? = null,
    val tokensPerSecond: Float = 0f,
    val isLiveModeRequested: Boolean = false,
    val errorMessage: String? = null
) {
    fun isProviderReady(provider: com.example.jarvisai.domain.model.ModelProvider): Boolean {
        if (provider == com.example.jarvisai.domain.model.ModelProvider.GEMINI) {
            val provKey = providerApiKeys["gemini"]
            val buildKey = try {
                com.example.BuildConfig.GEMINI_API_KEY
            } catch (_: Throwable) {
                ""
            }
            return !provKey.isNullOrBlank() || (buildKey.isNotBlank() && buildKey != "DEFAULT_API_KEY")
        }
        val key = providerApiKeys[provider.id.lowercase()]
        return !key.isNullOrBlank()
    }
}

sealed interface ChatInferenceStatus {
    object Idle : ChatInferenceStatus
    data class LoadingModel(val modelName: String) : ChatInferenceStatus
    data class Generating(val tokensPerSecond: Float = 0f) : ChatInferenceStatus
    data class Error(val message: String) : ChatInferenceStatus
}
