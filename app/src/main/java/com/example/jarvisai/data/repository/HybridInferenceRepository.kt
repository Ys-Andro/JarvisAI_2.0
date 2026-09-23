package com.example.jarvisai.data.repository

import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.model.InferenceState
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.model.Message
import com.example.jarvisai.domain.repository.IInferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class HybridInferenceRepository(
    private val localRepo: LlamaInferenceRepository,
    private val cloudRepo: GeminiInferenceRepository
) : IInferenceRepository {

    override val activeModel: Flow<LocalGgufModel?> = localRepo.activeModel.flatMapLatest { localActive ->
        if (localActive != null && localActive.isLoaded) {
            flowOf(localActive)
        } else {
            cloudRepo.activeModel
        }
    }

    override val inferenceState: Flow<InferenceState> = localRepo.activeModel.flatMapLatest { localActive ->
        if (localActive != null && localActive.isLoaded) {
            localRepo.inferenceState
        } else {
            cloudRepo.inferenceState
        }
    }

    override fun isModelLoaded(): Boolean {
        return localRepo.isModelLoaded() || cloudRepo.isModelLoaded()
    }

    override suspend fun loadModel(
        model: LocalGgufModel,
        contextLength: Int,
        threads: Int
    ): Result<Unit> {
        return localRepo.loadModel(model, contextLength, threads)
    }

    override suspend fun unloadModel() {
        localRepo.unloadModel()
    }

    override fun generateCompletionStream(
        prompt: String,
        conversationHistory: List<Message>,
        settings: GenerationSettings,
        imageBase64: String?,
        imageMimeType: String?
    ): Flow<String> {
        return if (localRepo.isModelLoaded()) {
            localRepo.generateCompletionStream(prompt, conversationHistory, settings, imageBase64, imageMimeType)
        } else {
            cloudRepo.generateCompletionStream(prompt, conversationHistory, settings, imageBase64, imageMimeType)
        }
    }

    override suspend fun stopGeneration() {
        if (localRepo.isModelLoaded()) {
            localRepo.stopGeneration()
        } else {
            cloudRepo.stopGeneration()
        }
    }
}
