package com.gpxvideo.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.overlays.OverlayConfigPanel
import com.gpxvideo.feature.overlays.OverlaysViewModel
import com.gpxvideo.feature.overlays.SyncConfigSheet
import com.gpxvideo.feature.preview.PreviewViewModel
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.feature.timeline.ClipContentMode
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineEditorAction
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineTrackState
import com.gpxvideo.feature.timeline.TimelineViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.UUID

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
    val overlaysVm: OverlaysViewModel = hiltViewModel()

    val timelineState by timelineVm.state.collectAsStateWithLifecycle()
    val overlays by overlaysVm.overlays.collectAsStateWithLifecycle()
    val syncEngine by overlaysVm.syncEngine.collectAsStateWithLifecycle()
    val syncMode by overlaysVm.syncMode.collectAsStateWithLifecycle()
    val timeOffsetMs by overlaysVm.timeOffsetMs.collectAsStateWithLifecycle()
    val keyframes by overlaysVm.keyframes.collectAsStateWithLifecycle()
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

    val activePreviewPosition = if (playerIsPlaying) playerPosition else timelineState.playheadPositionMs

    // Reload preview when tracks change OR when the project canvas resolution changes
    val canvasWidth = uiState.project?.resolutionWidth ?: 0
    val canvasHeight = uiState.project?.resolutionHeight ?: 0
    LaunchedEffect(timelineState.tracks, uiState.mediaItems, canvasWidth, canvasHeight) {
        previewVm.reloadMedia(
            tracks = timelineState.tracks,
            mediaItems = uiState.mediaItems,
            targetPositionMs = activePreviewPosition
        )
    }

    LaunchedEffect(activePreviewPosition) {
        overlaysVm.updateVideoPosition(activePreviewPosition)
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
    var showOverlaySettings by remember { mutableStateOf(false) }
    var showSyncConfig by remember { mutableStateOf(false) }
    var showGpxWorkspace by remember { mutableStateOf(false) }

    // Aspect ratio dropdown is anchored to the top bar button
    var showAspectRatioDropdown by remember { mutableStateOf(false) }
    val selectedTool = when {
        showOverlayCatalog -> EditorTool.GPX_OVERLAYS
        showGpxWorkspace -> EditorTool.GPX_FILE
        showTextInput -> EditorTool.TEXT
        showEffectsPanel -> EditorTool.EFFECTS
        else -> null
    }

    fun closeToolPanels() {
        showOverlayCatalog = false
        showTextInput = false
        showEffectsPanel = false
        showGpxWorkspace = false
        showAspectRatioDropdown = false
    }

    val selectedOverlay = remember(overlays, timelineState.selectedClipId) {
        overlays.firstOrNull { it.timelineClipId == timelineState.selectedClipId }
    }
    val selectedOverlayTrack = remember(timelineState.tracks, selectedOverlay?.timelineClipId) {
        selectedOverlay?.timelineClipId?.let { clipId ->
            timelineState.tracks.firstOrNull { track -> track.clips.any { it.id == clipId } }
        }
    }
    val selectedGpxData = uiState.gpxData
    val showVideoSurface = playerDuration > 0L || playerIsPlaying || timelineState.tracks.any { track ->
        (track.type == com.gpxvideo.core.model.TrackType.VIDEO ||
            track.type == com.gpxvideo.core.model.TrackType.IMAGE) && track.clips.isNotEmpty()
    }
    val previewAspectRatio = remember(uiState.project?.resolutionWidth, uiState.project?.resolutionHeight) {
        val width = uiState.project?.resolutionWidth?.coerceAtLeast(1) ?: 1920
        val height = uiState.project?.resolutionHeight?.coerceAtLeast(1) ?: 1080
        width.toFloat() / height.toFloat()
    }
    val aspectRatioLabel = remember(uiState.project?.resolutionWidth, uiState.project?.resolutionHeight) {
        formatAspectRatioLabel(
            uiState.project?.resolutionWidth ?: 1920,
            uiState.project?.resolutionHeight ?: 1080
        )
    }

    LaunchedEffect(selectedOverlay?.id) {
        if (selectedOverlay == null) {
            showOverlaySettings = false
            showSyncConfig = false
        }
    }

    // Dispatch timeline actions to both ViewModels
    val handleTimelineAction: (TimelineEditorAction) -> Unit = { action ->
        when (action) {
            is TimelineEditorAction.PlayheadMoved -> {
                timelineVm.setPlayheadPosition(action.positionMs)
                previewVm.seekTo(action.positionMs)
            }
            TimelineEditorAction.PlayPauseToggled -> previewVm.togglePlayback()
            is TimelineEditorAction.ClipSelected -> {
                timelineVm.selectClip(action.clipId)
                if (action.clipId == null) {
                    showOverlaySettings = false
                    showSyncConfig = false
                }
            }
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
            .statusBarsPadding()
    ) {
        // 1. Top bar
        EditorTopBar(
            canUndo = timelineState.canUndo,
            canRedo = timelineState.canRedo,
            aspectRatioLabel = aspectRatioLabel,
            showAspectRatioDropdown = showAspectRatioDropdown,
            currentResolutionWidth = uiState.project?.resolutionWidth ?: 1920,
            currentResolutionHeight = uiState.project?.resolutionHeight ?: 1080,
            onBack = onNavigateBack,
            onUndo = { handleTimelineAction(TimelineEditorAction.UndoRequested) },
            onRedo = { handleTimelineAction(TimelineEditorAction.RedoRequested) },
            onAspectRatioClick = { showAspectRatioDropdown = true },
            onAspectRatioSelected = { preset ->
                viewModel.updateCanvasResolution(preset.width, preset.height)
            },
            onAspectRatioDropdownDismiss = { showAspectRatioDropdown = false },
            onExport = { onNavigateToExport(projectIdStr) }
        )

        // 2. Video preview — clean, no wrapper box
        BoxWithConstraints(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val availableAspectRatio = constraints.maxWidth.toFloat() /
                constraints.maxHeight.coerceAtLeast(1).toFloat()
            val previewFrameModifier = if (previewAspectRatio >= availableAspectRatio) {
                Modifier.fillMaxWidth().aspectRatio(previewAspectRatio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(previewAspectRatio)
            }

            Box(
                modifier = previewFrameModifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color.Black)
            ) {
                if (showVideoSurface) {
                    VideoPreview(
                        previewEngine = previewVm.previewEngine,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TimelineFramePreview(
                        timelineState = timelineState,
                        mediaItems = uiState.mediaItems,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                EditorOverlayCanvas(
                    timelineState = timelineState,
                    overlays = overlays,
                    gpxData = uiState.gpxData,
                    gpxStats = uiState.gpxStats,
                    syncEngine = syncEngine,
                    currentPositionMs = activePreviewPosition,
                    onSelectClip = { clipId -> timelineVm.selectClip(clipId) },
                    onUpdateOverlay = { overlay ->
                        overlaysVm.updateOverlay(overlay)
                        timelineVm.updateClipLabel(overlay.timelineClipId, overlay.name)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Compact playback bar
        CompactPlaybackBar(
            currentPositionMs = activePreviewPosition,
            totalDurationMs = if (playerDuration > 0) playerDuration
                else timelineState.totalDurationMs,
            isPlaying = playerIsPlaying,
            onSeekTo = { ms ->
                timelineVm.setPlayheadPosition(ms)
                previewVm.seekTo(ms)
            },
            onPlayPause = { previewVm.togglePlayback() }
        )

        // 3.5 Clip action bar (when a clip is selected)
        ClipActionBar(
            timelineState = timelineState,
            onAction = handleTimelineAction,
            onShowEffects = {
                if (selectedOverlay != null) {
                    showOverlaySettings = true
                } else {
                    showEffectsPanel = true
                }
            }
        )

        // 4. Timeline area
        EditorTimeline(
            timelineState = timelineState.copy(
                isPlaying = playerIsPlaying,
                playheadPositionMs = activePreviewPosition
            ),
            mediaItems = uiState.mediaItems,
            playerPositionMs = activePreviewPosition,
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
            modifier = Modifier
                .weight(0.22f)
                .padding(horizontal = 4.dp)
        )

        // 5. Bottom action bar
        EditorBottomBar(
            selectedTool = selectedTool,
            onToolSelected = { tool ->
                when (tool) {
                    EditorTool.MEDIA -> {
                        closeToolPanels()
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                    EditorTool.GPX_OVERLAYS -> {
                        closeToolPanels()
                        showOverlayCatalog = true
                    }
                    EditorTool.GPX_FILE -> {
                        closeToolPanels()
                        showGpxWorkspace = true
                    }
                    EditorTool.TEXT -> {
                        closeToolPanels()
                        showTextInput = true
                    }
                    EditorTool.EFFECTS -> {
                        closeToolPanels()
                        showEffectsPanel = true
                    }
                }
            }
        )
    }

    // --- Bottom Sheets ---

    if (showOverlayCatalog) {
        GpxOverlayDrawer(
            currentFileName = uiState.gpxFiles.firstOrNull()?.name,
            hasGpx = uiState.gpxData != null,
            gpxData = uiState.gpxData,
            gpxStats = uiState.gpxStats,
            onImportGpx = { gpxPickerLauncher.launch(arrayOf("*/*")) },
            onAddOverlay = { overlayType, displayName, stylePreset, formatPreset ->
                timelineVm.addOverlayToTimeline(
                    overlayType = overlayType,
                    displayName = displayName,
                    stylePreset = stylePreset,
                    formatPreset = formatPreset
                )
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
        var frameBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(200)
            frameBitmap = previewVm.captureCurrentFrame()
        }
        val targetClip = timelineState.selectedClipId?.let { clipId ->
            timelineState.tracks.flatMap { it.clips }.find { it.id == clipId }
        } ?: timelineState.tracks.flatMap { it.clips }.firstOrNull()

        EffectsSheet(
            frameBitmap = frameBitmap,
            targetClip = targetClip,
            onColorAdjustmentsChange = timelineVm::updateClipColorAdjustments,
            onDismiss = { showEffectsPanel = false }
        )
    }

    // Aspect ratio is now handled by the dropdown in the top bar (no separate sheet)

    if (showGpxWorkspace) {
        GpxWorkspaceSheet(
            currentFile = uiState.gpxFiles.firstOrNull(),
            gpxData = uiState.gpxData,
            gpxStats = uiState.gpxStats,
            isImporting = uiState.isImportingGpx,
            onImportOrReplace = { gpxPickerLauncher.launch(arrayOf("*/*")) },
            onRename = viewModel::renameGpxFile,
            onDelete = {
                uiState.gpxFiles.firstOrNull()?.id?.let(viewModel::deleteGpxFile)
            },
            onDismiss = { showGpxWorkspace = false }
        )
    }

    if (showOverlaySettings && selectedOverlay != null) {
        OverlaySettingsSheet(
            overlay = selectedOverlay,
            onUpdate = { overlay ->
                overlaysVm.updateOverlay(overlay)
                timelineVm.updateClipLabel(overlay.timelineClipId, overlay.name)
            },
            onDelete = {
                deleteOverlayTrackOrClip(
                    timelineVm = timelineVm,
                    overlay = selectedOverlay,
                    track = selectedOverlayTrack
                )
                showOverlaySettings = false
            },
            onOpenSyncConfig = { showSyncConfig = true },
            onDismiss = {
                showOverlaySettings = false
                timelineVm.selectClip(null)
            }
        )
    }

    if (showSyncConfig && selectedOverlay is OverlayConfig.DynamicAltitudeProfile && selectedGpxData != null) {
        SyncConfigSheet(
            syncMode = syncMode,
            timeOffsetMs = timeOffsetMs,
            keyframes = keyframes,
            gpxData = selectedGpxData,
            onSyncModeChanged = { mode ->
                overlaysVm.updateSyncMode(mode)
                overlaysVm.updateOverlay(selectedOverlay.copy(syncMode = mode))
            },
            onTimeOffsetChanged = overlaysVm::updateTimeOffset,
            onKeyframeAdded = overlaysVm::addKeyframe,
            onDismiss = { showSyncConfig = false }
        )
    }

    if (showSyncConfig && selectedOverlay is OverlayConfig.DynamicMap && selectedGpxData != null) {
        SyncConfigSheet(
            syncMode = syncMode,
            timeOffsetMs = timeOffsetMs,
            keyframes = keyframes,
            gpxData = selectedGpxData,
            onSyncModeChanged = { mode ->
                overlaysVm.updateSyncMode(mode)
                overlaysVm.updateOverlay(selectedOverlay.copy(syncMode = mode))
            },
            onTimeOffsetChanged = overlaysVm::updateTimeOffset,
            onKeyframeAdded = overlaysVm::addKeyframe,
            onDismiss = { showSyncConfig = false }
        )
    }

    if (showSyncConfig && selectedOverlay is OverlayConfig.DynamicStat && selectedGpxData != null) {
        SyncConfigSheet(
            syncMode = syncMode,
            timeOffsetMs = timeOffsetMs,
            keyframes = keyframes,
            gpxData = selectedGpxData,
            onSyncModeChanged = { mode ->
                overlaysVm.updateSyncMode(mode)
                overlaysVm.updateOverlay(selectedOverlay.copy(syncMode = mode))
            },
            onTimeOffsetChanged = overlaysVm::updateTimeOffset,
            onKeyframeAdded = overlaysVm::addKeyframe,
            onDismiss = { showSyncConfig = false }
        )
    }
}

// --- Top Bar ---

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    aspectRatioLabel: String,
    showAspectRatioDropdown: Boolean,
    currentResolutionWidth: Int,
    currentResolutionHeight: Int,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onAspectRatioSelected: (AspectRatioPreset) -> Unit,
    onAspectRatioDropdownDismiss: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                modifier = Modifier.size(18.dp),
                tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }

        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                modifier = Modifier.size(18.dp),
                tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Canvas aspect ratio — compact chip
        Box(modifier = Modifier.padding(end = 4.dp)) {
            Text(
                text = aspectRatioLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onAspectRatioClick)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            AspectRatioDropdown(
                expanded = showAspectRatioDropdown,
                currentWidth = currentResolutionWidth,
                currentHeight = currentResolutionHeight,
                onSelectPreset = onAspectRatioSelected,
                onDismiss = onAspectRatioDropdownDismiss
            )
        }

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
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onAction(TimelineEditorAction.ClipSplit(selectedClip)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCut, "Split", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipDuplicated(selectedClip)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, "Duplicate", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onShowEffects, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Upload, "Effects", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipDeleted(selectedClip)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = { onAction(TimelineEditorAction.ClipSelected(null)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Deselect", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class ClipFilterPreset(
    val label: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
)

private data class ClipMotionPreset(
    val label: String,
    val positionX: Float,
    val scale: Float,
    val rotation: Float
)

private val ClipFilterPresets = listOf(
    ClipFilterPreset("Original", 0f, 1f, 1f),
    ClipFilterPreset("B&W", 0f, 1.05f, 0f),
    ClipFilterPreset("Warm", 0.05f, 1.05f, 1.15f),
    ClipFilterPreset("Cool", -0.03f, 1.05f, 0.85f),
    ClipFilterPreset("Vivid", 0.04f, 1.15f, 1.4f),
    ClipFilterPreset("Cinematic", -0.04f, 1.2f, 0.82f),
    ClipFilterPreset("Vintage", 0.08f, 0.9f, 0.6f),
    ClipFilterPreset("Faded", 0.1f, 0.85f, 0.5f),
    ClipFilterPreset("High Contrast", 0f, 1.4f, 1.1f),
    ClipFilterPreset("Night", -0.12f, 1.08f, 0.74f),
    ClipFilterPreset("Race Day", 0.06f, 1.18f, 1.28f),
    ClipFilterPreset("Noir", -0.08f, 1.3f, 0f)
)

private val ClipMotionPresets = listOf(
    ClipMotionPreset("Neutral", 0.5f, 1f, 0f),
    ClipMotionPreset("Punch In", 0.5f, 1.12f, 0f),
    ClipMotionPreset("Tilt", 0.5f, 1.05f, 5f),
    ClipMotionPreset("Left Drift", 0.42f, 1.08f, 0f),
    ClipMotionPreset("Right Drift", 0.58f, 1.08f, 0f)
)

// --- GPX Overlay Drawer ---

// Color palettes for route/profile style variants
private data class OverlayColorVariant(
    val label: String,
    val primary: androidx.compose.ui.graphics.Color,
    val fill: androidx.compose.ui.graphics.Color,
    val bg: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF0D1117)
)

private val routeColorVariants = listOf(
    OverlayColorVariant("Blue", androidx.compose.ui.graphics.Color(0xFF448AFF), androidx.compose.ui.graphics.Color(0x80448AFF)),
    OverlayColorVariant("Orange", androidx.compose.ui.graphics.Color(0xFFFF6B35), androidx.compose.ui.graphics.Color(0x80FF6B35)),
    OverlayColorVariant("Teal", androidx.compose.ui.graphics.Color(0xFF26A69A), androidx.compose.ui.graphics.Color(0x8026A69A)),
    OverlayColorVariant("White", androidx.compose.ui.graphics.Color(0xFFE0E0E0), androidx.compose.ui.graphics.Color(0x80E0E0E0)),
    OverlayColorVariant("Red", androidx.compose.ui.graphics.Color(0xFFEF5350), androidx.compose.ui.graphics.Color(0x80EF5350)),
    OverlayColorVariant("Green", androidx.compose.ui.graphics.Color(0xFF66BB6A), androidx.compose.ui.graphics.Color(0x8066BB6A))
)

// Stat tile definitions — one per metric
private data class StatTileDef(
    val label: String,
    val unit: String,
    val staticType: String,
    val dynamicType: String,
    val valueProvider: (com.gpxvideo.lib.gpxparser.GpxStats?) -> String
)

private val statTileDefs = listOf(
    StatTileDef("Distance", "km", "static_stat:TOTAL_DISTANCE", "dynamic_stat:ELAPSED_DISTANCE",
        { stats -> stats?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—" }),
    StatTileDef("Avg Pace", "min/km", "static_stat:AVG_PACE", "dynamic_stat:CURRENT_PACE",
        { stats -> stats?.let { val m = it.avgPace.toInt(); val s = ((it.avgPace - m) * 60).toInt(); "%d:%02d".format(m, s) } ?: "—" }),
    StatTileDef("Avg Speed", "km/h", "static_stat:AVG_SPEED", "dynamic_stat:CURRENT_SPEED",
        { stats -> stats?.let { "%.1f".format(it.avgSpeed) } ?: "—" }),
    StatTileDef("Max Speed", "km/h", "static_stat:MAX_SPEED", "dynamic_stat:CURRENT_SPEED",
        { stats -> stats?.let { "%.1f".format(it.maxSpeed) } ?: "—" }),
    StatTileDef("Elev. Gain", "m", "static_stat:TOTAL_ELEVATION_GAIN", "dynamic_stat:CURRENT_ELEVATION",
        { stats -> stats?.let { "%.0f".format(it.totalElevationGain) } ?: "—" }),
    StatTileDef("Duration", "", "static_stat:TOTAL_TIME", "dynamic_stat:ELAPSED_TIME",
        { stats -> stats?.let { val sec = it.totalDuration.seconds; "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) } ?: "—" }),
    StatTileDef("Avg HR", "bpm", "static_stat:AVG_HEART_RATE", "dynamic_stat:CURRENT_HEART_RATE",
        { stats -> stats?.avgHeartRate?.let { "%.0f".format(it) } ?: "—" }),
    StatTileDef("Max HR", "bpm", "static_stat:MAX_HEART_RATE", "dynamic_stat:CURRENT_HEART_RATE",
        { stats -> stats?.maxHeartRate?.toString() ?: "—" }),
    StatTileDef("Cadence", "rpm", "static_stat:AVG_CADENCE", "dynamic_stat:CURRENT_CADENCE",
        { stats -> stats?.avgCadence?.let { "%.0f".format(it) } ?: "—" }),
    StatTileDef("Power", "W", "static_stat:AVG_POWER", "dynamic_stat:CURRENT_POWER",
        { stats -> stats?.avgPower?.let { "%.0f".format(it) } ?: "—" }),
    StatTileDef("Avg Temp", "°C", "static_stat:AVG_TEMPERATURE", "dynamic_stat:CURRENT_TEMPERATURE",
        { stats -> stats?.avgTemperature?.let { "%.1f".format(it) } ?: "—" }),
    StatTileDef("Grade", "%", "static_stat:TOTAL_DISTANCE", "dynamic_stat:CURRENT_GRADE",
        { stats -> stats?.let { "%.1f".format(it.avgGrade) } ?: "—" })
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GpxOverlayDrawer(
    currentFileName: String?,
    hasGpx: Boolean,
    gpxData: com.gpxvideo.core.model.GpxData?,
    gpxStats: com.gpxvideo.lib.gpxparser.GpxStats?,
    onImportGpx: () -> Unit,
    onAddOverlay: (overlayType: String, displayName: String, com.gpxvideo.feature.overlays.OverlayStylePreset, com.gpxvideo.feature.overlays.OverlayFormatPreset) -> Unit,
    onDismiss: () -> Unit
) {
    // Flatten GPX points for canvas previews
    val allPoints = remember(gpxData) {
        gpxData?.tracks?.flatMap { t -> t.segments.flatMap { s -> s.points } } ?: emptyList()
    }

    // Tab: Routes, Elevation, Stats, Live
    var selectedTab by remember { mutableStateOf(0) }
    val tabLabels = listOf("Routes", "Elevation", "Stats", "Live")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPX Overlays", style = MaterialTheme.typography.titleMedium)
                if (currentFileName != null) {
                    Text(
                        text = currentFileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!hasGpx || gpxData == null) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Import a GPX file to unlock overlays",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(onClick = onImportGpx) { Text("Import GPX") }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                return@ModalBottomSheet
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabLabels.forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = idx }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab content inside a scrollable column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> { // Routes — actual mini route maps with different colors
                        Text("Tap to add a route overlay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        val cols = 3
                        routeColorVariants.chunked(cols).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { variant ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onAddOverlay(
                                                    "static_map", "Route (${variant.label})",
                                                    com.gpxvideo.feature.overlays.OverlayStylePreset.CLEAN,
                                                    com.gpxvideo.feature.overlays.OverlayFormatPreset.CARD
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        com.gpxvideo.feature.gpx.GpxRouteCanvas(
                                            points = allPoints,
                                            bounds = gpxData.bounds,
                                            routeColor = variant.primary,
                                            routeWidth = 2.5f,
                                            showStartEnd = true,
                                            backgroundColor = variant.bg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1.4f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(variant.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    1 -> { // Elevation — actual mini altitude profiles with different colors
                        Text("Tap to add an elevation profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        val cols = 2
                        routeColorVariants.chunked(cols).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { variant ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onAddOverlay(
                                                    "static_altitude_profile", "Elevation (${variant.label})",
                                                    com.gpxvideo.feature.overlays.OverlayStylePreset.CLEAN,
                                                    com.gpxvideo.feature.overlays.OverlayFormatPreset.CARD
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        com.gpxvideo.feature.gpx.AltitudeProfileCanvas(
                                            points = allPoints,
                                            lineColor = variant.primary,
                                            fillColor = variant.fill,
                                            showGrid = false,
                                            showLabels = false,
                                            backgroundColor = variant.bg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2.2f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(variant.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    2 -> { // Stats — individual stat value cards
                        Text("Tap to add a stat overlay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        val cols = 3
                        // Filter out stats without meaningful values
                        val availableStats = statTileDefs.filter { def ->
                            def.valueProvider(gpxStats) != "—"
                        }
                        availableStats.chunked(cols).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { def ->
                                    StatOverlayPreviewTile(
                                        label = def.label,
                                        value = def.valueProvider(gpxStats),
                                        unit = def.unit,
                                        onClick = {
                                            onAddOverlay(
                                                def.staticType, def.label,
                                                com.gpxvideo.feature.overlays.OverlayStylePreset.CLEAN,
                                                com.gpxvideo.feature.overlays.OverlayFormatPreset.CARD
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    3 -> { // Live — dynamic stat tiles
                        Text("Live overlays sync with video playback", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Live map
                        Text("Maps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            routeColorVariants.take(3).forEach { variant ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onAddOverlay(
                                                "dynamic_map", "Live Map",
                                                com.gpxvideo.feature.overlays.OverlayStylePreset.CLEAN,
                                                com.gpxvideo.feature.overlays.OverlayFormatPreset.CARD
                                            )
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    com.gpxvideo.feature.gpx.GpxRouteCanvas(
                                        points = allPoints,
                                        bounds = gpxData.bounds,
                                        routeColor = variant.primary,
                                        routeWidth = 2f,
                                        showStartEnd = true,
                                        backgroundColor = variant.bg,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Live", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Live stats
                        Text("Live Stats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        val cols = 3
                        val liveStats = statTileDefs.filter { def ->
                            def.valueProvider(gpxStats) != "—"
                        }
                        liveStats.chunked(cols).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { def ->
                                    StatOverlayPreviewTile(
                                        label = def.label,
                                        value = def.valueProvider(gpxStats),
                                        unit = def.unit,
                                        isLive = true,
                                        onClick = {
                                            onAddOverlay(
                                                def.dynamicType, "Live ${def.label}",
                                                com.gpxvideo.feature.overlays.OverlayStylePreset.CLEAN,
                                                com.gpxvideo.feature.overlays.OverlayFormatPreset.CARD
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatOverlayPreviewTile(
    label: String,
    value: String,
    unit: String,
    isLive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .background(
                    androidx.compose.ui.graphics.Color(0xFF161B22),
                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(0xFFEF5350),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun PillRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { label ->
            val isActive = label == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onSelected(label) }
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
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

private fun buildFilterColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float
): androidx.compose.ui.graphics.ColorMatrix {
    val androidMatrix = android.graphics.ColorMatrix().apply {
        setSaturation(saturation.coerceIn(0f, 1.8f))
    }
    val cVal = contrast.coerceIn(0.5f, 1.8f)
    val bVal = brightness.coerceIn(-0.4f, 0.4f) * 255f
    val translate = (1f - cVal) * 128f + bVal
    androidMatrix.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                cVal, 0f, 0f, 0f, translate,
                0f, cVal, 0f, 0f, translate,
                0f, 0f, cVal, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return androidx.compose.ui.graphics.ColorMatrix(androidMatrix.array)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsSheet(
    frameBitmap: android.graphics.Bitmap?,
    targetClip: TimelineClipState?,
    onColorAdjustmentsChange: (UUID, Float, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (targetClip == null) {
                Text(
                    text = "Add a clip to the timeline first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // 3-column grid of filter preview tiles
                val columns = 3
                val rows = (ClipFilterPresets.size + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until columns) {
                            val idx = row * columns + col
                            if (idx < ClipFilterPresets.size) {
                                val preset = ClipFilterPresets[idx]
                                val isActive = targetClip.brightness == preset.brightness &&
                                    targetClip.contrast == preset.contrast &&
                                    targetClip.saturation == preset.saturation
                                FilterPreviewTile(
                                    label = preset.label,
                                    isActive = isActive,
                                    brightness = preset.brightness,
                                    contrast = preset.contrast,
                                    saturation = preset.saturation,
                                    frameBitmap = frameBitmap,
                                    onClick = {
                                        onColorAdjustmentsChange(
                                            targetClip.id,
                                            preset.brightness,
                                            preset.contrast,
                                            preset.saturation
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (row < rows - 1) Spacer(modifier = Modifier.height(10.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FilterPreviewTile(
    label: String,
    isActive: Boolean,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    frameBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filterMatrix = remember(brightness, contrast, saturation) {
        buildFilterColorMatrix(brightness, contrast, saturation)
    }

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0xFF1A1A2A))
                .then(
                    if (isActive) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    )
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (frameBitmap != null) {
                Image(
                    bitmap = frameBitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(filterMatrix),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback gradient placeholder
                val bgColor = when {
                    saturation == 0f -> androidx.compose.ui.graphics.Color(0xFF3A3A3A)
                    brightness < 0 -> androidx.compose.ui.graphics.Color(0xFF1A2540)
                    contrast > 1.1f && saturation > 1.1f -> androidx.compose.ui.graphics.Color(0xFF2A1A12)
                    contrast > 1.1f -> androidx.compose.ui.graphics.Color(0xFF1A1A2A)
                    else -> androidx.compose.ui.graphics.Color(0xFF2A2A2A)
                }
                Box(modifier = Modifier.fillMaxSize().background(bgColor))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MotionPreviewTile(
    label: String,
    frameBitmap: android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (frameBitmap != null) {
                Image(
                    bitmap = frameBitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay icon to indicate motion type
                val icon = when (label) {
                    "Punch In" -> Icons.Default.Upload
                    "Tilt" -> Icons.AutoMirrored.Filled.Undo
                    "Left Drift" -> Icons.AutoMirrored.Filled.ArrowBack
                    "Right Drift" -> Icons.AutoMirrored.Filled.Redo
                    else -> null
                }
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            } else {
                val icon = when (label) {
                    "Punch In" -> Icons.Default.Upload
                    "Tilt" -> Icons.AutoMirrored.Filled.Undo
                    "Left Drift" -> Icons.AutoMirrored.Filled.ArrowBack
                    "Right Drift" -> Icons.AutoMirrored.Filled.Redo
                    else -> null
                }
                if (icon != null) {
                    Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp, 16.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatValue: (Float) -> String = { "${"%.2f".format(it)}" },
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.weight(1f).height(32.dp)
        )
        Text(
            text = formatValue(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlaySettingsSheet(
    overlay: OverlayConfig,
    onUpdate: (OverlayConfig) -> Unit,
    onDelete: () -> Unit,
    onOpenSyncConfig: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        OverlayConfigPanel(
            overlay = overlay,
            onUpdate = onUpdate,
            onDelete = onDelete,
            onOpenSyncConfig = onOpenSyncConfig,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun deleteOverlayTrackOrClip(
    timelineVm: TimelineViewModel,
    overlay: OverlayConfig,
    track: TimelineTrackState?
) {
    if (track != null && track.clips.size <= 1) {
        timelineVm.deleteTrack(track.id)
    } else {
        timelineVm.deleteClip(overlay.timelineClipId)
    }
}
