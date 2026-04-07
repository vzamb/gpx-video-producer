package com.gpxvideo.feature.project

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.TransitionType
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.preview.PreviewClip
import com.gpxvideo.feature.preview.PreviewDisplayTransform
import com.gpxvideo.feature.preview.PreviewEngine
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.feature.timeline.ClipContentMode
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineViewModel
import com.gpxvideo.feature.timeline.toColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.math.roundToInt

// ── Theme constants ─────────────────────────────────────────────────────────

private val DarkBg = Color(0xFF0D0D12)
private val CardBg = Color(0xFF1A1A2E)
private val SurfaceBg = Color(0xFF16162A)
private val AccentBlue = Color(0xFF448AFF)
private val PlayheadRed = Color(0xFFFF3D3D)

/**
 * Screen 1: "The Cut" — Video Assembly
 * Professional mobile timeline editor with trim handles, transitions, effects,
 * and synced preview playback.
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

    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    if (uiState.isLoading) {
        LoadingIndicator()
        return
    }

    // Create TimelineViewModel only after the project is loaded
    val timelineContent: @Composable (
        timelineVm: TimelineViewModel,
        timelineState: TimelineState
    ) -> Unit = { timelineVm, timelineState ->
        val videoClips = remember(timelineState.tracks) {
            timelineState.tracks
                .filter { it.type == TrackType.VIDEO }
                .flatMap { it.clips }
        }
        val mediaItems = uiState.mediaItems

        // Sync timeline clips to PreviewEngine
        LaunchedEffect(videoClips, mediaItems) {
            val mediaMap = mediaItems.associateBy { it.id }
            val previewClips = videoClips.mapNotNull { clip ->
                val media = clip.mediaItemId?.let { mediaMap[it] } ?: return@mapNotNull null
                val path = media.localCopyPath.ifBlank { media.sourcePath }
                val uri = if (path.startsWith("content://")) Uri.parse(path)
                else Uri.fromFile(java.io.File(path))
                val clipDuration = clip.endTimeMs - clip.startTimeMs
                PreviewClip(
                    uri = uri,
                    startMs = clip.trimStartMs,
                    endMs = (clipDuration - clip.trimEndMs).coerceAtLeast(0L),
                    speed = clip.speed,
                    displayTransform = PreviewDisplayTransform(
                        contentMode = clip.contentMode,
                        positionX = clip.positionX,
                        positionY = clip.positionY,
                        rotationDegrees = clip.rotation,
                        scale = clip.scale,
                        brightness = clip.brightness,
                        contrast = clip.contrast,
                        saturation = clip.saturation
                    )
                )
            }
            viewModel.previewEngine.setMediaSources(previewClips)
        }

        // Sync preview position → timeline playhead
        LaunchedEffect(Unit) {
            snapshotFlow { currentPositionMs }.collect { pos ->
                timelineVm.setPlayheadPosition(pos)
            }
        }

        TimelineAssemblyContent(
            viewModel = viewModel,
            timelineViewModel = timelineVm,
            uiState = uiState,
            timelineState = timelineState,
            videoClips = videoClips,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs,
            videoDuration = videoDuration,
            showAspectRatioMenu = showAspectRatioMenu,
            onToggleAspectRatioMenu = { showAspectRatioMenu = !showAspectRatioMenu },
            onAspectRatioSelected = {
                viewModel.setAspectRatio(it)
                showAspectRatioMenu = false
            },
            onNavigateBack = onNavigateBack,
            onAddClips = {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onAddActivity = { gpxPickerLauncher.launch(arrayOf("*/*")) },
            onGoToStyle = {
                if (projectIdStr.isNotBlank()) onNavigateToStyle(projectIdStr)
            }
        )
    }

    if (uiState.project != null) {
        val timelineVm = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
            creationCallback = { factory: TimelineViewModel.Factory -> factory.create(projectIdStr) }
        )
        val timelineState by timelineVm.state.collectAsStateWithLifecycle()
        timelineContent(timelineVm, timelineState)
    } else {
        // Fallback: no project loaded yet, show empty
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}

