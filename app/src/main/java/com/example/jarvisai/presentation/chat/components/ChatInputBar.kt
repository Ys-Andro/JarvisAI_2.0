package com.example.jarvisai.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.ui.theme.JarvisAccentRed
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisBorderGlow
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurfaceVariant
import com.example.jarvisai.ui.theme.JarvisTextPrimary
import com.example.jarvisai.ui.theme.JarvisTextSecondary

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
    isGenerating: Boolean,
    onStopClick: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(JarvisSurfaceVariant)
                .border(1.dp, if (isGenerating) JarvisBorderGlow else JarvisBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Input / Mic Button
            IconButton(
                onClick = onMicClick,
                enabled = isEnabled && !isGenerating,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Dictado por voz",
                    tint = JarvisTextSecondary
                )
            }

            // Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isEnabled) "Pregúntale a Jarvis..." else "Carga un modelo para chatear...",
                        color = JarvisTextSecondary.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    enabled = isEnabled,
                    textStyle = TextStyle(
                        color = JarvisTextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(JarvisPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }

            // Action Button: Stop or Send
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(JarvisAccentRed)
                        .clickable { onStopClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener generación",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                val hasContent = inputText.trim().isNotEmpty()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasContent && isEnabled) {
                                Brush.linearGradient(listOf(JarvisPrimary, Color(0xFF0288D1)))
                            } else {
                                SolidColor(JarvisBorder)
                            }
                        )
                        .clickable(enabled = hasContent && isEnabled) {
                            onSendClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar mensaje",
                        tint = if (hasContent && isEnabled) Color(0xFF001F28) else JarvisTextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
