package com.example.jarvisai.presentation.bubble

/**
 * Visual states of the futuristic Jarvis orb.
 */
enum class JarvisVisualState {
    IDLE,       // Soft ambient ring, slow pulse
    LISTENING,  // Concentric audio wave ripples, intensified glow
    THINKING,   // Rotating orbital ticks / energy ring
    SPEAKING,   // Reactive amplitude pulsation with speech
    ERROR       // Subtle red alert warning ring
}

/**
 * Quick actions triggered from the radial halo.
 */
enum class FloatingBubbleAction {
    OPEN_MINI_CHAT,
    OPEN_LIVE_MODE,
    TOGGLE_MUTE,
    CLOSE_BUBBLE
}

/**
 * Lightweight message representation for the Floating Mini Chat.
 */
data class MiniChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
