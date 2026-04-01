package com.gpxvideo.feature.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.Resolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onExportComplete: (String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Video") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = uiState.exportState) {
                is ExportState.Idle -> {
                    ExportSettingsSection(
                        settings = uiState.settings,
                        estimatedSizeMb = uiState.estimatedSizeMb,
                        onFormatChanged = viewModel::updateFormat,
                        onResolutionChanged = viewModel::updateResolution,
                        onFrameRateChanged = viewModel::updateFrameRate
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = viewModel::startExport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Start Export", modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                is ExportState.Exporting -> {
                    ExportProgressSection(
                        phase = state.phase,
                        progress = state.progress,
                        startTimeMs = state.startTimeMs,
                        onCancel = viewModel::cancelExport
                    )
                }

                is ExportState.Complete -> {
                    ExportCompleteSection(
                        outputPath = state.outputPath,
                        fileSizeBytes = state.fileSizeBytes,
                        onShare = {
                            shareVideoFile(context, state.outputPath)
                        },
                        onSaveToGallery = {
                            viewModel.saveToGallery(state.outputPath)
                        },
                        onDone = onNavigateBack
                    )
                }

                is ExportState.Error -> {
                    ExportErrorSection(
                        message = state.message,
                        onRetry = viewModel::resetState,
                        onDismiss = onNavigateBack
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSettingsSection(
    settings: com.gpxvideo.core.model.OutputSettings,
    estimatedSizeMb: Float,
    onFormatChanged: (ExportFormat) -> Unit,
    onResolutionChanged: (Resolution) -> Unit,
    onFrameRateChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Export Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Format dropdown
            DropdownSelector(
                label = "Format",
                selectedValue = settings.format.displayName,
                options = ExportFormat.entries.map { it.displayName },
                onOptionSelected = { name ->
                    ExportFormat.entries.find { it.displayName == name }?.let(onFormatChanged)
                }
            )

            // Resolution dropdown
            DropdownSelector(
                label = "Resolution",
                selectedValue = settings.resolution.displayName,
                options = Resolution.entries.map { it.displayName },
                onOptionSelected = { name ->
                    Resolution.entries.find { it.displayName == name }?.let(onResolutionChanged)
                }
            )

            // Frame rate dropdown
            val frameRates = listOf(24, 30, 60)
            DropdownSelector(
                label = "Frame Rate",
                selectedValue = "${settings.frameRate} fps",
                options = frameRates.map { "$it fps" },
                onOptionSelected = { value ->
                    value.replace(" fps", "").toIntOrNull()?.let(onFrameRateChanged)
                }
            )

            // Audio codec display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Audio Codec", style = MaterialTheme.typography.bodyMedium)
                Text(
                    settings.audioCodec.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Estimated size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Estimated Size", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "~${"%.1f".format(estimatedSizeMb)} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportProgressSection(
    phase: ExportPhase,
    progress: Float,
    startTimeMs: Long,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Exporting...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                phase.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val elapsed = System.currentTimeMillis() - startTimeMs
            if (progress > 0.01f && elapsed > 1000) {
                val estimatedTotal = elapsed / progress
                val remaining = (estimatedTotal - elapsed).toLong().coerceAtLeast(0)
                val remainingSec = remaining / 1000
                val minutes = remainingSec / 60
                val seconds = remainingSec % 60
                Text(
                    "~${minutes}m ${seconds}s remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ExportCompleteSection(
    outputPath: String,
    fileSizeBytes: Long,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Export Complete!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                outputPath.substringAfterLast("/"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val sizeMb = fileSizeBytes / (1024f * 1024f)
            if (fileSizeBytes > 0) {
                Text(
                    "${"%.1f".format(sizeMb)} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Stub export — add FFmpeg-kit for real encoding",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Share button
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }

            // Save to gallery
            OutlinedButton(
                onClick = onSaveToGallery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save to Gallery")
            }

            // Done
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

private fun shareVideoFile(context: android.content.Context, filePath: String) {
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(shareIntent, "Share video")
        )
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Could not share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ExportErrorSection(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Export Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}
