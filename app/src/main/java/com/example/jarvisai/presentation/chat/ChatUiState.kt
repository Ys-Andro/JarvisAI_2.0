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
    val isModelLoaded: Boolean = false,
    val inputPrompt: String = "",
    val inferenceStatus: ChatInferenceStatus = ChatInferenceStatus.Idle,
    val isSpeakingTts: Boolean = false,
    val streamingMessageId: String? = null,
    val tokensPerSecond: Float = 0f,
    val errorMessage: String? = null
)

sealed interface ChatInferenceStatus {
    object Idle : ChatInferenceStatus
    data class LoadingModel(val modelName: String) : ChatInferenceStatus
    data class Generating(val tokensPerSecond: Float = 0f) : ChatInferenceStatus
    data class Error(val message: String) : ChatInferenceStatus
}
