package com.gpxvideo.feature.project

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.ui.component.LoadingIndicator

/**
 * Screen 1: "The Cut" — Video Assembly
 * Simplified single-track editor with clip strip, aspect ratio, and "+ Add Activity" bridge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAssemblyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStyle: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectIdStr = uiState.project?.id?.toString() ?: ""

    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

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
            // Auto-navigate to Style screen after GPX import
            if (projectIdStr.isNotBlank()) {
                onNavigateToStyle(projectIdStr)
            }
        }
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
                onInfoClick = { showInfoSheet = true },
                hasGpxData = uiState.gpxData != null,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Video preview area
                VideoPreviewArea(
                    mediaItems = uiState.mediaItems,
                    aspectRatio = uiState.selectedAspectRatio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Clip strip + controls
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

                // "+ Add Activity" primary CTA
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

    // Info Bottom Sheet
    if (showInfoSheet) {
        ActivityInfoBottomSheet(
            gpxStats = uiState.gpxStats,
            gpxData = uiState.gpxData,
            mediaItems = uiState.mediaItems,
            sportType = uiState.project?.sportType ?: "CYCLING",
            onDismiss = { showInfoSheet = false }
        )
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────

@Composable
private fun AssemblyTopBar(
    title: String,
    selectedRatio: SocialAspectRatio,
    showAspectRatioMenu: Boolean,
    onToggleAspectRatioMenu: () -> Unit,
    onAspectRatioSelected: (SocialAspectRatio) -> Unit,
    onInfoClick: () -> Unit,
    hasGpxData: Boolean,
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

            // Info button
            if (hasGpxData) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Activity Info",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ── Video Preview ────────────────────────────────────────────────────────

@Composable
private fun VideoPreviewArea(
    mediaItems: List<MediaItemEntity>,
    aspectRatio: SocialAspectRatio,
    modifier: Modifier = Modifier
) {
    val ratio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
    val firstVideoPath = mediaItems
        .firstOrNull { it.type == "VIDEO" }
        ?.let { it.localCopyPath.ifBlank { it.sourcePath } }
        ?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (mediaItems.isEmpty()) {
            // Empty state — invite to add clips
            EmptyPreviewState()
        } else {
            // Video canvas with correct aspect ratio
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A2E))
            ) {
                if (firstVideoPath != null) {
                    AssemblyVideoThumbnail(
                        videoPath = firstVideoPath,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF1A1A2E),
                                        Color(0xFF16213E),
                                        Color(0xFF0F3460)
                                    )
                                )
                            )
                    )
                }

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

// ── Clip Strip ───────────────────────────────────────────────────────────

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

// ── Add Activity Button ──────────────────────────────────────────────────

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
        // GPX already loaded — show "Continue to Style" button
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
        // Primary CTA — "+ Add Activity"
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

// ── Info Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityInfoBottomSheet(
    gpxStats: com.gpxvideo.lib.gpxparser.GpxStats?,
    gpxData: com.gpxvideo.core.model.GpxData?,
    mediaItems: List<MediaItemEntity>,
    sportType: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            // Sport icon + title
            val sportIcon = when (sportType.uppercase()) {
                "RUNNING" -> Icons.Default.DirectionsRun
                "HIKING" -> Icons.Default.Hiking
                else -> Icons.Default.DirectionsBike
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF448AFF).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        sportIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF448AFF)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Activity Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        sportType.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            if (gpxStats != null) {
                // Stats grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoStatCard(
                        label = "Distance",
                        value = "%.1f".format(gpxStats.totalDistance / 1000.0),
                        unit = "km",
                        modifier = Modifier.weight(1f)
                    )
                    InfoStatCard(
                        label = "Elevation",
                        value = "%.0f".format(gpxStats.totalElevationGain),
                        unit = "m",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoStatCard(
                        label = "Duration",
                        value = formatDurationMs(gpxStats.movingDuration.toMillis()),
                        unit = "",
                        modifier = Modifier.weight(1f)
                    )
                    InfoStatCard(
                        label = "Avg Speed",
                        value = if (gpxStats.avgSpeed > 0) "%.1f".format(gpxStats.avgSpeed * 3.6) else "—",
                        unit = if (gpxStats.avgSpeed > 0) "km/h" else "",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoStatCard(
                        label = "Clips",
                        value = "${mediaItems.size}",
                        unit = "video${if (mediaItems.size != 1) "s" else ""}",
                        modifier = Modifier.weight(1f)
                    )
                    InfoStatCard(
                        label = "Clip Duration",
                        value = formatDurationMs(mediaItems.mapNotNull { it.durationMs }.sum()),
                        unit = "",
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    "No activity data loaded yet.\nImport a GPX file to see stats.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoStatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

// ── Video Thumbnail ──────────────────────────────────────────────────────

@Composable
private fun AssemblyVideoThumbnail(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnail = remember(videoPath) {
        try {
            val retriever = MediaMetadataRetriever()
            if (videoPath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(videoPath))
            } else {
                retriever.setDataSource(videoPath)
            }
            val frame = retriever.getFrameAtTime(1_000_000)
            retriever.release()
            frame
        } catch (_: Exception) { null }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

// ── Utilities ────────────────────────────────────────────────────────────

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
