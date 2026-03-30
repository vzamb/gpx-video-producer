package com.gpxvideo.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.overlays.OverlayCatalog
import com.gpxvideo.feature.overlays.OverlayCatalogItem
import com.gpxvideo.feature.overlays.OverlayCategory
import com.gpxvideo.feature.preview.PreviewViewModel
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectIdStr = uiState.project?.id?.toString() ?: ""

    if (uiState.isLoading) {
        LoadingIndicator()
        return
    }

    val projectId = uiState.project?.id ?: return

    val previewVm = hiltViewModel<PreviewViewModel, PreviewViewModel.Factory>(
        key = "preview-$projectId",
        creationCallback = { factory -> factory.create(projectId.toString()) }
    )
    val timelineVm = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
        key = "timeline-$projectId",
        creationCallback = { factory -> factory.create(projectId.toString()) }
    )

    val timelineState by timelineVm.state.collectAsStateWithLifecycle()
    val playerPosition by previewVm.currentPositionMs.collectAsStateWithLifecycle()
    val playerIsPlaying by previewVm.isPlaying.collectAsStateWithLifecycle()
    val playerDuration by previewVm.duration.collectAsStateWithLifecycle()

    // Reload preview when timeline content changes
    LaunchedEffect(timelineVm) {
        timelineVm.timelineChanged.collectLatest {
            previewVm.reloadMedia()
        }
    }

    // Sync player position → timeline playhead while playing
    LaunchedEffect(Unit) {
        snapshotFlow { playerPosition to playerIsPlaying }
            .distinctUntilChanged()
            .collect { (position, playing) ->
                if (playing) {
                    timelineVm.setPlayheadPosition(position)
                }
            }
    }

    // Media picker
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMedia(uris)
        }
    }

    // GPX picker
    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importGpxFile(it) }
    }

    // Bottom sheet states
    var showOverlayCatalog by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var showEffectsPanel by remember { mutableStateOf(false) }

    // Dispatch timeline actions to both ViewModels
    val handleTimelineAction: (TimelineEditorAction) -> Unit = { action ->
        when (action) {
            is TimelineEditorAction.PlayheadMoved -> {
                timelineVm.setPlayheadPosition(action.positionMs)
                previewVm.seekTo(action.positionMs)
            }
            TimelineEditorAction.PlayPauseToggled -> previewVm.togglePlayback()
            is TimelineEditorAction.ClipSelected -> timelineVm.selectClip(action.clipId)
            is TimelineEditorAction.ClipMoved ->
                timelineVm.moveClip(action.clipId, action.newStartMs)
            is TimelineEditorAction.ClipTrimmed ->
                timelineVm.trimClip(action.clipId, action.newStartMs, action.newEndMs)
            is TimelineEditorAction.ZoomChanged -> timelineVm.setZoomLevel(action.level)
            TimelineEditorAction.UndoRequested -> timelineVm.undo()
            TimelineEditorAction.RedoRequested -> timelineVm.redo()
            is TimelineEditorAction.TrackAdded -> timelineVm.addTrack(action.type)
            is TimelineEditorAction.TrackDeleted -> timelineVm.deleteTrack(action.trackId)
            is TimelineEditorAction.TrackVisibilityToggled ->
                timelineVm.toggleTrackVisibility(action.trackId)
            is TimelineEditorAction.TrackLockToggled ->
                timelineVm.toggleTrackLock(action.trackId)
            is TimelineEditorAction.ClipDeleted -> timelineVm.deleteClip(action.clipId)
            is TimelineEditorAction.ClipDuplicated -> timelineVm.duplicateClip(action.clipId)
            is TimelineEditorAction.ClipSplit -> timelineVm.splitClipAtPlayhead(action.clipId)
            is TimelineEditorAction.SpeedChanged ->
                timelineVm.setClipSpeed(action.clipId, action.speed)
            is TimelineEditorAction.VolumeChanged ->
                timelineVm.setClipVolume(action.clipId, action.volume)
            is TimelineEditorAction.EntryTransitionChanged ->
                timelineVm.setClipEntryTransition(
                    action.clipId,
                    action.transition?.type?.name,
                    action.transition?.durationMs
                )
            is TimelineEditorAction.ExitTransitionChanged ->
                timelineVm.setClipExitTransition(
                    action.clipId,
                    action.transition?.type?.name,
                    action.transition?.durationMs
                )
            is TimelineEditorAction.KenBurnsChanged ->
                timelineVm.setClipKenBurns(
                    action.clipId,
                    action.config.startX, action.config.startY, action.config.startScale,
                    action.config.endX, action.config.endY, action.config.endScale
                )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
    ) {
        // 1. Top bar
        EditorTopBar(
            canUndo = timelineState.canUndo,
            canRedo = timelineState.canRedo,
            onBack = onNavigateBack,
            onUndo = { handleTimelineAction(TimelineEditorAction.UndoRequested) },
            onRedo = { handleTimelineAction(TimelineEditorAction.RedoRequested) },
            onExport = { onNavigateToExport(projectIdStr) }
        )

        // 2. Video preview
        VideoPreview(
            previewEngine = previewVm.previewEngine,
            modifier = Modifier.weight(0.35f)
        )

        // 3. Compact playback bar
        CompactPlaybackBar(
            currentPositionMs = if (playerIsPlaying) playerPosition
                else timelineState.playheadPositionMs,
            totalDurationMs = if (playerDuration > 0) playerDuration
                else timelineState.totalDurationMs,
            isPlaying = playerIsPlaying,
            onPlayPause = { previewVm.togglePlayback() },
            onToggleFullscreen = { /* TODO: fullscreen preview */ }
        )

        // 3.5 Clip action bar (when a clip is selected)
        ClipActionBar(
            timelineState = timelineState,
            onAction = handleTimelineAction,
            onShowEffects = { showEffectsPanel = true }
        )

        // 4. Timeline area
        EditorTimeline(
            timelineState = timelineState.copy(
                isPlaying = playerIsPlaying,
                playheadPositionMs = if (playerIsPlaying) playerPosition
                    else timelineState.playheadPositionMs
            ),
            playerPositionMs = if (playerIsPlaying) playerPosition
                else timelineState.playheadPositionMs,
            onSeekTo = { ms ->
                timelineVm.setPlayheadPosition(ms)
                previewVm.seekTo(ms)
            },
            onAction = handleTimelineAction,
            onAddMediaClick = {
                pickMedia.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            },
            modifier = Modifier.weight(0.45f)
        )

        // 5. Bottom action bar
        EditorBottomBar(
            onAddMedia = {
                pickMedia.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            },
            onAddEffect = { showEffectsPanel = true },
            onAddOverlay = { showOverlayCatalog = true },
            onAddGpx = { gpxPickerLauncher.launch(arrayOf("*/*")) },
            onAddText = { showTextInput = true }
        )
    }

    // --- Bottom Sheets ---

    if (showOverlayCatalog) {
        OverlayCatalogSheet(
            onSelect = { item ->
                timelineVm.addOverlayToTimeline(item.type, item.displayName)
                showOverlayCatalog = false
            },
            onDismiss = { showOverlayCatalog = false }
        )
    }

    if (showTextInput) {
        TextInputSheet(
            onConfirm = { text ->
                timelineVm.addTextToTimeline(text)
                showTextInput = false
            },
            onDismiss = { showTextInput = false }
        )
    }

    if (showEffectsPanel) {
        EffectsSheet(
            timelineState = timelineState,
            onSpeedChange = { clipId, speed ->
                timelineVm.setClipSpeed(clipId, speed)
            },
            onVolumeChange = { clipId, volume ->
                timelineVm.setClipVolume(clipId, volume)
            },
            onDismiss = { showEffectsPanel = false }
        )
    }
}