// ── Main Assembly Layout ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineAssemblyContent(
    viewModel: ProjectEditorViewModel,
    timelineViewModel: TimelineViewModel,
    uiState: ProjectEditorUiState,
    timelineState: TimelineState,
    videoClips: List<TimelineClipState>,
    isPlaying: Boolean,
    currentPositionMs: Long,
    videoDuration: Long,
    showAspectRatioMenu: Boolean,
    onToggleAspectRatioMenu: () -> Unit,
    onAspectRatioSelected: (SocialAspectRatio) -> Unit,
    onNavigateBack: () -> Unit,
    onAddClips: () -> Unit,
    onAddActivity: () -> Unit,
    onGoToStyle: () -> Unit
) {
    val selectedClip = remember(timelineState.selectedClipId, videoClips) {
        timelineState.selectedClipId?.let { id -> videoClips.find { it.id == id } }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            AssemblyTopBar(
                title = uiState.project?.name ?: "New Story",
                selectedRatio = uiState.selectedAspectRatio,
                showAspectRatioMenu = showAspectRatioMenu,
                onToggleAspectRatioMenu = onToggleAspectRatioMenu,
                onAspectRatioSelected = onAspectRatioSelected,
                onNavigateBack = onNavigateBack,
                canUndo = timelineState.canUndo,
                canRedo = timelineState.canRedo,
                onUndo = timelineViewModel::undo,
                onRedo = timelineViewModel::redo
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video preview
            VideoPreviewArea(
                previewEngine = viewModel.previewEngine,
                mediaItems = uiState.mediaItems,
                aspectRatio = uiState.selectedAspectRatio,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                videoDuration = videoDuration,
                onTogglePlayback = {
                    viewModel.togglePlayback()
                    timelineViewModel.togglePlayback()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Timeline strip
            TimelineStripSection(
                timelineState = timelineState,
                videoClips = videoClips,
                currentPositionMs = currentPositionMs,
                isImporting = uiState.isImporting,
                onSelectClip = timelineViewModel::selectClip,
                onTrimClip = timelineViewModel::trimClip,
                onSeek = { ms ->
                    viewModel.seekTo(ms)
                    timelineViewModel.setPlayheadPosition(ms)
                },
                onZoomChange = timelineViewModel::setZoomLevel,
                onAddClips = onAddClips,
                onTransitionTap = { clipId ->
                    timelineViewModel.selectClip(clipId)
                }
            )

            // Tool panel (when clip selected)
            AnimatedVisibility(visible = selectedClip != null) {
                selectedClip?.let { clip ->
                    ToolPanel(
                        clip = clip,
                        onUpdateEffects = { b, c, s ->
                            timelineViewModel.updateClipColorAdjustments(clip.id, b, c, s)
                        },
                        onSetEntryTransition = { type, dur ->
                            timelineViewModel.setClipEntryTransition(clip.id, type, dur)
                        },
                        onSetExitTransition = { type, dur ->
                            timelineViewModel.setClipExitTransition(clip.id, type, dur)
                        },
                        onSetVolume = { timelineViewModel.setClipVolume(clip.id, it) },
                        onSetSpeed = { timelineViewModel.setClipSpeed(clip.id, it) },
                        onSplit = { timelineViewModel.splitClipAtPlayhead(clip.id) },
                        onDuplicate = { timelineViewModel.duplicateClip(clip.id) },
                        onDelete = {
                            timelineViewModel.deleteClip(clip.id)
                            timelineViewModel.selectClip(null)
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bottom CTA
            AddActivityButton(
                hasGpxData = uiState.gpxData != null,
                hasMedia = uiState.mediaItems.isNotEmpty(),
                isImportingGpx = uiState.isImportingGpx,
                onAddActivity = onAddActivity,
                onGoToStyle = onGoToStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

// ── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssemblyTopBar(
    title: String,
    selectedRatio: SocialAspectRatio,
    showAspectRatioMenu: Boolean,
    onToggleAspectRatioMenu: () -> Unit,
    onAspectRatioSelected: (SocialAspectRatio) -> Unit,
    onNavigateBack: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {}
) {
    Surface(
        color = DarkBg,
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

            // Undo / Redo
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(20.dp)
                )
            }

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

// ── Video Preview ───────────────────────────────────────────────────────────

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
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .aspectRatio(canvasRatio)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                VideoPreview(
                    previewEngine = previewEngine,
                    modifier = Modifier.fillMaxSize()
                )

                PlayPauseOverlay(
                    isPlaying = isPlaying,
                    onToggle = onTogglePlayback,
                    modifier = Modifier.fillMaxSize()
                )

                PlaybackProgressBar(
                    currentPositionMs = currentPositionMs,
                    durationMs = videoDuration,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                )

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
    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        ),
        contentAlignment = Alignment.Center
    ) {
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
        color = AccentBlue,
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

// ── Timeline Strip ──────────────────────────────────────────────────────────

private const val PX_PER_SECOND_BASE = 40f

@Composable
private fun TimelineStripSection(
    timelineState: TimelineState,
    videoClips: List<TimelineClipState>,
    currentPositionMs: Long,
    isImporting: Boolean,
    onSelectClip: (UUID?) -> Unit,
    onTrimClip: (UUID, Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onZoomChange: (Float) -> Unit,
    onAddClips: () -> Unit,
    onTransitionTap: (UUID) -> Unit
) {
    val totalDuration = timelineState.totalDurationMs.coerceAtLeast(1L)
    val zoom = timelineState.zoomLevel
    val pxPerMs = (PX_PER_SECOND_BASE * zoom) / 1000f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBg)
    ) {
        // Timeline header: label, zoom slider, clip count
        TimelineHeader(
            clipCount = videoClips.size,
            totalDurationMs = totalDuration,
            zoomLevel = zoom,
            onZoomChange = onZoomChange
        )

        // Clip blocks row
        val scrollState = rememberScrollState()
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .height(IntrinsicSize.Max),
                verticalAlignment = Alignment.CenterVertically
            ) {
                videoClips.forEachIndexed { index, clip ->
                    // Transition diamond between clips
                    if (index > 0) {
                        TransitionDiamond(
                            entryTransition = clip.entryTransitionType,
                            onClick = { onTransitionTap(clip.id) }
                        )
                    }

                    ClipBlock(
                        clip = clip,
                        isSelected = clip.id == timelineState.selectedClipId,
                        pxPerMs = pxPerMs,
                        onClick = {
                            onSelectClip(
                                if (clip.id == timelineState.selectedClipId) null else clip.id
                            )
                        },
                        onTrimStart = { deltaMs ->
                            val newStart = (clip.trimStartMs + deltaMs).coerceIn(
                                0L,
                                (clip.endTimeMs - clip.startTimeMs) - clip.trimEndMs - 100
                            )
                            onTrimClip(clip.id, newStart, clip.trimEndMs)
                        },
                        onTrimEnd = { deltaMs ->
                            val newEnd = (clip.trimEndMs - deltaMs).coerceIn(
                                0L,
                                (clip.endTimeMs - clip.startTimeMs) - clip.trimStartMs - 100
                            )
                            onTrimClip(clip.id, clip.trimStartMs, newEnd)
                        }
                    )
                }

                // Add clip button at the end
                Spacer(Modifier.width(6.dp))
                AddClipButton(
                    isImporting = isImporting,
                    onClick = onAddClips
                )
            }

            // Playhead line
            if (totalDuration > 0 && videoClips.isNotEmpty()) {
                val playheadOffsetDp = with(density) {
                    ((currentPositionMs * pxPerMs) + 16f).toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(PlayheadRed)
                )
            }
        }

        // Scrubber + time display
        TimelineScrubber(
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDuration,
            onSeek = onSeek
        )
    }
}

@Composable
private fun TimelineHeader(
    clipCount: Int,
    totalDurationMs: Long,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "TIMELINE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 2.sp
        )

        if (clipCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$clipCount clip${if (clipCount != 1) "s" else ""} · ${formatDurationMs(totalDurationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f)
            )
        }

        Spacer(Modifier.weight(1f))

        // Zoom slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(100.dp)
        ) {
            Text(
                "−",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = zoomLevel,
                onValueChange = onZoomChange,
                valueRange = 0.25f..8f,
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
            Text(
                "+",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ClipBlock(
    clip: TimelineClipState,
    isSelected: Boolean,
    pxPerMs: Float,
    onClick: () -> Unit,
    onTrimStart: (Long) -> Unit,
    onTrimEnd: (Long) -> Unit
) {
    val clipDuration = clip.endTimeMs - clip.startTimeMs
    val visibleDuration = (clipDuration - clip.trimStartMs - clip.trimEndMs).coerceAtLeast(100)
    val density = LocalDensity.current
    val widthDp = with(density) { (visibleDuration * pxPerMs).coerceAtLeast(40f).toDp() }
    val borderColor = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.12f)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        clip.color.copy(alpha = 0.6f),
                        clip.color.copy(alpha = 0.35f)
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        // Clip label + duration
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                clip.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatDurationMs(visibleDuration),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // Left trim handle
        TrimHandle(
            modifier = Modifier.align(Alignment.CenterStart),
            onDrag = { deltaPx ->
                val deltaMs = (deltaPx / pxPerMs).toLong()
                onTrimStart(deltaMs)
            }
        )

        // Right trim handle
        TrimHandle(
            modifier = Modifier.align(Alignment.CenterEnd),
            onDrag = { deltaPx ->
                val deltaMs = (deltaPx / pxPerMs).toLong()
                onTrimEnd(deltaMs)
            }
        )
    }
}

@Composable
private fun TrimHandle(
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    onDrag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Visual grip lines
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 2.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
private fun TransitionDiamond(
    entryTransition: String?,
    onClick: () -> Unit
) {
    val hasTransition = entryTransition != null && entryTransition != "CUT"
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .size(18.dp)
            .rotate(45f)
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (hasTransition) AccentBlue.copy(alpha = 0.7f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(
                1.dp,
                if (hasTransition) AccentBlue else Color.White.copy(alpha = 0.2f),
                RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (hasTransition) {
            Text(
                "◆",
                fontSize = 6.sp,
                color = Color.White,
                modifier = Modifier.rotate(-45f)
            )
        }
    }
}

@Composable
private fun AddClipButton(isImporting: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable(enabled = !isImporting, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isImporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add clips",
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun TimelineScrubber(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = { frac ->
                onSeek((frac * totalDurationMs).toLong())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = PlayheadRed,
                activeTrackColor = PlayheadRed.copy(alpha = 0.7f),
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDurationMs(currentPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
            Text(
                formatDurationMs(totalDurationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp
            )
        }
    }
}

// ── Tool Panel ──────────────────────────────────────────────────────────────

private enum class ToolTab(val label: String) {
    EFFECTS("Effects"),
    TRANSITIONS("Transitions"),
    AUDIO("Audio")
}

@Composable
private fun ToolPanel(
    clip: TimelineClipState,
    onUpdateEffects: (brightness: Float, contrast: Float, saturation: Float) -> Unit,
    onSetEntryTransition: (type: String?, durationMs: Long?) -> Unit,
    onSetExitTransition: (type: String?, durationMs: Long?) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ToolTab.EFFECTS) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CardBg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = AccentBlue,
                            height = 2.dp
                        )
                    }
                },
                divider = {}
            ) {
                ToolTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == tab) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    )
                }
            }

            // Tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (selectedTab) {
                    ToolTab.EFFECTS -> EffectsTabContent(clip, onUpdateEffects)
                    ToolTab.TRANSITIONS -> TransitionsTabContent(
                        clip, onSetEntryTransition, onSetExitTransition
                    )
                    ToolTab.AUDIO -> AudioTabContent(clip, onSetVolume, onSetSpeed)
                }
            }

            // Actions row
            ActionsRow(
                onSplit = onSplit,
                onDuplicate = onDuplicate,
                onDelete = onDelete
            )
        }
    }
}

