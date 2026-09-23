package com.example.jarvisai.presentation.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.ui.theme.JarvisAccentGreen
import com.example.jarvisai.ui.theme.JarvisAccentRed
import com.example.jarvisai.ui.theme.JarvisBackground
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurface
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-fidelity Jarvis Floating Orb overlay supporting:
 * - IDLE: Ambient breathing pulse with slow-rotating arc-reactor reticle.
 * - LISTENING: Concentric radar sonar ripples and pulsating microphone icon.
 * - THINKING: Dual high-speed counter-rotating orbital cybernetic rings.
 * - SPEAKING: Reactive voice synthesizer frequency equalizer bars inside core.
 */
@Composable
fun FloatingBubbleOrb(
    visualState: JarvisVisualState,
    isMuted: Boolean,
    isHaloExpanded: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onActionClick: (FloatingBubbleAction) -> Unit,
    onDismissHalo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbAnimations")

    // Ambient breathing pulse for IDLE state
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Slow ambient reticle rotation for IDLE
    val idleReticleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "idleReticleRotation"
    )

    // Fast rotation for THINKING state (outer ring, clockwise)
    val thinkingOuterRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingOuterRotation"
    )

    // Counter-rotation for THINKING state (inner ring, counter-clockwise)
    val thinkingInnerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingInnerRotation"
    )

    // Concentric ripple waves for LISTENING state
    val listeningRippleScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listeningRipple1"
    )
    val listeningRippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listeningRippleAlpha1"
    )
    val listeningRippleScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listeningRipple2"
    )
    val listeningRippleAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "listeningRippleAlpha2"
    )

    // Sonic shockwave pulse for SPEAKING state
    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingPulse"
    )

    val currentScale = when (visualState) {
        JarvisVisualState.IDLE -> idlePulse
        JarvisVisualState.SPEAKING -> speakingPulse
        JarvisVisualState.LISTENING -> 1.06f
        JarvisVisualState.THINKING -> 1.02f
        JarvisVisualState.ERROR -> 1.0f
    }

    val primaryColor = when (visualState) {
        JarvisVisualState.ERROR -> JarvisAccentRed
        JarvisVisualState.LISTENING -> JarvisAccentGreen
        JarvisVisualState.SPEAKING -> Color(0xFF40C4FF)
        JarvisVisualState.THINKING -> JarvisPrimary
        JarvisVisualState.IDLE -> JarvisPrimary
    }

    Box(
        modifier = modifier
            .size(if (isHaloExpanded) 200.dp else 90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Backdrop dismissal for halo
        if (isHaloExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onDismissHalo() }
            )
        }

        // ==========================================
        // 1. VISUAL STATE: IDLE Standby Reticle
        // ==========================================
        if (visualState == JarvisVisualState.IDLE) {
            Canvas(
                modifier = Modifier
                    .size(76.dp)
                    .rotate(idleReticleRotation)
            ) {
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 24f), 0f)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    style = stroke
                )
            }
        }

        // ==========================================
        // 2. VISUAL STATE: LISTENING Concentric Sonar Waves
        // ==========================================
        if (visualState == JarvisVisualState.LISTENING) {
            // Ripple 1
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(listeningRippleScale1)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = primaryColor.copy(alpha = listeningRippleAlpha1),
                        shape = CircleShape
                    )
            )
            // Ripple 2
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(listeningRippleScale2)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = primaryColor.copy(alpha = listeningRippleAlpha2),
                        shape = CircleShape
                    )
            )
        }

        // ==========================================
        // 3. VISUAL STATE: THINKING Cybernetic Vortex Rings
        // ==========================================
        if (visualState == JarvisVisualState.THINKING) {
            // Outer clockwise dashed ring
            Canvas(
                modifier = Modifier
                    .size(78.dp)
                    .rotate(thinkingOuterRotation)
            ) {
                val outerStroke = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                )
                drawCircle(
                    color = JarvisPrimary,
                    style = outerStroke
                )
            }

            // Inner counter-clockwise segmented ring
            Canvas(
                modifier = Modifier
                    .size(68.dp)
                    .rotate(thinkingInnerRotation)
            ) {
                val innerStroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 16f), 0f)
                )
                drawCircle(
                    color = Color(0xFF80D8FF).copy(alpha = 0.8f),
                    style = innerStroke
                )
            }
        }

        // ==========================================
        // 4. VISUAL STATE: SPEAKING Shockwave Aura
        // ==========================================
        if (visualState == JarvisVisualState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(speakingPulse)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = primaryColor.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
            )
        }

        // ==========================================
        // Central Arc Reactor Orb Container
        // ==========================================
        Box(
            modifier = Modifier
                .size(62.dp)
                .scale(currentScale)
                .shadow(
                    elevation = if (visualState == JarvisVisualState.LISTENING || visualState == JarvisVisualState.SPEAKING) 16.dp else 10.dp,
                    shape = CircleShape,
                    spotColor = primaryColor,
                    ambientColor = primaryColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            JarvisSurface,
                            JarvisBackground,
                            Color(0xFF00080D)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            primaryColor,
                            primaryColor.copy(alpha = 0.35f),
                            primaryColor,
                            primaryColor.copy(alpha = 0.15f),
                            primaryColor
                        )
                    ),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isHaloExpanded) onDismissHalo() else onTap()
                        },
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { onLongPress() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Inner Core
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00141C))
                    .border(
                        width = 1.2.dp,
                        color = primaryColor.copy(alpha = 0.65f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (visualState) {
                    JarvisVisualState.SPEAKING -> {
                        // Oscillating voice synthesizer equalizer bars
                        VoiceEqualizerBars(color = primaryColor)
                    }
                    JarvisVisualState.LISTENING -> {
                        // Pulsing microphone icon
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Escuchando",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        // Futuristic central "J" Core Symbol
                        Text(
                            text = "J",
                            color = primaryColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Error indicator
                if (visualState == JarvisVisualState.ERROR) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(JarvisAccentRed)
                    )
                }
            }
        }

        // ==========================================
        // Satellite Halo Quick Action Buttons
        // ==========================================
        AnimatedVisibility(
            visible = isHaloExpanded,
            enter = scaleIn(tween(200)) + fadeIn(tween(160)),
            exit = scaleOut(tween(160)) + fadeOut(tween(130))
        ) {
            Box(modifier = Modifier.matchParentSize()) {
                val radius = 68.dp
                val actions = listOf(
                    Triple(0, FloatingBubbleAction.OPEN_MINI_CHAT, Icons.AutoMirrored.Filled.Chat to "Chat"),
                    Triple(90, FloatingBubbleAction.OPEN_LIVE_MODE, Icons.Default.Mic to "Live"),
                    Triple(180, FloatingBubbleAction.TOGGLE_MUTE, (if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp) to "Voz"),
                    Triple(270, FloatingBubbleAction.CLOSE_BUBBLE, Icons.Default.Close to "Cerrar")
                )

                actions.forEach { (angleDeg, action, iconDesc) ->
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val xOffset = (radius.value * cos(angleRad)).dp
                    val yOffset = (radius.value * sin(angleRad)).dp

                    SatelliteButton(
                        icon = iconDesc.first,
                        description = iconDesc.second,
                        isDestructive = action == FloatingBubbleAction.CLOSE_BUBBLE,
                        onClick = { onActionClick(action) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                    )
                }
            }
        }
    }
}

/**
 * Animated voice synthesizer frequency equalizer bars for the SPEAKING state.
 */
@Composable
private fun VoiceEqualizerBars(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "eqBars")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(230, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 9f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 17f,
        animationSpec = infiniteRepeatable(tween(310, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h5"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        listOf(h1, h2, h3, h4, h5).forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun SatelliteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isDestructive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(38.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        color = if (isDestructive) JarvisAccentRed.copy(alpha = 0.9f) else JarvisSurface,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDestructive) JarvisAccentRed else JarvisPrimary
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (isDestructive) Color.White else JarvisPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
