package com.example.jarvisai.presentation.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Base64
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.jarvisai.domain.model.CloudAiModel
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
import java.io.InputStream
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

    var showLiveMode by remember { mutableStateOf(false) }

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

    // Photo Picker launcher for multimodal input
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    viewModel.attachImage(uri.toString(), base64, mimeType)
                }
            } catch (e: Exception) {
                // Ignore read error
            }
        }
    }

    // Document Picker launcher for PDF, TXT, DOCX
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val parsed = com.example.jarvisai.data.util.DocumentParser.parseDocument(context, uri)
            viewModel.attachDocument(
                title = parsed.title,
                fileType = parsed.fileType,
                content = parsed.content,
                uriString = uri.toString()
            )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = JarvisBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ChatTopBar(
                    selectedModelId = uiState.selectedModelId,
                    onModelSelected = { viewModel.selectModel(it) },
                    isModelLoaded = uiState.isModelLoaded,
                    isProviderReady = { uiState.isProviderReady(it) },
                    tokensPerSecond = uiState.tokensPerSecond,
                    onShareClick = {
                        val exportText = viewModel.getConversationExportText()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Conversación con Jarvis AI")
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir conversación"))
                    },
                    onModelsClick = onNavigateToModels,
                    onHistoryClick = onNavigateToHistory,
                    onLiveModeClick = { showLiveMode = true }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    // Warning if current selected provider has no API key
                    if (!uiState.isModelLoaded) {
                        val currentModel = CloudAiModel.findById(uiState.selectedModelId)
                        NoModelLoadedBanner(
                            providerName = currentModel.provider.displayName,
                            onLoadClick = onNavigateToModels
                        )
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
                        onPickImageClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        attachedImageUri = uiState.attachedImageUri,
                        onRemoveImageClick = { viewModel.clearAttachedImage() },
                        attachedDocumentTitle = uiState.attachedDocumentTitle,
                        attachedDocumentType = uiState.attachedDocumentType,
                        onPickDocumentClick = {
                            documentPickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/plain",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onRemoveDocumentClick = { viewModel.removeAttachedDocument() },
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
                    val currentModel = CloudAiModel.ALL_MODELS.firstOrNull { it.id == uiState.selectedModelId }
                    EmptyChatPlaceholder(
                        isModelLoaded = uiState.isModelLoaded,
                        modelName = currentModel?.name ?: uiState.activeModel?.name,
                        onConfigureModelClick = onNavigateToModels
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
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

        if (showLiveMode) {
            com.example.jarvisai.presentation.chat.components.JarvisLiveModeDialog(
                isSpeakingTts = uiState.isSpeakingTts,
                onUserSpoken = { prompt, onComplete ->
                    viewModel.sendMessageDirect(prompt, onComplete)
                },
                onStopTts = { viewModel.stopTts() },
                onDismiss = { showLiveMode = false }
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
    isModelLoaded: Boolean,
    isProviderReady: (com.example.jarvisai.domain.model.ModelProvider) -> Boolean = { true },
    tokensPerSecond: Float,
    onShareClick: () -> Unit,
    onModelsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLiveModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val currentModel = CloudAiModel.ALL_MODELS.firstOrNull { it.id == selectedModelId } ?: CloudAiModel.ALL_MODELS.first()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(JarvisSurface)
            .border(width = 1.dp, color = JarvisBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Model Selector Dropdown trigger (Minimalist 3 lines only)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F1E33))
                    .border(1.dp, JarvisBorder, RoundedCornerShape(20.dp))
                    .clickable { isDropdownExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Desplegar modelos",
                    tint = JarvisPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isModelLoaded) JarvisAccentGreen else JarvisAccentRed)
                )
            }

            DropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false },
                modifier = Modifier
                    .background(JarvisSurface)
                    .border(1.dp, JarvisBorder, RoundedCornerShape(8.dp))
            ) {
                CloudAiModel.ALL_MODELS.forEach { model ->
                    val isSelected = model.id == selectedModelId
                    val isReady = isProviderReady(model.provider)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = model.name,
                                        color = if (isSelected) JarvisPrimary else JarvisTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isReady) Color(0x2000E5FF) else Color(0x25FF5252))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (isReady) "Listo" else "Sin Key",
                                            color = if (isReady) JarvisAccentGreen else Color(0xFFFF8A80),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Text(
                                    text = model.description,
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(model.id)
                            isDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Action Icons (Speed, Share, History, Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                onClick = onLiveModeClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Modo Live de voz continua",
                    tint = JarvisPrimary
                )
            }

            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Compartir o exportar conversación",
                    tint = JarvisTextSecondary
                )
            }

            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Historial de conversaciones",
                    tint = JarvisTextSecondary
                )
            }

            IconButton(
                onClick = onModelsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuración y API Keys",
                    tint = JarvisPrimary
                )
            }
        }
    }
}

@Composable
private fun NoModelLoadedBanner(
    providerName: String,
    onLoadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1416))
            .border(1.dp, Color(0xFF6A2027), RoundedCornerShape(8.dp))
            .clickable { onLoadClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "⚠️ Falta la API Key de $providerName",
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Toca aquí para ingresarla en Ajustes o selecciona Google Gemini",
                color = JarvisTextSecondary,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AJUSTES",
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
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = JarvisPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "JARVIS MULTI-MODEL AI",
            color = JarvisTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isModelLoaded) {
                "Motor activo: ${modelName ?: "Multi-Model AI"}\nSoporte para Gemini, OpenAI, DeepSeek, Groq y Anthropic."
            } else {
                "Conéctate con tu proveedor preferido (Gemini, OpenAI, DeepSeek, Groq o Claude) para comenzar."
            },
            color = JarvisTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(JarvisPrimary)
                .clickable { onConfigureModelClick() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "CONFIGURAR API KEY Y MODELO",
                color = Color(0xFF001F28),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
