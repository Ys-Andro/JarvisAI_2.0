package com.example.jarvisai.presentation.bubble

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.presentation.chat.ChatViewModel
import com.example.jarvisai.presentation.chat.components.MessageBubble
import com.example.jarvisai.ui.theme.*

@Composable
fun BubbleContent(
    isExpanded: Boolean,
    chatViewModel: ChatViewModel,
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val uiState by chatViewModel.uiState.collectAsState()
    
    JarvisAiTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (isExpanded) Alignment.Center else Alignment.TopStart
        ) {
            if (isExpanded) {
                // Mini Chat Window
                MiniChatWindow(
                    uiState = uiState,
                    onClose = onToggleExpand,
                    onSendMessage = { chatViewModel.sendMessage() },
                    onInputChange = { chatViewModel.onInputChange(it) },
                    onStopTts = { chatViewModel.stopTts() },
                    onSpeakClick = { chatViewModel.speakText(it) }
                )
            } else {
                // Floating Bubble Icon
                FloatingBubbleIcon(
                    onToggleExpand = onToggleExpand,
                    onDrag = onDrag
                )
            }
        }
    }
}

@Composable
fun FloatingBubbleIcon(
    onToggleExpand: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Box(
        modifier = Modifier
            .size(70.dp)
            .padding(4.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onToggleExpand() },
        contentAlignment = Alignment.Center
    ) {
        // Outer Glow/Ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(JarvisPrimary.copy(alpha = 0.4f), Color.Transparent),
                        radius = 200f
                    )
                )
                .border(2.dp, JarvisPrimary.copy(alpha = 0.5f), CircleShape)
        )

        // Middle Rotating Ring (Futuristic UI element)
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .graphicsLayer(rotationZ = rotate)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(JarvisPrimary, Color.Transparent, JarvisPrimary, Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Inner Core Orb
        Box(
            modifier = Modifier
                .fillMaxSize(0.6f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(JarvisPrimaryLight, JarvisPrimary, JarvisPrimaryDark)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
        ) {
            // Core highlight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                            radius = 50f
                        )
                    )
            )
        }
    }
}

@Composable
fun MiniChatWindow(
    uiState: com.example.jarvisai.presentation.chat.ChatUiState,
    onClose: () -> Unit,
    onSendMessage: () -> Unit,
    onInputChange: (String) -> Unit,
    onStopTts: () -> Unit,
    onSpeakClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.7f)
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisBackground.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jarvis Mini",
                    style = MaterialTheme.typography.titleMedium,
                    color = JarvisPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                }
            }

            // Messages
            val listState = rememberLazyListState()
            LaunchedEffect(uiState.messages.size) {
                if (uiState.messages.isNotEmpty()) {
                    listState.animateScrollToItem(uiState.messages.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageBubble(
                        message = message,
                        isSpeaking = uiState.isSpeakingTts && uiState.speakingMessageId == message.id,
                        onSpeakClick = { onSpeakClick(it) },
                        onStopSpeakClick = { onStopTts() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = uiState.inputPrompt,
                    onValueChange = onInputChange,
                    placeholder = { Text("Escribe algo...", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = JarvisPrimary,
                        unfocusedIndicatorColor = Color.Gray
                    ),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSendMessage,
                    enabled = uiState.inputPrompt.isNotBlank() && uiState.streamingMessageId == null
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = JarvisPrimary)
                }
            }
        }
    }
}
