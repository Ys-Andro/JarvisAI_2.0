package com.example.jarvisai.presentation.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.domain.model.AppThemeMode
import com.example.jarvisai.presentation.models.ModelsViewModel
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings

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
            // Section 1: Inferencia & Parámetros LLM
            item {
                SettingsSectionCard(
                    title = "MOTOR DE INFERENCIA",
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

                    // Context Size (KV Cache)
                    SliderSettingRow(
                        label = "Ventana de Contexto (Tokens)",
                        valueText = "${settings.contextWindow}",
                        value = settings.contextWindow.toFloat(),
                        onValueChange = { viewModel.updateContextWindow(it.roundToInt()) },
                        valueRange = 512f..8192f,
                        steps = 14
                    )

                    // CPU Threads
                    SliderSettingRow(
                        label = "Hilos de CPU (${uiState.availableCpuCores} núcleos detectados)",
                        valueText = "${settings.cpuThreads} hilos",
                        value = settings.cpuThreads.toFloat(),
                        onValueChange = { viewModel.updateCpuThreads(it.roundToInt()) },
                        valueRange = 1f..uiState.availableCpuCores.coerceAtLeast(4).toFloat(),
                        steps = (uiState.availableCpuCores.coerceAtLeast(4) - 2).coerceAtLeast(0)
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
                }
            }

            // Section 4: Tema Visual
            item {
                SettingsSectionCard(
                    title = "TEMA VISUAL",
                    icon = Icons.Default.Palette
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            label = "DARK JARVIS",
                            isSelected = uiState.appTheme == AppThemeMode.DARK_JARVIS,
                            onClick = { viewModel.setAppTheme(AppThemeMode.DARK_JARVIS) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "AMOLED BLUE",
                            isSelected = uiState.appTheme == AppThemeMode.AMOLED_BLUE,
                            onClick = { viewModel.setAppTheme(AppThemeMode.AMOLED_BLUE) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "CYBER SLATE",
                            isSelected = uiState.appTheme == AppThemeMode.CYBER_TACTICAL,
                            onClick = { viewModel.setAppTheme(AppThemeMode.CYBER_TACTICAL) },
                            modifier = Modifier.weight(1f)
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
            Spacer(modifier = Modifier.height(14.dp))
            content()
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
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) JarvisPrimary else JarvisSurfaceVariant)
            .border(
                1.dp,
                if (isSelected) JarvisBorderGlow else JarvisBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF001F28) else JarvisTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
