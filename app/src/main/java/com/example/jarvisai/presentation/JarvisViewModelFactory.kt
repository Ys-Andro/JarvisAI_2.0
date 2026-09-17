package com.example.jarvisai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import com.example.jarvisai.di.AppContainer
import com.example.jarvisai.presentation.chat.ChatViewModel
import com.example.jarvisai.presentation.library.LibraryViewModel
import com.example.jarvisai.presentation.models.ModelsViewModel

class JarvisViewModelFactory(
    private val appContainer: AppContainer,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(
                    conversationRepository = appContainer.conversationRepository,
                    inferenceRepository = appContainer.inferenceRepository,
                    settingsRepository = appContainer.settingsRepository,
                    ttsRepository = appContainer.ttsRepository
                ) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(
                    conversationRepository = appContainer.conversationRepository
                ) as T
            }
            modelClass.isAssignableFrom(ModelsViewModel::class.java) -> {
                ModelsViewModel(
                    modelRepository = appContainer.modelRepository,
                    inferenceRepository = appContainer.inferenceRepository,
                    settingsRepository = appContainer.settingsRepository,
                    context = context
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
