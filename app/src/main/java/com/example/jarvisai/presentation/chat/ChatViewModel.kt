package com.example.jarvisai.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.InferenceState
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.model.Message
import com.example.jarvisai.domain.model.Role
import com.example.jarvisai.domain.repository.IConversationRepository
import com.example.jarvisai.domain.repository.IInferenceRepository
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val conversationRepository: IConversationRepository,
    private val inferenceRepository: IInferenceRepository,
    private val settingsRepository: ISettingsRepository,
    private val ttsRepository: ITtsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentConversationId: String? = null
    private var generationJob: Job? = null
    private var messagesObservationJob: Job? = null

    init {
        observeInferenceState()
        observeActiveModel()
        observeTtsState()
        initDefaultConversation()
    }

    private fun observeInferenceState() {
        viewModelScope.launch {
            inferenceRepository.inferenceState.collect { state ->
                when (state) {
                    is InferenceState.Idle -> {
                        _uiState.update {
                            it.copy(
                                inferenceStatus = ChatInferenceStatus.Idle,
                                errorMessage = null
                            )
                        }
                    }
                    is InferenceState.LoadingModel -> {
                        _uiState.update {
                            it.copy(
                                inferenceStatus = ChatInferenceStatus.LoadingModel(state.modelName),
                                errorMessage = null
                            )
                        }
                    }
                    is InferenceState.ModelReady -> {
                        _uiState.update {
                            it.copy(
                                activeModel = state.model,
                                isModelLoaded = true,
                                inferenceStatus = ChatInferenceStatus.Idle,
                                errorMessage = null
                            )
                        }
                    }
                    is InferenceState.Generating -> {
                        _uiState.update {
                            it.copy(
                                inferenceStatus = ChatInferenceStatus.Generating(state.tokensPerSecond),
                                tokensPerSecond = state.tokensPerSecond
                            )
                        }
                    }
                    is InferenceState.Error -> {
                        _uiState.update {
                            it.copy(
                                inferenceStatus = ChatInferenceStatus.Error(state.message),
                                errorMessage = state.message
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeActiveModel() {
        viewModelScope.launch {
            inferenceRepository.activeModel.collect { model ->
                _uiState.update {
                    it.copy(
                        activeModel = model,
                        isModelLoaded = model != null
                    )
                }
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsRepository.isSpeaking.collect { isSpeaking ->
                _uiState.update { it.copy(isSpeakingTts = isSpeaking) }
            }
        }
    }

    private fun initDefaultConversation() {
        viewModelScope.launch {
            val conversations = conversationRepository.getAllConversations().first()
            val targetId = if (conversations.isNotEmpty()) {
                conversations.first().id
            } else {
                conversationRepository.createConversation("Jarvis Session", null)
            }
            selectConversation(targetId)
        }
    }

    fun selectConversation(conversationId: String) {
        currentConversationId = conversationId
        messagesObservationJob?.cancel()

        viewModelScope.launch {
            conversationRepository.getConversationById(conversationId).collect { conversation ->
                _uiState.update { it.copy(conversation = conversation) }
            }
        }

        messagesObservationJob = viewModelScope.launch {
            conversationRepository.getMessagesForConversation(conversationId).collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputPrompt = text) }
    }

    fun sendMessage() {
        val prompt = _uiState.value.inputPrompt.trim()
        if (prompt.isEmpty()) return

        val conversationId = currentConversationId ?: return

        // 1. Clear input field immediately
        _uiState.update { it.copy(inputPrompt = "") }

        viewModelScope.launch {
            // Check if model is loaded
            if (!inferenceRepository.isModelLoaded()) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Por favor carga un modelo GGUF antes de enviar mensajes."
                    )
                }
                return@launch
            }

            // 2. Insert User message into Room
            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = Role.USER,
                content = prompt,
                timestamp = System.currentTimeMillis()
            )
            conversationRepository.insertMessage(userMsg)

            // Update conversation title if this is the first user prompt
            val currentConv = _uiState.value.conversation
            if (currentConv != null && currentConv.messageCount == 0) {
                val autoTitle = if (prompt.length > 28) prompt.take(28) + "..." else prompt
                conversationRepository.updateConversationTitle(conversationId, autoTitle)
            }

            // 3. Prepare Assistant placeholder message
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = Message(
                id = assistantMsgId,
                conversationId = conversationId,
                role = Role.ASSISTANT,
                content = "",
                timestamp = System.currentTimeMillis() + 1,
                isStreaming = true
            )
            conversationRepository.insertMessage(initialAssistantMsg)
            _uiState.update { it.copy(streamingMessageId = assistantMsgId) }

            // 4. Retrieve settings & history
            val settings = settingsRepository.getSettings().first()
            val history = _uiState.value.messages

            // 5. Launch native token generation streaming
            executeInferenceStream(
                assistantMsgId = assistantMsgId,
                prompt = prompt,
                history = history,
                settings = settings
            )
        }
    }

    private fun executeInferenceStream(
        assistantMsgId: String,
        prompt: String,
        history: List<Message>,
        settings: GenerationSettings
    ) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val responseBuilder = StringBuilder()
            val startTime = System.currentTimeMillis()
            var tokenCount = 0

            inferenceRepository.generateCompletionStream(prompt, history, settings)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.message,
                            streamingMessageId = null
                        )
                    }
                }
                .collect { tokenPiece ->
                    tokenCount++
                    responseBuilder.append(tokenPiece)
                    val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                    val tokPerSec = (tokenCount.toFloat() / (elapsedMs.toFloat() / 1000f))

                    // Update streaming message in Room database
                    conversationRepository.updateMessageContent(
                        messageId = assistantMsgId,
                        content = responseBuilder.toString(),
                        tokensPerSec = tokPerSec,
                        durationMs = elapsedMs
                    )
                }

            val finalElapsedMs = System.currentTimeMillis() - startTime
            val finalTokPerSec = if (finalElapsedMs > 0) tokenCount.toFloat() / (finalElapsedMs.toFloat() / 1000f) else 0f

            _uiState.update {
                it.copy(
                    streamingMessageId = null,
                    tokensPerSecond = finalTokPerSec
                )
            }

            // Auto-speak response if configured
            val finalResponse = responseBuilder.toString()
            if (settings.autoTts && finalResponse.isNotBlank()) {
                speakText(finalResponse)
            }
        }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            generationJob?.cancel()
            inferenceRepository.stopGeneration()
            _uiState.update { it.copy(streamingMessageId = null) }
        }
    }

    fun speakText(text: String) {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings().first()
            ttsRepository.speak(
                text = text,
                pitch = settings.ttsPitch,
                speed = settings.ttsSpeed
            )
        }
    }

    fun stopTts() {
        viewModelScope.launch {
            ttsRepository.stop()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        viewModelScope.launch {
            ttsRepository.stop()
        }
    }
}