// --- Top Bar ---

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledTonalButton(
            onClick = onExport,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Export", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// --- Clip Action Bar (shown when a clip is selected) ---

@Composable
private fun ClipActionBar(
    timelineState: TimelineState,
    onAction: (TimelineEditorAction) -> Unit,
    onShowEffects: () -> Unit
) {
    val selectedClip = timelineState.selectedClipId ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onAction(TimelineEditorAction.ClipSplit(selectedClip)) }) {
            Icon(Icons.Default.ContentCut, "Split", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipDuplicated(selectedClip)) }) {
            Icon(Icons.Default.ContentCopy, "Duplicate", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onShowEffects) {
            Icon(Icons.Default.Upload, "Effects", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipDeleted(selectedClip)) }) {
            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipSelected(null)) }) {
            Icon(Icons.Default.Close, "Deselect", modifier = Modifier.size(20.dp))
        }
    }
}

// --- Overlay Catalog Bottom Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayCatalogSheet(
    onSelect: (OverlayCatalogItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = "Add Overlay",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        OverlayCategory.entries.forEach { category ->
            val items = OverlayCatalog.items.filter { it.category == category }
            if (items.isNotEmpty()) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                items.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.displayName) },
                        supportingContent = { Text(item.description) },
                        leadingContent = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable { onSelect(item) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// --- Text Input Bottom Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextInputSheet(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Add Text Overlay",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text("Text content") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { if (textValue.isNotBlank()) onConfirm(textValue) },
                    enabled = textValue.isNotBlank()
                ) { Text("Add") }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// --- Effects Bottom Sheet ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsSheet(
    timelineState: TimelineState,
    onSpeedChange: (java.util.UUID, Float) -> Unit,
    onVolumeChange: (java.util.UUID, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedClip = timelineState.selectedClipId?.let { clipId ->
        timelineState.tracks.flatMap { it.clips }.find { it.id == clipId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Clip Effects",
                style = MaterialTheme.typography.titleMedium
            )

            if (selectedClip == null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Select a clip in the timeline first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Speed control
                Text("Speed: ${"%.1fx".format(selectedClip.speed)}", style = MaterialTheme.typography.labelLarge)
                var speedValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.speed) }
                Slider(
                    value = speedValue,
                    onValueChange = { speedValue = it },
                    onValueChangeFinished = { onSpeedChange(selectedClip.id, speedValue) },
                    valueRange = 0.25f..4f,
                    steps = 14
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Volume control
                Text("Volume: ${(selectedClip.volume * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                var volumeValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.volume) }
                Slider(
                    value = volumeValue,
                    onValueChange = { volumeValue = it },
                    onValueChangeFinished = { onVolumeChange(selectedClip.id, volumeValue) },
                    valueRange = 0f..2f,
                    steps = 7
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Clip info
                val durationSec = (selectedClip.endTimeMs - selectedClip.startTimeMs) / 1000f
                Text(
                    text = "Duration: ${"%.1f".format(durationSec)}s | Start: ${selectedClip.startTimeMs / 1000}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
