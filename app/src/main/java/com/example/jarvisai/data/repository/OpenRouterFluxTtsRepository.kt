package com.example.jarvisai.data.repository

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.example.jarvisai.domain.repository.ISettingsRepository
import com.example.jarvisai.domain.repository.ITtsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OpenRouterFluxTtsRepository(
    private val context: Context,
    private val settingsRepository: ISettingsRepository,
    private val fallbackTts: ITtsRepository
) : ITtsRepository {

    companion object {
        private const val TAG = "OpenRouterFluxTts"
        private const val ENDPOINT = "https://openrouter.ai/api/v1/audio/speech"
    }

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Flow<Boolean> = _isSpeaking.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    override suspend fun speak(text: String, pitch: Float, speed: Float) {
        val cleanText = text.replace(Regex("<[^>]*>"), "").trim()
        if (cleanText.isEmpty()) return

        val settings = settingsRepository.getSettings().first()
        val allKeys = settingsRepository.getAllProviderApiKeys().first()
        val apiKey = allKeys["openrouter"] ?: ""

        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenRouter API key is missing. Falling back to Android TTS.")
            fallbackTts.speak(text, pitch, speed)
            return
        }

        _isSpeaking.value = true
        try {
            val audioBytes = withContext(Dispatchers.IO) {
                val url = URL(ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                val jsonBody = JSONObject().apply {
                    put("model", "deepgram/flux-tts:free")
                    put("input", cleanText)
                    put("voice", settings.fluxVoice.ifBlank { "flux-alexis-en" })
                    put("response_format", "mp3")
                }

                conn.outputStream.use { os ->
                    os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { it.readBytes() }
                } else {
                    val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Log.e(TAG, "Flux TTS error: $responseCode - $errorStream")
                    null
                }
            }

            if (audioBytes != null && audioBytes.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    playAudioBytes(audioBytes)
                }
            } else {
                Log.w(TAG, "Flux TTS returned empty audio. Falling back to Android TTS.")
                _isSpeaking.value = false
                fallbackTts.speak(text, pitch, speed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Flux TTS request", e)
            _isSpeaking.value = false
            fallbackTts.speak(text, pitch, speed)
        }
    }

    private fun playAudioBytes(bytes: ByteArray) {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            _isSpeaking.value = false

            val tempFile = File.createTempFile("flux_tts_", ".mp3", context.cacheDir)
            tempFile.writeBytes(bytes)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener {
                    start()
                    _isSpeaking.value = true
                }
                setOnCompletionListener {
                    _isSpeaking.value = false
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    try { tempFile.delete() } catch (_: Exception) {}
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio bytes", e)
            _isSpeaking.value = false
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    release()
                }
            } catch (_: Exception) {}
            mediaPlayer = null
            _isSpeaking.value = false
        }
        fallbackTts.stop()
    }

    override suspend fun release() {
        stop()
        fallbackTts.release()
    }
}
