package com.gpxvideo.feature.project

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.preview.PreviewEngine
import com.gpxvideo.feature.preview.VideoPreview

/**
 * Screen 1: "The Cut" — Video Assembly
 * Single-track editor with live video preview, clip strip, aspect ratio, and "+ Add Activity" bridge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAssemblyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStyle: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()
    val projectIdStr = uiState.project?.id?.toString() ?: ""

    var showAspectRatioMenu by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importGpxFile(it)
            if (projectIdStr.isNotBlank()) {
                onNavigateToStyle(projectIdStr)
            }
        }
    }

    // Pause preview when leaving the screen
    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    if (uiState.isLoading) {
        LoadingIndicator()
        return
    }

    Scaffold(
        containerColor = Color(0xFF0D0D12),
        topBar = {
            AssemblyTopBar(
                title = uiState.project?.name ?: "New Story",
                selectedRatio = uiState.selectedAspectRatio,
                showAspectRatioMenu = showAspectRatioMenu,
                onToggleAspectRatioMenu = { showAspectRatioMenu = !showAspectRatioMenu },
                onAspectRatioSelected = {
                    viewModel.setAspectRatio(it)
                    showAspectRatioMenu = false
                },
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live video preview area
            VideoPreviewArea(
                previewEngine = viewModel.previewEngine,
                mediaItems = uiState.mediaItems,
                aspectRatio = uiState.selectedAspectRatio,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                videoDuration = videoDuration,
                onTogglePlayback = viewModel::togglePlayback,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Clip strip
            ClipStripSection(
                mediaItems = uiState.mediaItems,
                isImporting = uiState.isImporting,
                onAddClips = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onDeleteMedia = viewModel::deleteMedia,
                onReorder = viewModel::reorderMedia
            )

            Spacer(Modifier.height(8.dp))

            // Bottom action buttons
            AddActivityButton(
                hasGpxData = uiState.gpxData != null,
                hasMedia = uiState.mediaItems.isNotEmpty(),
                isImportingGpx = uiState.isImportingGpx,
                onAddActivity = {
                    gpxPickerLauncher.launch(arrayOf("*/*"))
                },
                onGoToStyle = {
                    if (projectIdStr.isNotBlank()) onNavigateToStyle(projectIdStr)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssemblyTopBar(
    title: String,
    selectedRatio: SocialAspectRatio,
    showAspectRatioMenu: Boolean,
    onToggleAspectRatioMenu: () -> Unit,
    onAspectRatioSelected: (SocialAspectRatio) -> Unit,
    onNavigateBack: () -> Unit
) {
    Surface(
        color = Color(0xFF0D0D12),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Aspect Ratio dropdown
            Box {
                Surface(
                    onClick = onToggleAspectRatioMenu,
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            selectedRatio.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "▾",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAspectRatioMenu,
                    onDismissRequest = { onAspectRatioSelected(selectedRatio) }
                ) {
                    SocialAspectRatio.entries.forEach { ratio ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(ratio.icon, fontSize = 16.sp)
                                    Column {
                                        Text(
                                            ratio.displayName,
                                            fontWeight = if (ratio == selectedRatio) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            ratio.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = { onAspectRatioSelected(ratio) }
                        )
                    }
                }
            }
        }
    }
}

// ── Video Preview ────────────────────────────────────────────────────────────

@Composable
private fun VideoPreviewArea(
    previewEngine: PreviewEngine,
    mediaItems: List<MediaItemEntity>,
    aspectRatio: SocialAspectRatio,
    isPlaying: Boolean,
    currentPositionMs: Long,
    videoDuration: Long,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canvasRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (mediaItems.isEmpty()) {
            EmptyPreviewState()
        } else {
            // Canvas box with the selected aspect ratio and black background for letterbox/pillarbox
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .aspectRatio(canvasRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                // Live video preview — fills the canvas; VideoPreview handles fit/letterbox internally
                VideoPreview(
                    previewEngine = previewEngine,
                    modifier = Modifier.fillMaxSize()
                )

                // Play/pause overlay
                PlayPauseOverlay(
                    isPlaying = isPlaying,
                    onToggle = onTogglePlayback,
                    modifier = Modifier.fillMaxSize()
                )

                // Thin progress bar at the bottom
                PlaybackProgressBar(
                    currentPositionMs = currentPositionMs,
                    durationMs = videoDuration,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )

                // Subtle border frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun PlayPauseOverlay(
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Tap the entire preview area to toggle playback
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        ),
        contentAlignment = Alignment.Center
    ) {
        // Show the button with a fade — visible when paused, fades out shortly after play resumes
        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlaybackProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 150),
        label = "progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier.height(3.dp),
        color = Color(0xFF448AFF),
        trackColor = Color.White.copy(alpha = 0.15f)
    )
}

@Composable
private fun EmptyPreviewState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(48.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Add your clips",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold
        )
        Text(
            "Import video clips from your activity to start assembling your story",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

// ── Clip Strip ───────────────────────────────────────────────────────────────

@Composable
private fun ClipStripSection(
    mediaItems: List<MediaItemEntity>,
    isImporting: Boolean,
    onAddClips: () -> Unit,
    onDeleteMedia: (java.util.UUID) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF0D0D12))
                )
            )
            .padding(top = 8.dp)
    ) {
        // Section label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "CLIPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                letterSpacing = 2.sp
            )
            if (mediaItems.isNotEmpty()) {
                Text(
                    "${mediaItems.size} clip${if (mediaItems.size != 1) "s" else ""} · ${
                        formatDurationMs(mediaItems.mapNotNull { it.durationMs }.sum())
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Clip strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(mediaItems, key = { _, item -> item.id }) { _, media ->
                ClipThumbnailCard(
                    media = media,
                    onDelete = { onDeleteMedia(media.id) }
                )
            }

            // Add button
            item {
                AddClipButton(
                    isImporting = isImporting,
                    onClick = onAddClips
                )
            }
        }
    }
}

@Composable
private fun ClipThumbnailCard(
    media: MediaItemEntity,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val videoPath = media.localCopyPath.ifBlank { media.sourcePath }
    val thumbnail = remember(media.id) {
        try {
            val retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }
            val frame = retriever.getFrameAtTime(500_000) // 0.5s
            retriever.release()
            frame
        } catch (_: Exception) { null }
    }

    Card(
        modifier = Modifier
            .width(72.dp)
            .height(72.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C28)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C28)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (media.type == "VIDEO") Icons.Default.Movie else Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            // Duration badge
            if (media.durationMs != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        formatDurationMs(media.durationMs!!),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(10.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun AddClipButton(isImporting: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(72.dp)
            .height(72.dp)
            .clickable(enabled = !isImporting, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add clips",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ── Add Activity Button ─────────────────────────────────────────────────────

@Composable
private fun AddActivityButton(
    hasGpxData: Boolean,
    hasMedia: Boolean,
    isImportingGpx: Boolean,
    onAddActivity: () -> Unit,
    onGoToStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasGpxData && hasMedia) {
        Button(
            onClick = onGoToStyle,
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF448AFF)
            )
        ) {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Continue to Overlays",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    } else {
        Button(
            onClick = onAddActivity,
            modifier = modifier.height(54.dp),
            enabled = hasMedia && !isImportingGpx,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hasMedia) Color(0xFF00C853) else Color(0xFF00C853).copy(alpha = 0.3f),
                disabledContainerColor = Color(0xFF00C853).copy(alpha = 0.3f)
            )
        ) {
            if (isImportingGpx) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text("Importing activity…", fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasMedia) "+ Add Activity" else "Add clips first",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ── Utilities ────────────────────────────────────────────────────────────────

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
