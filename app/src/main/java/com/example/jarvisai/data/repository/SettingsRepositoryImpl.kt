package com.example.jarvisai.data.repository

import com.example.jarvisai.data.local.datastore.AppPreferences
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import com.example.jarvisai.domain.repository.ISettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
    private val appPreferences: AppPreferences
) : ISettingsRepository {

    override fun getSettings(): Flow<GenerationSettings> {
        return appPreferences.generationSettings
    }

    override suspend fun updateSettings(settings: GenerationSettings) {
        appPreferences.updateGenerationSettings(settings)
    }

    override fun getAppTheme(): Flow<AppThemeMode> {
        return appPreferences.appThemeMode
    }

    override suspend fun setAppTheme(theme: AppThemeMode) {
        appPreferences.setAppTheme(theme)
    }
}
