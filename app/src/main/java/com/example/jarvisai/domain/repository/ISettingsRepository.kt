package com.example.jarvisai.domain.repository

import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    fun getSettings(): Flow<GenerationSettings>
    suspend fun updateSettings(settings: GenerationSettings)
    fun getAppTheme(): Flow<AppThemeMode>
    suspend fun setAppTheme(theme: AppThemeMode)
}
