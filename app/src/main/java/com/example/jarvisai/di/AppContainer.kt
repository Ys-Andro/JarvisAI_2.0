package com.example.jarvisai.di

import android.content.Context
import com.example.jarvisai.data.local.database.JarvisDatabase
import com.example.jarvisai.data.local.database.dao.ConversationDao
import com.example.jarvisai.data.local.database.dao.MessageDao
import com.example.jarvisai.data.local.database.dao.ModelDao
import com.example.jarvisai.data.local.datastore.AppPreferences
import com.example.jarvisai.data.native.LlamaEngine
import com.example.jarvisai.data.repository.AndroidTtsRepository
import com.example.jarvisai.data.repository.ConversationRepositoryImpl
import com.example.jarvisai.data.repository.LlamaInferenceRepository
import com.example.jarvisai.data.repository.ModelRepositoryImpl
import com.example.jarvisai.data.repository.SettingsRepositoryImpl
import com.example.jarvisai.domain.repository.IConversationRepository
import com.example.jarvisai.domain.repository.IInferenceRepository
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

    val appPreferences: AppPreferences by lazy {
        AppPreferences(context)
    }

    val llamaEngine: LlamaEngine by lazy {
        LlamaEngine()
    }

    val conversationRepository: IConversationRepository by lazy {
        ConversationRepositoryImpl(conversationDao, messageDao)
    }

    val inferenceRepository: IInferenceRepository by lazy {
        LlamaInferenceRepository(llamaEngine)
    }

    val modelRepository: IModelRepository by lazy {
        ModelRepositoryImpl(modelDao)
    }

    val settingsRepository: ISettingsRepository by lazy {
        SettingsRepositoryImpl(appPreferences)
    }

    val ttsRepository: ITtsRepository by lazy {
        AndroidTtsRepository(context)
    }
}
