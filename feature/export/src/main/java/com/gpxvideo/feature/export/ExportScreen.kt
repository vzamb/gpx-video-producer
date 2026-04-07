package com.gpxvideo.feature.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val DarkBg = Color(0xFF0D0D12)
private val AccentBlue = Color(0xFF448AFF)
private val SurfaceDark = Color(0xFF1A1A2E)

@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onExportComplete: (String) -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Auto-start export when screen opens
    LaunchedEffect(Unit) {
        if (uiState.exportState is ExportState.Idle) {
            viewModel.startExport()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .padding(top = 48.dp, start = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, start = 24.dp, end = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val state = uiState.exportState) {
                is ExportState.Idle -> {
                    // Brief loading state before auto-start kicks in
                    Text(
                        "Preparing export…",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                is ExportState.Exporting -> {
                    ExportProgressSection(
                        phase = state.phase,
                        progress = state.progress,
                        startTimeMs = state.startTimeMs,
                        onCancel = {
                            viewModel.cancelExport()
                            onNavigateBack()
                        }
                    )
                }

                is ExportState.Complete -> {
                    ExportCompleteSection(
                        outputPath = state.outputPath,
                        fileSizeBytes = state.fileSizeBytes,
                        onShare = { shareVideoFile(context, state.outputPath) },
                        onSaveToGallery = { viewModel.saveToGallery(state.outputPath) },
                        onDone = onNavigateBack
                    )
                }

                is ExportState.Error -> {
                    ExportErrorSection(
                        message = state.message,
                        onRetry = viewModel::startExport,
                        onDismiss = onNavigateBack
                    )
                }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Exporting your video",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            phase.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = AccentBlue
        )

        // Large percentage
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Progress bar
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AccentBlue,
            trackColor = SurfaceDark
        )

        // Estimated remaining time
        val elapsed = System.currentTimeMillis() - startTimeMs
        if (progress > 0.01f && elapsed > 1000) {
            val estimatedTotal = elapsed / progress
            val remaining = (estimatedTotal - elapsed).toLong().coerceAtLeast(0)
            val remainingSec = remaining / 1000
            val minutes = remainingSec / 60
            val seconds = remainingSec % 60
            Text(
                "About ${minutes}m ${seconds}s remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.2f)))
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cancel")
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AccentBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    modifier = Modifier.size(48.dp),
                    tint = AccentBlue
                )
            }
        }

        Text(
            "Export Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            outputPath.substringAfterLast("/"),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White.copy(alpha = 0.6f)
        )

        if (fileSizeBytes > 0) {
            val sizeMb = fileSizeBytes / (1024f * 1024f)
            Text(
                "${"%.1f".format(sizeMb)} MB",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.4f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share", modifier = Modifier.padding(vertical = 4.dp))
        }

        OutlinedButton(
            onClick = onSaveToGallery,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Save to Gallery")
        }

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done")
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Export Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEF5350)
        )

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Retry")
            }
        }
    }
}
