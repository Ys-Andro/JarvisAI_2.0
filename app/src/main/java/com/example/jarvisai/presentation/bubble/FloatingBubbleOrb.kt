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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.ui.theme.JarvisAccentGreen
import com.example.jarvisai.ui.theme.JarvisAccentRed
import com.example.jarvisai.ui.theme.JarvisBackground
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisBorderGlow
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurface
import kotlin.math.cos
import kotlin.math.sin

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

    // Slow ambient breathing pulse for idle
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    // Fast rotation for thinking state
    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkingRotation"
    )

    // Concentric ripple waves for listening state
    val rippleScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    val rippleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha1"
    )

    // Pulsing waveform for speaking state
    val speakingPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
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
        else -> JarvisPrimary
    }

    Box(
        modifier = modifier
            .size(if (isHaloExpanded) 200.dp else 90.dp),
        contentAlignment = Alignment.Center
    ) {
        // Halo Backdrop Tap to Dismiss Halo
        if (isHaloExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onDismissHalo() }
            )
        }

        // Concentric ripples when LISTENING
        if (visualState == JarvisVisualState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(rippleScale1)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        color = primaryColor.copy(alpha = rippleAlpha1),
                        shape = CircleShape
                    )
            )
        }

        // Rotating Energy Ring when THINKING
        if (visualState == JarvisVisualState.THINKING) {
            Canvas(
                modifier = Modifier
                    .size(76.dp)
                    .rotate(thinkingRotation)
            ) {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f), 0f)
                )
                drawCircle(
                    color = JarvisPrimary,
                    style = stroke
                )
            }
        }

        // Main Orb Container
        Box(
            modifier = Modifier
                .size(62.dp)
                .scale(currentScale)
                .shadow(
                    elevation = 12.dp,
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
                            Color.Black
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            primaryColor,
                            primaryColor.copy(alpha = 0.3f),
                            primaryColor,
                            primaryColor.copy(alpha = 0.1f),
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
            // Inner Core Arc Reactor Ring
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF001B24))
                    .border(
                        width = 1.dp,
                        color = primaryColor.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Futuristic central "J" Core Symbol
                Text(
                    text = "J",
                    color = primaryColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Error micro-dot
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

        // Radial Halo Buttons when Long-Pressed
        AnimatedVisibility(
            visible = isHaloExpanded,
            enter = scaleIn(tween(220)) + fadeIn(tween(180)),
            exit = scaleOut(tween(180)) + fadeOut(tween(150))
        ) {
            Box(modifier = Modifier.matchParentSize()) {
                val radius = 68.dp
                val actions = listOf(
                    Triple(0, FloatingBubbleAction.OPEN_MINI_CHAT, Icons.Default.Chat to "Chat"),
                    Triple(90, FloatingBubbleAction.OPEN_LIVE_MODE, Icons.Default.Mic to "Live"),
                    Triple(180, FloatingBubbleAction.TOGGLE_MUTE, (if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp) to "Voz"),
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
                        onClick = {
                            onActionClick(action)
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                    )
                }
            }
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
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .border(
                width = 1.2.dp,
                color = if (isDestructive) JarvisAccentRed else JarvisPrimary,
                shape = CircleShape
            )
            .clickable { onClick() },
        color = JarvisSurface,
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (isDestructive) JarvisAccentRed else JarvisPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
