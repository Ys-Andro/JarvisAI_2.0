package com.example.jarvisai.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.presentation.models.ModelsViewModel
import com.example.jarvisai.ui.theme.JarvisAccentGreen
import com.example.jarvisai.ui.theme.JarvisBackground
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisBorderGlow
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurface
import com.example.jarvisai.ui.theme.JarvisSurfaceVariant
import com.example.jarvisai.ui.theme.JarvisTextPrimary
import com.example.jarvisai.ui.theme.JarvisTextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: ModelsViewModel,
    onBackClick: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    var apiKeyInput by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey ?: "") }
    var isApiKeysExpanded by remember { mutableStateOf(true) }
    var isMemoryExpanded by remember { mutableStateOf(true) }
    var isAgentExpanded by remember { mutableStateOf(true) }
    var isDocumentsExpanded by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground,
        topBar = {
            SettingsTopBar(onBackClick = onBackClick)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section -1: Long-term Memory
            item {
                SettingsSectionCard(
                    title = "MEMORIA A LARGO PLAZO",
                    icon = Icons.Default.SmartToy,
                    isCollapsible = true,
                    isExpanded = isMemoryExpanded,
                    onToggleExpand = { isMemoryExpanded = !isMemoryExpanded }
                ) {
                    Text(
                        text = "Jarvis recuerda información importante del usuario (nombre, preferencias, proyectos) entre distintas conversaciones y la integra automáticamente.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToMemory,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "VER Y EDITAR RECUERDOS",
                            color = JarvisBackground,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Section -0.5: Multiple Personalities / Agents
            item {
                SettingsSectionCard(
                    title = "PERSONALIDADES / AGENTES",
                    icon = Icons.Default.SmartToy,
                    isCollapsible = true,
                    isExpanded = isAgentExpanded,
                    onToggleExpand = { isAgentExpanded = !isAgentExpanded }
                ) {
                    Text(
                        text = "Selecciona la personalidad de Jarvis. Cada agente cuenta con su propio system prompt especializado.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val agents = com.example.jarvisai.domain.model.Agent.DEFAULT_AGENTS
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (agent in agents) {
                            val isSelected = uiState.selectedAgentId == agent.id
                            AgentOptionCard(
                                agent = agent,
                                isSelected = isSelected,
                                onClick = { viewModel.setSelectedAgent(agent.id) }
                            )
                        }
                    }
                }
            }

            // Section -0.2: Analyzed Documents
            item {
                SettingsSectionCard(
                    title = "DOCUMENTOS ANALIZADOS",
                    icon = Icons.Default.Description,
                    isCollapsible = true,
                    isExpanded = isDocumentsExpanded,
                    onToggleExpand = { isDocumentsExpanded = !isDocumentsExpanded }
                ) {
                    Text(
                        text = "Consulta y lee los documentos PDF, TXT y DOCX analizados por Jarvis. Disponibles tanto con internet como en modo offline.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToDocuments,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "VER DOCUMENTOS ANALIZADOS",
                            color = JarvisBackground,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Section 0: Multi-provider API Keys & Model Configuration
            item {
                SettingsSectionCard(
                    title = "CLAVES API POR PROVEEDOR",
                    icon = Icons.Default.Key,
                    isCollapsible = true,
                    isExpanded = isApiKeysExpanded,
                    onToggleExpand = { isApiKeysExpanded = !isApiKeysExpanded }
                ) {
                    Text(
                        text = "Configura de manera independiente las API Keys para cada proveedor (Gemini, OpenAI, DeepSeek, Groq, Anthropic). Cada proveedor almacena su clave de forma segura y aislada.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val providers = listOf(
                        Triple("gemini", "Google Gemini", "AIzaSy... (o usa .env)"),
                        Triple("openrouter", "OpenRouter", "sk-or-v1-..."),
                        Triple("openai", "OpenAI", "sk-proj-..."),
                        Triple("deepseek", "DeepSeek", "sk-..."),
                        Triple("groq", "Groq Cloud", "gsk_..."),
                        Triple("anthropic", "Anthropic Claude", "sk-ant-api...")
                    )

                    var selectedProviderTab by remember { mutableStateOf("gemini") }

                    // Provider Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        providers.forEach { (pId, pName, _) ->
                            val isCurrentTab = selectedProviderTab == pId
                            val hasKey = !uiState.providerApiKeys[pId].isNullOrBlank() || (pId == "gemini" && !uiState.apiKey.isNullOrBlank())
                            val tabLabel = when (pId) {
                                "openrouter" -> "OpenRouter"
                                "anthropic" -> "Claude"
                                "gemini" -> "Gemini"
                                "openai" -> "OpenAI"
                                "deepseek" -> "DeepSeek"
                                "groq" -> "Groq"
                                else -> pName.split(" ").last()
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentTab) JarvisPrimary else JarvisSurfaceVariant)
                                    .clickable { selectedProviderTab = pId }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = tabLabel,
                                        color = if (isCurrentTab) Color(0xFF001F28) else JarvisTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (hasKey) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 3.dp)
                                                .size(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (isCurrentTab) Color(0xFF001F28) else JarvisAccentGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input for selected provider
                    val currentProvider = providers.first { it.first == selectedProviderTab }
                    val currentSavedKey = uiState.providerApiKeys[currentProvider.first] ?: if (currentProvider.first == "gemini") uiState.apiKey else null
                    var currentKeyInput by remember(selectedProviderTab, currentSavedKey) {
                        mutableStateOf(currentSavedKey ?: "")
                    }

                    Text(
                        text = "CLAVE PARA ${currentProvider.second.uppercase()}",
                        color = JarvisPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = currentKeyInput,
                        onValueChange = { currentKeyInput = it },
                        placeholder = {
                            Text(
                                text = currentProvider.third,
                                color = JarvisTextSecondary.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.updateProviderApiKey(currentProvider.first, currentKeyInput.trim())
                                if (currentProvider.first == "gemini") {
                                    viewModel.updateApiKey(currentKeyInput.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisPrimary,
                                contentColor = Color(0xFF001F28)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "GUARDAR",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.verifyApiKey(currentProvider.first, currentKeyInput)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisSurfaceVariant,
                                contentColor = JarvisPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "VERIFICAR",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }

                        if (!currentSavedKey.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    currentKeyInput = ""
                                    viewModel.updateProviderApiKey(currentProvider.first, "")
                                    if (currentProvider.first == "gemini") {
                                        viewModel.updateApiKey("")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = JarvisSurfaceVariant,
                                    contentColor = Color(0xFFFF8A80)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "BORRAR",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.statusMessage!!,
                            color = if (uiState.statusMessage!!.contains("✓") || uiState.statusMessage!!.contains("guardada")) JarvisAccentGreen else Color(0xFFFF8A80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Optional Custom Endpoint for OpenAI or Compatible APIs (LocalAI, Ollama, LMStudio, etc.)
                    if (currentProvider.first == "openai") {
                        Spacer(modifier = Modifier.height(12.dp))
                        var customEndpointInput by remember(uiState.customOpenAiEndpoint) {
                            mutableStateOf(uiState.customOpenAiEndpoint ?: "")
                        }
                        Text(
                            text = "ENDPOINT PERSONALIZADO (OPCIONAL)",
                            color = JarvisTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customEndpointInput,
                            onValueChange = { customEndpointInput = it },
                            placeholder = {
                                Text(
                                    text = "https://api.openai.com/v1 (o servidor local)",
                                    color = JarvisTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisPrimary,
                                unfocusedBorderColor = JarvisBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.updateCustomOpenAiEndpoint(customEndpointInput.trim()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisSurfaceVariant,
                                contentColor = JarvisPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "GUARDAR ENDPOINT",
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "MODELO DE INTELIGENCIA ARTIFICIAL",
                        color = JarvisTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    com.example.jarvisai.domain.model.CloudAiModel.ALL_MODELS.forEach { model ->
                        val isSelected = uiState.selectedGeminiModel == model.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) JarvisPrimary.copy(alpha = 0.15f) else JarvisSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) JarvisPrimary else JarvisBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateSelectedGeminiModel(model.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = model.name,
                                        color = if (isSelected) JarvisPrimary else JarvisTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "[${model.provider.displayName}]",
                                        color = JarvisPrimary.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = model.description,
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "ACTIVO",
                                    color = JarvisAccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: Inferencia & Parámetros LLM
            item {
                SettingsSectionCard(
                    title = "PARÁMETROS DE GENERACIÓN",
                    icon = Icons.Default.Tune
                ) {
                    // Temperature
                    SliderSettingRow(
                        label = "Temperatura (Creatividad)",
                        valueText = String.format("%.2f", settings.temperature),
                        value = settings.temperature,
                        onValueChange = { viewModel.updateTemperature(it) },
                        valueRange = 0.0f..1.5f,
                        steps = 14
                    )

                    // Top-P
                    SliderSettingRow(
                        label = "Top-P (Nucleus Sampling)",
                        valueText = String.format("%.2f", settings.topP),
                        value = settings.topP,
                        onValueChange = { viewModel.updateTopP(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8
                    )

                    // Top-K
                    SliderSettingRow(
                        label = "Top-K",
                        valueText = "${settings.topK}",
                        value = settings.topK.toFloat(),
                        onValueChange = { viewModel.updateTopK(it.roundToInt()) },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            }

            // Section 2: System Prompt
            item {
                SettingsSectionCard(
                    title = "PERSONALIDAD & SYSTEM PROMPT",
                    icon = Icons.Default.Info
                ) {
                    Text(
                        text = "Define la directiva inicial que modela el comportamiento de Jarvis.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = settings.systemPrompt,
                        onValueChange = { viewModel.updateSystemPrompt(it) },
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisPrimary,
                            unfocusedBorderColor = JarvisBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Section 3: Text to Speech
            item {
                SettingsSectionCard(
                    title = "SÍNTESIS DE VOZ (TTS)",
                    icon = Icons.Default.RecordVoiceOver
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lectura automática",
                                color = JarvisTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Leer en voz alta cada respuesta completada.",
                                color = JarvisTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.autoTts,
                            onCheckedChange = { viewModel.updateAutoTts(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF001F28),
                                checkedTrackColor = JarvisPrimary,
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisSurfaceVariant
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "MOTOR DE TTS",
                        color = JarvisPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val engines = listOf(
                        Triple("android", "Android TTS", "Motor integrado del sistema (Offline)"),
                        Triple("openrouter_flux", "OpenRouter Flux TTS", "Deepgram Flux via OpenRouter (Gratis, Cloud)")
                    )

                    engines.forEach { (id, name, desc) ->
                        val isSelected = settings.ttsEngine == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) JarvisPrimary.copy(alpha = 0.15f) else JarvisSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) JarvisPrimary else JarvisBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateTtsEngine(id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = if (isSelected) JarvisPrimary else JarvisTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = desc,
                                    color = JarvisTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "ACTIVO",
                                    color = JarvisAccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    if (settings.ttsEngine == "android") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "VOZ DE ANDROID (ESTILO JARVIS)",
                            color = JarvisPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tono grave y asistente optimizado por defecto (Pitch: 0.9x)",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val androidVoiceOptions = listOf(
                            Pair("", "Predeterminado (Automático - Masculino / Grave)"),
                            Pair("en-us-x-sfg#male_1-local", "Inglés US - Masculino 1"),
                            Pair("en-gb-x-rjs#male_1-local", "Inglés UK - Británico Jarvis"),
                            Pair("es-es-x-eee#male_1-local", "Español - Masculino Asistente")
                        )

                        androidVoiceOptions.forEach { (voiceId, voiceName) ->
                            val isSelectedVoice = settings.androidVoiceName == voiceId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelectedVoice) JarvisPrimary.copy(alpha = 0.12f) else JarvisSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelectedVoice) JarvisPrimary else JarvisBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.updateAndroidVoiceName(voiceId) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = voiceName,
                                    color = if (isSelectedVoice) JarvisPrimary else JarvisTextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (isSelectedVoice) {
                                    Text(
                                        text = "✓",
                                        color = JarvisAccentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (settings.ttsEngine == "openrouter_flux") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "VOCES MASCULINAS / JARVIS (OPENROUTER FLUX)",
                            color = JarvisPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Modelo: deepgram/flux-tts:free (Voces graves y de asistente)",
                            color = JarvisTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val voices = listOf(
                            Triple("flux-cliff-en", "Cliff (Hombre grave)", "Estilo Jarvis — Grave y autoritaria"),
                            Triple("flux-conor-en", "Conor (Hombre británico grave)", "Tono británico formal y profundo"),
                            Triple("flux-jack-en", "Jack (Hombre británico profesional)", "Voz corporativa seria"),
                            Triple("flux-kit-en", "Kit (Hombre británico claro)", "Tono claro y articulado"),
                            Triple("flux-donovan-en", "Donovan (Hombre calmado)", "Tono pausado y sereno"),
                            Triple("flux-bruce-en", "Bruce (Hombre natural)", "Tono natural conversacional")
                        )

                        voices.forEach { (voiceId, name, desc) ->
                            val isSelectedVoice = settings.fluxVoice == voiceId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelectedVoice) JarvisPrimary.copy(alpha = 0.12f) else JarvisSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelectedVoice) JarvisPrimary else JarvisBorder,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.updateFluxVoice(voiceId) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        color = if (isSelectedVoice) JarvisPrimary else JarvisTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = desc,
                                        color = JarvisTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                if (isSelectedVoice) {
                                    Text(
                                        text = "✓",
                                        color = JarvisAccentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (uiState.providerApiKeys["openrouter"].isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "⚠️ Falta configurar la API Key de OpenRouter arriba para usar Flux TTS.",
                                color = Color(0xFFFFB74D),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.testVoice() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_voice_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisPrimary,
                            contentColor = Color(0xFF001F28)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "🔊 Probar voz actual",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }


        }
    }
}

@Composable
private fun SettingsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(JarvisSurface)
            .border(width = 1.dp, color = JarvisBorder)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = JarvisTextPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AJUSTES DE JARVIS",
            color = JarvisTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCollapsible && onToggleExpand != null) {
                            Modifier.clickable { onToggleExpand() }
                        } else Modifier
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = JarvisPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = JarvisTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                if (isCollapsible && onToggleExpand != null) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Contraer" else "Expandir",
                        tint = JarvisPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = JarvisTextSecondary,
                fontSize = 13.sp
            )
            Text(
                text = valueText,
                color = JarvisPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = JarvisPrimary,
                activeTrackColor = JarvisPrimary,
                inactiveTrackColor = JarvisSurfaceVariant
            )
        )
    }
}

@Composable
private fun AgentOptionCard(
    agent: com.example.jarvisai.domain.model.Agent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                1.dp,
                if (isSelected) JarvisPrimary else JarvisBorder,
                RoundedCornerShape(10.dp)
            ),
        color = if (isSelected) JarvisSurfaceVariant else JarvisSurface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = agent.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    color = if (isSelected) JarvisPrimary else JarvisTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = agent.roleDescription,
                    color = JarvisTextSecondary,
                    fontSize = 11.sp
                )
            }
            if (isSelected) {
                Surface(
                    color = JarvisPrimary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ACTIVO",
                        color = JarvisBackground,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}


