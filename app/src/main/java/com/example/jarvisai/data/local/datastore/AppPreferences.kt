package com.example.jarvisai.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.domain.model.GenerationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_preferences")

class AppPreferences(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.jarvisDataStore

    private object Keys {
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val TOP_P = floatPreferencesKey("top_p")
        val TOP_K = intPreferencesKey("top_k")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_SIZE = intPreferencesKey("context_size")
        val CPU_THREADS = intPreferencesKey("cpu_threads")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val AUTO_TTS = booleanPreferencesKey("auto_tts")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    private val safePreferences: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val selectedModelId: Flow<String?> = safePreferences.map { preferences ->
        preferences[Keys.SELECTED_MODEL_ID]
    }

    val generationSettings: Flow<GenerationSettings> = safePreferences.map { preferences ->
        GenerationSettings(
            temperature = preferences[Keys.TEMPERATURE] ?: 0.7f,
            topP = preferences[Keys.TOP_P] ?: 0.9f,
            topK = preferences[Keys.TOP_K] ?: 40,
            maxTokens = preferences[Keys.MAX_TOKENS] ?: 1024,
            contextWindow = preferences[Keys.CONTEXT_SIZE] ?: 2048,
            cpuThreads = preferences[Keys.CPU_THREADS] ?: defaultOptimalThreads(),
            systemPrompt = preferences[Keys.SYSTEM_PROMPT]
                ?: "You are Jarvis, an intelligent, helpful, and concise AI assistant running completely offline on the user's device.",
            autoTts = preferences[Keys.AUTO_TTS] ?: false,
            ttsSpeed = preferences[Keys.TTS_SPEED] ?: 1.0f,
            ttsPitch = preferences[Keys.TTS_PITCH] ?: 1.0f
        )
    }

    val appThemeMode: Flow<AppThemeMode> = safePreferences.map { preferences ->
        val rawName = preferences[Keys.THEME_MODE] ?: AppThemeMode.DARK_JARVIS.name
        try {
            AppThemeMode.valueOf(rawName)
        } catch (_: IllegalArgumentException) {
            AppThemeMode.DARK_JARVIS
        }
    }

    suspend fun setSelectedModelId(modelId: String?) {
        dataStore.edit { preferences ->
            if (modelId != null) {
                preferences[Keys.SELECTED_MODEL_ID] = modelId
            } else {
                preferences.remove(Keys.SELECTED_MODEL_ID)
            }
        }
    }

    suspend fun updateGenerationSettings(settings: GenerationSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.TEMPERATURE] = settings.temperature
            preferences[Keys.TOP_P] = settings.topP
            preferences[Keys.TOP_K] = settings.topK
            preferences[Keys.MAX_TOKENS] = settings.maxTokens
            preferences[Keys.CONTEXT_SIZE] = settings.contextWindow
            preferences[Keys.CPU_THREADS] = settings.cpuThreads
            preferences[Keys.SYSTEM_PROMPT] = settings.systemPrompt
            preferences[Keys.AUTO_TTS] = settings.autoTts
            preferences[Keys.TTS_SPEED] = settings.ttsSpeed
            preferences[Keys.TTS_PITCH] = settings.ttsPitch
        }
    }

    suspend fun updateTemperature(temperature: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.TEMPERATURE] = temperature
        }
    }

    suspend fun updateTopP(topP: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.TOP_P] = topP
        }
    }

    suspend fun updateTopK(topK: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.TOP_K] = topK
        }
    }

    suspend fun updateContextSize(contextSize: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CONTEXT_SIZE] = contextSize
        }
    }

    suspend fun updateCpuThreads(threads: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.CPU_THREADS] = threads
        }
    }

    suspend fun updateSystemPrompt(prompt: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun setAppTheme(theme: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = theme.name
        }
    }

    companion object {
        fun defaultOptimalThreads(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            return (cores - 1).coerceIn(2, 6)
        }
    }
}
