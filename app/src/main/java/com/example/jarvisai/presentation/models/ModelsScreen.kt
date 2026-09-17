package com.example.jarvisai.presentation.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.ui.theme.JarvisAccentGreen
import com.example.jarvisai.ui.theme.JarvisAccentRed
import com.example.jarvisai.ui.theme.JarvisBackground
import com.example.jarvisai.ui.theme.JarvisBorder
import com.example.jarvisai.ui.theme.JarvisBorderGlow
import com.example.jarvisai.ui.theme.JarvisPrimary
import com.example.jarvisai.ui.theme.JarvisSurface
import com.example.jarvisai.ui.theme.JarvisSurfaceVariant
import com.example.jarvisai.ui.theme.JarvisTextPrimary
import com.example.jarvisai.ui.theme.JarvisTextSecondary

@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onBackClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var modelToDelete by remember { mutableStateOf<LocalGgufModel?>(null) }

    // SAF File Picker for .gguf files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importGgufFromUri(it) }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModelsTopBar(
                onBackClick = onBackClick,
                onSettingsClick = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                containerColor = JarvisPrimary,
                contentColor = Color(0xFF001F28),
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Importar modelo GGUF"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Active Model Banner
                ActiveModelCard(
                    activeModel = uiState.activeModel,
                    isLoading = uiState.isLoadingModel,
                    loadingName = uiState.loadingModelName,
                    onUnloadClick = { viewModel.unloadModel() }
                )

                // Models List Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BIBLIOTECA LOCAL (${uiState.models.size})",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "FORMATO GGUF",
                        color = JarvisPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (uiState.models.isEmpty()) {
                    EmptyModelsPlaceholder(
                        onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.models,
                            key = { it.id }
                        ) { model ->
                            val isCurrentlyLoaded = uiState.activeModel?.id == model.id
                            ModelItemCard(
                                model = model,
                                isLoaded = isCurrentlyLoaded,
                                onLoadClick = { viewModel.loadModel(model) },
                                onUnloadClick = { viewModel.unloadModel() },
                                onDeleteClick = { modelToDelete = model }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog to delete a model
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            containerColor = JarvisSurface,
            title = {
                Text(
                    text = "ELIMINAR MODELO",
                    color = JarvisAccentRed,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Eliminar '${model.name}' (${model.formattedSize}) del almacenamiento interno?",
                    color = JarvisTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(model)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = JarvisAccentRed)
                ) {
                    Text("ELIMINAR", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { modelToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = JarvisTextSecondary)
                ) {
                    Text("CANCELAR", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

@Composable
private fun ModelsTopBar(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = JarvisTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GESTOR DE MODELOS",
                color = JarvisTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }

        IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configuración",
                tint = JarvisPrimary
            )
        }
    }
}

@Composable
private fun ActiveModelCard(
    activeModel: LocalGgufModel?,
    isLoading: Boolean,
    loadingName: String?,
    onUnloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (activeModel != null) {
                    Brush.linearGradient(listOf(Color(0xFF0B192C), Color(0xFF070F1C)))
                } else {
                    Brush.linearGradient(listOf(JarvisSurface, Color(0xFF0A0E17)))
                }
            )
            .border(
                1.dp,
                if (activeModel != null) JarvisBorderGlow else JarvisBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (activeModel != null) JarvisAccentGreen else JarvisAccentRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeModel != null) "EN MEMORIA RAM (ACTIVO)" else "SIN MODELO CARGADO",
                        color = if (activeModel != null) JarvisAccentGreen else JarvisTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = JarvisPrimary,
                        strokeWidth = 2.dp
                    )
                } else if (activeModel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2A1418))
                            .border(1.dp, Color(0xFF632029), RoundedCornerShape(8.dp))
                            .clickable { onUnloadClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "DESCARGAR",
                            color = JarvisAccentRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Text(
                    text = "Mapeando tensores: ${loadingName ?: "modelo"}...",
                    color = JarvisPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else if (activeModel != null) {
                Text(
                    text = activeModel.name,
                    color = JarvisTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = activeModel.formattedSize,
                        color = JarvisPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• ${activeModel.quantization}",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• Ctx ${activeModel.contextLength}",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(
                    text = "Selecciona un modelo abajo para cargarlo y chatear 100% offline.",
                    color = JarvisTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ModelItemCard(
    model: LocalGgufModel,
    isLoaded: Boolean,
    onLoadClick: () -> Unit,
    onUnloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurface)
            .border(
                1.dp,
                if (isLoaded) JarvisPrimary else JarvisBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(JarvisSurfaceVariant)
                    .border(1.dp, if (isLoaded) JarvisPrimary else JarvisBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = if (isLoaded) JarvisPrimary else JarvisTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = model.name,
                    color = JarvisTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = model.formattedSize,
                        color = JarvisPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "• ${model.quantization}",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isLoaded) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF00363A))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CARGADO",
                        color = JarvisPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisPrimary)
                        .clickable { onLoadClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CARGAR",
                        color = Color(0xFF001F28),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = JarvisTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyModelsPlaceholder(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = JarvisTextSecondary,
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Sin modelos GGUF",
            color = JarvisTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Pulsa el botón de abajo para importar cualquier modelo .gguf que tengas en tu almacenamiento.",
            color = JarvisTextSecondary,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(JarvisPrimary)
                .clickable { onImportClick() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "SELECCIONAR ARCHIVO GGUF",
                color = Color(0xFF001F28),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
