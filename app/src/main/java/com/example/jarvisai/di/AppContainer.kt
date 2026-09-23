package com.example.jarvisai.di

import android.content.Context
import com.example.jarvisai.data.local.database.JarvisDatabase
import com.example.jarvisai.data.local.database.dao.ConversationDao
import com.example.jarvisai.data.local.database.dao.DocumentDao
import com.example.jarvisai.data.local.database.dao.MemoryDao
import com.example.jarvisai.data.local.database.dao.MessageDao
import com.example.jarvisai.data.local.database.dao.ModelDao
import com.example.jarvisai.data.local.datastore.AppPreferences
import com.example.jarvisai.data.api.gemini.GeminiApiClient
import com.example.jarvisai.data.repository.AndroidTtsRepository
import com.example.jarvisai.data.repository.ConversationRepositoryImpl
import com.example.jarvisai.data.repository.DocumentRepositoryImpl
import com.example.jarvisai.data.repository.GeminiInferenceRepository
import com.example.jarvisai.data.repository.MemoryRepositoryImpl
import com.example.jarvisai.data.repository.ModelRepositoryImpl
import com.example.jarvisai.data.repository.SettingsRepositoryImpl
import com.example.jarvisai.data.util.NetworkMonitor
import com.example.jarvisai.domain.repository.IConversationRepository
import com.example.jarvisai.domain.repository.IDocumentRepository
import com.example.jarvisai.domain.repository.IInferenceRepository
import com.example.jarvisai.domain.repository.IMemoryRepository
import com.example.jarvisai.domain.repository.IModelRepository
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository

/**
 * Service locator providing singleton dependencies for JarvisAi.
 */
class AppContainer(private val context: Context) {

    val database: JarvisDatabase by lazy {
        JarvisDatabase.getInstance(context)
    }

    val conversationDao: ConversationDao by lazy {
        database.conversationDao()
    }

    val messageDao: MessageDao by lazy {
        database.messageDao()
    }

    val modelDao: ModelDao by lazy {
        database.modelDao()
    }

    val memoryDao: MemoryDao by lazy {
        database.memoryDao()
    }

    val documentDao: DocumentDao by lazy {
        database.documentDao()
    }

    val appPreferences: AppPreferences by lazy {
        AppPreferences(context)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(context)
    }

    val geminiApiClient: GeminiApiClient by lazy {
        GeminiApiClient()
    }

    val universalApiClient: com.example.jarvisai.data.api.multi.UniversalAiApiClient by lazy {
        com.example.jarvisai.data.api.multi.UniversalAiApiClient(geminiApiClient)
    }

    val settingsRepository: ISettingsRepository by lazy {
        SettingsRepositoryImpl(appPreferences)
    }

    val memoryRepository: IMemoryRepository by lazy {
        MemoryRepositoryImpl(memoryDao)
    }

    val documentRepository: IDocumentRepository by lazy {
        DocumentRepositoryImpl(documentDao)
    }

    val conversationRepository: IConversationRepository by lazy {
        ConversationRepositoryImpl(conversationDao, messageDao)
    }

    val llamaInferenceRepository: com.example.jarvisai.data.repository.LlamaInferenceRepository by lazy {
        com.example.jarvisai.data.repository.LlamaInferenceRepository(context)
    }

    val geminiInferenceRepository: GeminiInferenceRepository by lazy {
        GeminiInferenceRepository(context, geminiApiClient, universalApiClient, settingsRepository, memoryRepository)
    }

    val inferenceRepository: IInferenceRepository by lazy {
        com.example.jarvisai.data.repository.HybridInferenceRepository(llamaInferenceRepository, geminiInferenceRepository)
    }

    val modelRepository: IModelRepository by lazy {
        ModelRepositoryImpl(modelDao)
    }

    val ttsRepository: ITtsRepository by lazy {
        AndroidTtsRepository(context, settingsRepository)
    }
}