// ── Effects Tab ─────────────────────────────────────────────────────────────

private data class LutPreset(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
)

private val lutPresets = listOf(
    LutPreset("Normal", 0f, 1f, 1f),
    LutPreset("Warm", 0.08f, 1.1f, 1.2f),
    LutPreset("Cool", -0.05f, 1.05f, 0.8f),
    LutPreset("B&W", 0f, 1.2f, 0f),
    LutPreset("Vivid", 0.05f, 1.3f, 1.6f)
)

@Composable
private fun EffectsTabContent(
    clip: TimelineClipState,
    onUpdateEffects: (brightness: Float, contrast: Float, saturation: Float) -> Unit
) {
    var brightness by remember(clip.id) { mutableFloatStateOf(clip.brightness) }
    var contrast by remember(clip.id) { mutableFloatStateOf(clip.contrast) }
    var saturation by remember(clip.id) { mutableFloatStateOf(clip.saturation) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Preset buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            lutPresets.forEach { preset ->
                val isActive = brightness == preset.brightness &&
                    contrast == preset.contrast &&
                    saturation == preset.saturation
                Surface(
                    onClick = {
                        brightness = preset.brightness
                        contrast = preset.contrast
                        saturation = preset.saturation
                        onUpdateEffects(preset.brightness, preset.contrast, preset.saturation)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) AccentBlue.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(
                        1.dp,
                        if (isActive) AccentBlue else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        preset.name,
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        EffectSlider(
            label = "Brightness",
            value = brightness,
            range = -0.4f..0.4f,
            onValueChange = {
                brightness = it
                onUpdateEffects(it, contrast, saturation)
            }
        )
        EffectSlider(
            label = "Contrast",
            value = contrast,
            range = 0.5f..1.8f,
            onValueChange = {
                contrast = it
                onUpdateEffects(brightness, it, saturation)
            }
        )
        EffectSlider(
            label = "Saturation",
            value = saturation,
            range = 0f..1.8f,
            onValueChange = {
                saturation = it
                onUpdateEffects(brightness, contrast, it)
            }
        )
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(70.dp),
            fontSize = 11.sp
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.08f)
            )
        )
        Text(
            "%.2f".format(value),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            fontSize = 10.sp
        )
    }
}

