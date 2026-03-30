package com.gpxvideo.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.preview.PreviewViewModel
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

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
            onAddEffect = { /* TODO: open effects sheet */ },
            onAddOverlay = { /* TODO: open overlay catalog sheet */ },
            onAddGpx = { gpxPickerLauncher.launch(arrayOf("*/*")) },
            onAddText = { /* TODO: open text editor sheet */ }
        )
    }
}

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
