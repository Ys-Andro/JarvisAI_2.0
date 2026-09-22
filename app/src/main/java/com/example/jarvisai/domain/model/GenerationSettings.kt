package com.example.jarvisai.domain.model

data class GenerationSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 1024,
    val contextWindow: Int = 2048,
    val cpuThreads: Int = 4,
    val systemPrompt: String = "You are Jarvis, an intelligent, helpful, and concise AI assistant running completely offline on the user's device.",
    val autoTts: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsEngine: String = "android", // "android" or "openrouter_flux"
    val fluxVoice: String = "flux-alexis-en"
)