// ── Transitions Tab ─────────────────────────────────────────────────────────

@Composable
private fun TransitionsTabContent(
    clip: TimelineClipState,
    onSetEntryTransition: (type: String?, durationMs: Long?) -> Unit,
    onSetExitTransition: (type: String?, durationMs: Long?) -> Unit
) {
    val transitionTypes = listOf("CUT") + TransitionType.entries.map { it.name }

    var entryType by remember(clip.id) { mutableStateOf(clip.entryTransitionType ?: "CUT") }
    var entryDuration by remember(clip.id) {
        mutableFloatStateOf((clip.entryTransitionDurationMs ?: 500L).toFloat())
    }
    var exitType by remember(clip.id) { mutableStateOf(clip.exitTransitionType ?: "CUT") }
    var exitDuration by remember(clip.id) {
        mutableFloatStateOf((clip.exitTransitionDurationMs ?: 500L).toFloat())
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Entry transition
        TransitionPicker(
            label = "Entry",
            selectedType = entryType,
            types = transitionTypes,
            durationMs = entryDuration,
            onTypeSelected = { type ->
                entryType = type
                val t = if (type == "CUT") null else type
                val d = if (type == "CUT") null else entryDuration.toLong()
                onSetEntryTransition(t, d)
            },
            onDurationChange = { ms ->
                entryDuration = ms
                if (entryType != "CUT") {
                    onSetEntryTransition(entryType, ms.toLong())
                }
            }
        )

        // Exit transition
        TransitionPicker(
            label = "Exit",
            selectedType = exitType,
            types = transitionTypes,
            durationMs = exitDuration,
            onTypeSelected = { type ->
                exitType = type
                val t = if (type == "CUT") null else type
                val d = if (type == "CUT") null else exitDuration.toLong()
                onSetExitTransition(t, d)
            },
            onDurationChange = { ms ->
                exitDuration = ms
                if (exitType != "CUT") {
                    onSetExitTransition(exitType, ms.toLong())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionPicker(
    label: String,
    selectedType: String,
    types: List<String>,
    durationMs: Float,
    onTypeSelected: (String) -> Unit,
    onDurationChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(40.dp),
                fontSize = 11.sp
            )

            Box {
                Surface(
                    onClick = { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            selectedType.lowercase().replaceFirstChar { it.uppercase() }
                                .replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text("▾", color = Color.White.copy(alpha = 0.4f), fontSize = 8.sp)
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    types.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    type.lowercase().replaceFirstChar { it.uppercase() }
                                        .replace("_", " "),
                                    fontWeight = if (type == selectedType) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onTypeSelected(type)
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (selectedType != "CUT") {
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = durationMs,
                    onValueChange = onDurationChange,
                    valueRange = 100f..2000f,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                    )
                )
                Text(
                    "${durationMs.roundToInt()}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── Audio Tab ───────────────────────────────────────────────────────────────

@Composable
private fun AudioTabContent(
    clip: TimelineClipState,
    onSetVolume: (Float) -> Unit,
    onSetSpeed: (Float) -> Unit
) {
    var volume by remember(clip.id) { mutableFloatStateOf(clip.volume) }
    var speed by remember(clip.id) { mutableFloatStateOf(clip.speed) }
    var isMuted by remember(clip.id) { mutableStateOf(clip.volume == 0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Volume
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {
                    isMuted = !isMuted
                    if (isMuted) {
                        onSetVolume(0f)
                    } else {
                        val restored = if (volume == 0f) 1f else volume
                        volume = restored
                        onSetVolume(restored)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted) Color.White.copy(alpha = 0.3f) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                "Volume",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(50.dp),
                fontSize = 11.sp
            )
            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = {
                    volume = it
                    isMuted = it == 0f
                    onSetVolume(it)
                },
                valueRange = 0f..2f,
                enabled = !isMuted,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f),
                    disabledThumbColor = Color.White.copy(alpha = 0.2f),
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
            Text(
                "${(volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                fontSize = 10.sp
            )
        }

        // Speed
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.width(32.dp))
            Text(
                "Speed",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(50.dp),
                fontSize = 11.sp
            )
            Slider(
                value = speed,
                onValueChange = {
                    speed = it
                    onSetSpeed(it)
                },
                valueRange = 0.25f..4f,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                )
            )
            Text(
                "%.1fx".format(speed),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                fontSize = 10.sp
            )
        }
    }
}

// ── Actions Row ─────────────────────────────────────────────────────────────

@Composable
private fun ActionsRow(
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        ActionChip(
            icon = Icons.Default.ContentCut,
            label = "Split",
            onClick = onSplit
        )
        ActionChip(
            icon = Icons.Default.ContentCopy,
            label = "Duplicate",
            onClick = onDuplicate
        )
        ActionChip(
            icon = Icons.Default.Delete,
            label = "Delete",
            onClick = onDelete,
            tint = Color(0xFFFF5252)
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White.copy(alpha = 0.7f)
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(14.dp),
                tint = tint
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
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
                containerColor = AccentBlue
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
