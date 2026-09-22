package com.example.jarvisai.data.repository

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

class AndroidTtsRepository(
    private val context: Context,
    private val settingsRepository: ISettingsRepository
) : ITtsRepository, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AndroidTtsRepository"
        private const val UTTERANCE_ID = "jarvis_tts_utterance"
    }

    private var tts: TextToSpeech? = null
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Flow<Boolean> = _isSpeaking.asStateFlow()

    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Default language missing data, falling back to English US")
                tts?.setLanguage(Locale.US)
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
            isInitialized = true
            Log.i(TAG, "TextToSpeech initialized successfully.")
        } else {
            Log.e(TAG, "Failed to initialize TextToSpeech engine.")
        }
    }

    override suspend fun speak(text: String, pitch: Float, speed: Float) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "Cannot speak: TTS not initialized")
            return
        }
        val settings = settingsRepository.getSettings().first()
        val cleanText = text.replace(Regex("<[^>]*>"), "")

        // Apply deeper Jarvis pitch by default (0.9f) or user setting
        val effectivePitch = if (pitch == 1.0f) settings.ttsPitch else pitch
        tts?.setPitch(effectivePitch)
        tts?.setSpeechRate(speed)

        try {
            if (settings.androidVoiceName.isNotBlank()) {
                tts?.voices?.find { it.name == settings.androidVoiceName }?.let { voice ->
                    tts?.voice = voice
                }
            } else {
                val bestVoice = tts?.voices?.firstOrNull {
                    it.name.contains("male", ignoreCase = true) &&
                    (it.locale.language == "en" || it.locale.language == "es")
                } ?: tts?.voices?.firstOrNull {
                    it.locale.language == "en" || it.locale.language == "es"
                } ?: tts?.voices?.firstOrNull()

                if (bestVoice != null) {
                    tts?.voice = bestVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting Android voice: ${e.message}")
        }

        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override suspend fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    override suspend fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isSpeaking.value = false
        isInitialized = false
    }
}
