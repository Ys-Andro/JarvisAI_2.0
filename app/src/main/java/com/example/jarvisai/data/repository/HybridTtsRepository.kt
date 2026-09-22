package com.example.jarvisai.data.repository

import android.content.Context
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge

class HybridTtsRepository(
    private val context: Context,
    private val settingsRepository: ISettingsRepository
) : ITtsRepository {

    private val androidTts = AndroidTtsRepository(context)
    private val fluxTts = OpenRouterFluxTtsRepository(context, settingsRepository, androidTts)

    override val isSpeaking: Flow<Boolean> = merge(
        androidTts.isSpeaking,
        fluxTts.isSpeaking
    )

    override suspend fun speak(text: String, pitch: Float, speed: Float) {
        val settings = settingsRepository.getSettings().first()
        if (settings.ttsEngine == "openrouter_flux") {
            fluxTts.speak(text, pitch, speed)
        } else {
            androidTts.speak(text, pitch, speed)
        }
    }

    override suspend fun stop() {
        androidTts.stop()
        fluxTts.stop()
    }

    override suspend fun release() {
        androidTts.release()
        fluxTts.release()
    }
}
