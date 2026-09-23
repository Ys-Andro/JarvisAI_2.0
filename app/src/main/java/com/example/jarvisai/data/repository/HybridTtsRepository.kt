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

    private val androidTts = AndroidTtsRepository(context, settingsRepository)
    private val fluxTts = OpenRouterFluxTtsRepository(context, settingsRepository, androidTts)

    override val isSpeaking: Flow<Boolean> = merge(
        androidTts.isSpeaking,
        fluxTts.isSpeaking
    )

    private fun isSpanishText(text: String): Boolean {
        val lower = text.lowercase()
        val hasSpanishChars = Regex("[áéíóúñ¿¡]").containsMatchIn(lower)
        val spanishWords = listOf(" el ", " la ", " los ", " las ", " de ", " y ", " en ", " un ", " una ", " es ", " por ", " con ", " que ", " para ", " hola ", " buenas ", " noches ", " todos ", " sistemas ")
        val hasSpanishWords = spanishWords.any { lower.contains(it) }
        return hasSpanishChars || hasSpanishWords
    }

    override suspend fun speak(text: String, pitch: Float, speed: Float) {
        val settings = settingsRepository.getSettings().first()
        val isSpanish = isSpanishText(text)

        if (isSpanish) {
            // Spanish uses Android TTS optimized for Spanish (natural, clear, male voice, pitch 0.9, speed 0.95)
            androidTts.speak(text, pitch, speed)
        } else {
            // English uses Flux TTS (if openrouter_flux engine is active) or Android TTS
            if (settings.ttsEngine == "openrouter_flux") {
                fluxTts.speak(text, pitch, speed)
            } else {
                androidTts.speak(text, pitch, speed)
            }
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
