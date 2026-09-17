package com.example.jarvisai.presentation.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.presentation.chat.components.ChatInputBar
import com.example.jarvisai.presentation.chat.components.MessageBubble
import com.example.jarvisai.ui.theme.JarvisAccentGreen
import com.example.jarvisai.ui.theme.JarvisAccentRed
import com.example.jarvisai.ui.theme.JarvisBackground
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisBorderGlow
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurface
import com.example.jarvisai.ui.theme.JarvisTextPrimary
import com.example.jarvisai.ui.theme.JarvisTextSecondary
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToModels: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Speech-To-Text launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.onInputChange(
                    if (uiState.inputPrompt.isBlank()) spokenText else "${uiState.inputPrompt} $spokenText"
                )
            }
        }
    }

    // Auto-scroll to bottom on new message or streaming update
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar if any
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                modelName = uiState.activeModel?.name,
                isModelLoaded = uiState.isModelLoaded,
                tokensPerSecond = uiState.tokensPerSecond,
                onModelsClick = onNavigateToModels,
                onHistoryClick = onNavigateToHistory
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Warning if no model is loaded
                if (!uiState.isModelLoaded) {
                    NoModelLoadedBanner(onLoadClick = onNavigateToModels)
                }

                ChatInputBar(
                    inputText = uiState.inputPrompt,
                    onInputChange = viewModel::onInputChange,
                    onSendClick = viewModel::sendMessage,
                    onMicClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para escribir con Jarvis...")
                        }
                        try {
                            speechRecognizerLauncher.launch(intent)
                        } catch (_: Exception) {
                            // STT not supported on this device
                        }
                    },
                    isGenerating = uiState.inferenceStatus is ChatInferenceStatus.Generating,
                    onStopClick = viewModel::stopGeneration,
                    isEnabled = uiState.isModelLoaded
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.messages.isEmpty()) {
                EmptyChatPlaceholder(
                    isModelLoaded = uiState.isModelLoaded,
                    modelName = uiState.activeModel?.name,
                    onConfigureModelClick = onNavigateToModels
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            isSpeaking = uiState.isSpeakingTts && message.role != com.example.jarvisai.domain.model.Role.USER,
                            onSpeakClick = { viewModel.speakText(it) },
                            onStopSpeakClick = { viewModel.stopTts() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    modelName: String?,
    isModelLoaded: Boolean,
    tokensPerSecond: Float,
    onModelsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(JarvisSurface)
            .border(width = 1.dp, color = JarvisBorder)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Title & Model Status Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onModelsClick() }
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isModelLoaded) JarvisAccentGreen else JarvisAccentRed)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "JARVIS AI",
                    color = JarvisTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isModelLoaded) {
                        modelName ?: "Modelo cargado (Offline)"
                    } else {
                        "Sin modelo activo • Tap para cargar"
                    },
                    color = if (isModelLoaded) JarvisPrimary else JarvisTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // Action Icons (Sessions History & Models / Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (tokensPerSecond > 0f) {
                Text(
                    text = String.format("%.1f t/s", tokensPerSecond),
                    color = JarvisPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Historial de conversaciones",
                    tint = JarvisTextSecondary
                )
            }

            IconButton(
                onClick = onModelsClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Gestor de modelos GGUF",
                    tint = JarvisPrimary
                )
            }
        }
    }
}

@Composable
private fun NoModelLoadedBanner(
    onLoadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1C1315))
            .border(1.dp, Color(0xFF5A1E24), RoundedCornerShape(8.dp))
            .clickable { onLoadClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "⚠️ No hay modelo GGUF cargado en RAM",
            color = Color(0xFFFF8A80),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "CARGAR",
            color = JarvisPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyChatPlaceholder(
    isModelLoaded: Boolean,
    modelName: String?,
    onConfigureModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing Core Orb
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(JarvisSurface)
                .border(2.dp, JarvisBorderGlow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = JarvisPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "JARVIS OFFLINE ENGINE",
            color = JarvisTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isModelLoaded) {
                "Modelo activo: ${modelName ?: "GGUF"}\nInferencia privada y 100% offline lista."
            } else {
                "Importa y carga cualquier modelo GGUF (Llama 3, Qwen, Mistral, Gemma) para comenzar a chatear sin internet."
            },
            color = JarvisTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        if (!isModelLoaded) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(JarvisPrimary)
                    .clickable { onConfigureModelClick() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "ABRIR GESTOR DE MODELOS",
                    color = Color(0xFF001F28),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
