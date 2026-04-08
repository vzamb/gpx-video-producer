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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.preview.PreviewClip
import com.gpxvideo.feature.preview.PreviewDisplayTransform
import com.gpxvideo.feature.preview.PreviewEngine
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.feature.timeline.TimelineState
import com.gpxvideo.feature.timeline.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

// ── Theme constants ─────────────────────────────────────────────────────────

private val DarkBg = Color(0xFF0D0D12)
private val CardBg = Color(0xFF1A1A2E)
private val SurfaceBg = Color(0xFF16162A)
private val AccentBlue = Color(0xFF448AFF)
private val PlayheadRed = Color(0xFFFF3D3D)

private const val DP_PER_SECOND = 30f
private const val TRANSITION_GAP_DP = 28f

// ── Effect presets ──────────────────────────────────────────────────────────

private data class EffectPreset(
    val name: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
)

private val effectPresets = listOf(
    EffectPreset("Original", 0f, 1f, 1f),
    EffectPreset("Warm", 0.08f, 1.1f, 1.2f),
    EffectPreset("Cool", -0.05f, 1.05f, 0.8f),
    EffectPreset("B&W", 0f, 1.2f, 0f),
    EffectPreset("Vivid", 0.05f, 1.3f, 1.6f),
    EffectPreset("Cinematic", -0.03f, 1.15f, 0.7f),
    EffectPreset("Vintage", 0.1f, 0.9f, 0.5f),
    EffectPreset("Dramatic", -0.05f, 1.5f, 0.6f)
)

// ── Transition options ──────────────────────────────────────────────────────

private data class TransitionOption(val label: String, val type: String?)

private val transitionOptions = listOf(
    TransitionOption("Cut", null),
    TransitionOption("Fade", "FADE"),
    TransitionOption("Dissolve", "DISSOLVE"),
    TransitionOption("Slide", "SLIDE_LEFT"),
    TransitionOption("Wipe", "WIPE_LEFT")
)

// ═════════════════════════════════════════════════════════════════════════════
// 1. VideoAssemblyScreen — main entry
// ═════════════════════════════════════════════════════════════════════════════

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

        // Helper to build PreviewClips from timeline state
        fun buildPreviewClips(): List<PreviewClip> {
            val mediaMap = mediaItems.associateBy { it.id }
            return videoClips.mapNotNull { clip ->
                val media = clip.mediaItemId?.let { mediaMap[it] } ?: return@mapNotNull null
                val path = media.localCopyPath.ifBlank { media.sourcePath }
                val uri = if (path.startsWith("content://")) Uri.parse(path)
                          else Uri.fromFile(java.io.File(path))
                val sourceAR = if (media.height > 0) {
                    val r = media.rotation % 360
                    if (r == 90 || r == 270) media.height.toFloat() / media.width.toFloat()
                    else media.width.toFloat() / media.height.toFloat()
                } else 0f
                val effectiveDurationMs = clip.endTimeMs - clip.startTimeMs
                PreviewClip(
                    uri = uri,
                    startMs = clip.trimStartMs,
                    endMs = clip.trimStartMs + effectiveDurationMs,
                    speed = clip.speed,
                    volume = clip.volume,
                    displayTransform = PreviewDisplayTransform(
                        contentMode = clip.contentMode,
                        positionX = clip.positionX,
                        positionY = clip.positionY,
                        rotationDegrees = clip.rotation,
                        scale = clip.scale,
                        brightness = clip.brightness,
                        contrast = clip.contrast,
                        saturation = clip.saturation,
                        sourceVideoAspectRatio = sourceAR
                    )
                )
            }
        }

        // Structural key: only changes when clip list, URIs, trim, or speed change
        // (NOT when brightness/contrast/saturation change)
        val clipStructureKey = remember(videoClips) {
            videoClips.map { c ->
                listOf(c.mediaItemId, c.trimStartMs, c.endTimeMs - c.startTimeMs, c.speed)
            }
        }

        // Reload ExoPlayer media only on structural changes
        LaunchedEffect(clipStructureKey, mediaItems) {
            viewModel.previewEngine.setMediaSources(buildPreviewClips())
        }

        // Update display transforms (effects) without resetting playback position
        val clipDisplayKey = remember(videoClips) {
            videoClips.map { c ->
                listOf(c.brightness, c.contrast, c.saturation, c.contentMode, c.scale, c.rotation, c.volume)
            }
        }
        LaunchedEffect(clipDisplayKey) {
            viewModel.previewEngine.updateDisplayTransforms(buildPreviewClips())
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
        Box(
            modifier = Modifier.fillMaxSize().background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. TimelineAssemblyContent — Scaffold with layout
// ═════════════════════════════════════════════════════════════════════════════

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
    val mediaMap = remember(uiState.mediaItems) { uiState.mediaItems.associateBy { it.id } }

    // Auto-select the clip under the playhead during playback
    val clipUnderPlayhead = remember(currentPositionMs, videoClips) {
        if (videoClips.isEmpty()) null
        else videoClips.find { currentPositionMs >= it.startTimeMs && currentPositionMs < it.endTimeMs }
            ?: videoClips.lastOrNull { currentPositionMs >= it.startTimeMs }
    }
    LaunchedEffect(clipUnderPlayhead?.id, isPlaying) {
        if (isPlaying && clipUnderPlayhead != null && clipUnderPlayhead.id != timelineState.selectedClipId) {
            timelineViewModel.selectClip(clipUnderPlayhead.id)
        }
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
                onNavigateBack = onNavigateBack
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
                videoClips = videoClips,
                onTogglePlayback = {
                    viewModel.togglePlayback()
                    timelineViewModel.togglePlayback()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Action bar (when clip selected)
            AnimatedVisibility(visible = selectedClip != null) {
                selectedClip?.let { clip ->
                    ClipActionBar(
                        isMuted = clip.volume == 0f,
                        canUndo = timelineState.canUndo,
                        canRedo = timelineState.canRedo,
                        onToggleMute = {
                            timelineViewModel.setClipVolume(
                                clip.id,
                                if (clip.volume == 0f) 1f else 0f
                            )
                        },
                        onDuplicate = { timelineViewModel.duplicateClip(clip.id) },
                        onDelete = {
                            timelineViewModel.deleteClip(clip.id)
                            timelineViewModel.selectClip(null)
                        },
                        onUndo = timelineViewModel::undo,
                        onRedo = timelineViewModel::redo
                    )
                }
            }

            // Effect presets (when clip selected) — above timeline for stable layout
            AnimatedVisibility(visible = selectedClip != null) {
                selectedClip?.let { clip ->
                    val mediaItem = clip.mediaItemId?.let { mediaMap[it] }
                    EffectPresetRow(
                        clip = clip,
                        mediaItem = mediaItem,
                        onApplyPreset = { b, c, s ->
                            timelineViewModel.updateClipColorAdjustments(clip.id, b, c, s)
                        }
                    )
                }
            }

            // Timeline
            FrameTimeline(
                videoClips = videoClips,
                mediaItems = uiState.mediaItems,
                selectedClipId = timelineState.selectedClipId,
                currentPositionMs = currentPositionMs,
                totalDurationMs = timelineState.totalDurationMs,
                isPlaying = isPlaying,
                isImporting = uiState.isImporting,
                onSelectClip = timelineViewModel::selectClip,
                onBeginTrimDrag = timelineViewModel::beginTrimDrag,
                onTrimDrag = timelineViewModel::trimClipDirect,
                onCommitTrimDrag = timelineViewModel::commitTrimDrag,
                onSetEntryTransition = timelineViewModel::setClipEntryTransition,
                onSeek = { ms ->
                    viewModel.seekTo(ms)
                    timelineViewModel.setPlayheadPosition(ms)
                },
                onAddClips = onAddClips
            )

            // Scrubber
            TimelineScrubber(
                currentPositionMs = currentPositionMs,
                totalDurationMs = timelineState.totalDurationMs.coerceAtLeast(1L),
                onSeek = { ms ->
                    viewModel.seekTo(ms)
                    timelineViewModel.setPlayheadPosition(ms)
                }
            )

            // Bottom CTA — fixed below everything
            if (uiState.mediaItems.isNotEmpty()) {
                val hasGpx = uiState.gpxData != null
                Button(
                    onClick = { if (hasGpx) onGoToStyle() else onAddActivity() },
                    enabled = !uiState.isImportingGpx,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasGpx) AccentBlue else Color(0xFF00C853),
                        disabledContainerColor = Color(0xFF00C853).copy(alpha = 0.4f)
                    )
                ) {
                    if (uiState.isImportingGpx) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Importing GPX…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            if (hasGpx) Icons.Default.Route else Icons.Default.Route,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasGpx) "Continue to Overlays →" else "Next: Add GPX Activity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. AssemblyTopBar — simplified (no undo/redo)
// ═════════════════════════════════════════════════════════════════════════════

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
    Surface(color = DarkBg, tonalElevation = 0.dp) {
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

            // Aspect ratio dropdown
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
                        Text("▾", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
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
                                            fontWeight = if (ratio == selectedRatio) FontWeight.Bold
                                            else FontWeight.Normal
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

// ═════════════════════════════════════════════════════════════════════════════
// 4. VideoPreviewArea — preview box with play/pause and progress
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoPreviewArea(
    previewEngine: PreviewEngine,
    mediaItems: List<MediaItemEntity>,
    aspectRatio: SocialAspectRatio,
    isPlaying: Boolean,
    currentPositionMs: Long,
    videoDuration: Long,
    videoClips: List<TimelineClipState>,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canvasRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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

                // Transition overlay on top of video
                TransitionOverlay(
                    currentPositionMs = currentPositionMs,
                    videoClips = videoClips
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
    } else 0f
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

// ═════════════════════════════════════════════════════════════════════════════
// 4b. TransitionOverlay — renders fade/dissolve/slide/wipe in video preview
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TransitionOverlay(
    currentPositionMs: Long,
    videoClips: List<TimelineClipState>
) {
    // Transitions span BOTH clips:
    //   last half of clip N (fade-out, progress 0→0.5)
    //   first half of clip N+1 (fade-in, progress 0.5→1.0)
    val transitionInfo = remember(currentPositionMs, videoClips) {
        for (i in videoClips.indices) {
            val clip = videoClips[i]
            val type = clip.entryTransitionType
            val duration = clip.entryTransitionDurationMs ?: 800L
            if (type == null || type == "CUT" || duration <= 0) continue
            val halfDur = duration / 2

            // Phase 2: first half of this clip → fade-in (progress 0.5→1.0)
            val fadeInEnd = clip.startTimeMs + halfDur
            if (currentPositionMs >= clip.startTimeMs && currentPositionMs < fadeInEnd) {
                val elapsed = currentPositionMs - clip.startTimeMs
                val progress = 0.5f + (elapsed.toFloat() / halfDur) * 0.5f
                return@remember type to progress.coerceIn(0f, 1f)
            }

            // Phase 1: last half of previous clip → fade-out (progress 0→0.5)
            if (i > 0) {
                val prevClip = videoClips[i - 1]
                val fadeOutStart = prevClip.endTimeMs - halfDur
                if (currentPositionMs >= fadeOutStart && currentPositionMs < prevClip.endTimeMs) {
                    val elapsed = currentPositionMs - fadeOutStart
                    val progress = (elapsed.toFloat() / halfDur) * 0.5f
                    return@remember type to progress.coerceIn(0f, 1f)
                }
            }
        }
        null
    }

    if (transitionInfo != null) {
        val (type, progress) = transitionInfo
        val upper = type.uppercase()
        when {
            upper == "FADE" -> FadeTransitionEffect(progress)
            upper == "DISSOLVE" -> DissolveTransitionEffect(progress)
            upper.startsWith("SLIDE") -> SlideTransitionEffect(progress)
            upper.startsWith("WIPE") -> WipeTransitionEffect(progress)
        }
    }
}

@Composable
private fun FadeTransitionEffect(progress: Float) {
    // 0→0.5: fade to black (alpha 0→1), 0.5→1: fade from black (alpha 1→0)
    val alpha = if (progress <= 0.5f) {
        (progress / 0.5f).coerceIn(0f, 1f)
    } else {
        ((1f - progress) / 0.5f).coerceIn(0f, 1f)
    }
    if (alpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha))
        )
    }
}

@Composable
private fun DissolveTransitionEffect(progress: Float) {
    // 0→0.5: white flash increases, 0.5→1: white flash decreases
    val alpha = if (progress <= 0.5f) {
        (progress / 0.5f).coerceIn(0f, 1f) * 0.6f
    } else {
        ((1f - progress) / 0.5f).coerceIn(0f, 1f) * 0.6f
    }
    if (alpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha))
        )
    }
}

@Composable
private fun SlideTransitionEffect(progress: Float) {
    // 0→0.5: black curtain slides in from right, 0.5→1: slides out to left
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val offsetPx = if (progress <= 0.5f) {
            widthPx * (1f - progress / 0.5f) // slides in from right: width→0
        } else {
            -widthPx * ((progress - 0.5f) / 0.5f) // slides out to left: 0→-width
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .background(Color.Black)
        )
    }
}

@Composable
private fun WipeTransitionEffect(progress: Float) {
    // 0→0.5: wipe closes from right, 0.5→1: wipe opens from left
    val coverFraction = if (progress <= 0.5f) {
        (progress / 0.5f).coerceIn(0f, 1f) // 0→1 (closing)
    } else {
        ((1f - progress) / 0.5f).coerceIn(0f, 1f) // 1→0 (opening)
    }
    if (coverFraction > 0.01f) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = coverFraction)
                    .align(if (progress <= 0.5f) Alignment.CenterEnd else Alignment.CenterStart)
                    .background(Color.Black)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. ClipActionBar — mute, duplicate, delete, undo, redo
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipActionBar(
    isMuted: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onToggleMute: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        ActionChip(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                   else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "Unmute" else "Mute",
            onClick = onToggleMute,
            tint = if (isMuted) Color.White.copy(alpha = 0.4f) else Color.White
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

        Spacer(Modifier.width(4.dp))

        ActionChip(
            icon = Icons.AutoMirrored.Filled.Undo,
            label = "Undo",
            onClick = onUndo,
            enabled = canUndo,
            tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.2f)
        )
        ActionChip(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = "Redo",
            onClick = onRedo,
            enabled = canRedo,
            tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.2f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.7f)
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = tint)
            Text(
                label,
                fontSize = 9.sp,
                color = tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5b. Scroll ↔ time mapping helpers (accounts for transition gaps between clips)
// ═════════════════════════════════════════════════════════════════════════════

/** Convert scroll pixel offset → timeline time (ms), skipping over inter-clip gaps. */
private fun scrollPxToTime(
    scrollPx: Int,
    clips: List<TimelineClipState>,
    pxPerMs: Float,
    gapPx: Float
): Long {
    if (clips.isEmpty()) return 0L
    var accumulated = 0f
    for ((i, clip) in clips.withIndex()) {
        val clipWidthPx = (clip.endTimeMs - clip.startTimeMs) * pxPerMs
        if (scrollPx <= accumulated + clipWidthPx) {
            return clip.startTimeMs + ((scrollPx - accumulated) / pxPerMs).toLong()
                .coerceAtLeast(clip.startTimeMs)
        }
        accumulated += clipWidthPx
        if (i < clips.size - 1) {
            if (scrollPx <= accumulated + gapPx) {
                return clip.endTimeMs // inside gap → snap to end of clip
            }
            accumulated += gapPx
        }
    }
    return clips.last().endTimeMs
}

/** Convert timeline time (ms) → scroll pixel offset, accounting for gaps. */
private fun timeToScrollPx(
    timeMs: Long,
    clips: List<TimelineClipState>,
    pxPerMs: Float,
    gapPx: Float
): Int {
    if (clips.isEmpty()) return 0
    var accumulated = 0f
    for ((i, clip) in clips.withIndex()) {
        if (timeMs <= clip.endTimeMs) {
            val offsetInClip = (timeMs.coerceAtLeast(clip.startTimeMs) - clip.startTimeMs) * pxPerMs
            return (accumulated + offsetInClip).roundToInt()
        }
        accumulated += (clip.endTimeMs - clip.startTimeMs) * pxPerMs
        if (i < clips.size - 1) accumulated += gapPx
    }
    return accumulated.roundToInt()
}

// ═════════════════════════════════════════════════════════════════════════════
// 6. FrameTimeline — CapCut-style center-fixed playhead with scrollable clips
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun FrameTimeline(
    videoClips: List<TimelineClipState>,
    mediaItems: List<MediaItemEntity>,
    selectedClipId: UUID?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isImporting: Boolean,
    onSelectClip: (UUID?) -> Unit,
    onBeginTrimDrag: (UUID) -> Unit,
    onTrimDrag: (UUID, Long, Long) -> Unit,
    onCommitTrimDrag: (UUID) -> Unit,
    onSetEntryTransition: (UUID, String?, Long?) -> Unit,
    onSeek: (Long) -> Unit,
    onAddClips: () -> Unit
) {
    val mediaMap = remember(mediaItems) { mediaItems.associateBy { it.id } }
    val scrollState = rememberScrollState()
    var transitionPopupClipId by remember { mutableStateOf<UUID?>(null) }
    val density = LocalDensity.current
    val pxPerMs = with(density) { DP_PER_SECOND * density.density / 1000f }
    var isTrimming by remember { mutableStateOf(false) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    val gapPx = with(density) { TRANSITION_GAP_DP * density.density }

    // Keep a fresh ref for callbacks used inside pointer handlers
    val currentVideoClips by rememberUpdatedState(videoClips)

    // Sync playback position → scroll (only when not trimming and user not scrolling)
    // Uses snapshotFlow with debounce to avoid reacting to brief position glitches
    // during media source rebuilds.
    LaunchedEffect(Unit) {
        snapshotFlow { currentPositionMs }
            .collect { posMs ->
                if (!isTrimming && !scrollState.isScrollInProgress) {
                    isAutoScrolling = true
                    val targetPx = timeToScrollPx(posMs, currentVideoClips, pxPerMs, gapPx)
                        .coerceIn(0, scrollState.maxValue)
                    scrollState.scrollTo(targetPx)
                    isAutoScrolling = false
                }
            }
    }

    // Sync user scroll → seek position (scroll timeline = scrub)
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.isScrollInProgress to scrollState.value }
            .collect { (isScrolling, scrollPx) ->
                if (isScrolling && !isTrimming && !isAutoScrolling) {
                    val ms = scrollPxToTime(scrollPx, currentVideoClips, pxPerMs, gapPx)
                    onSeek(ms)
                }
            }
    }

    // Trim drag handler: left-handle adjusts scroll to keep right side visually fixed
    val handleTrimDrag: (UUID, Long, Long) -> Unit = { clipId, newStart, newEnd ->
        val clip = currentVideoClips.find { it.id == clipId }
        if (clip != null) {
            val startDelta = newStart - clip.startTimeMs
            onTrimDrag(clipId, newStart, newEnd)
            // Left-handle trim: compensate scroll so right side stays visually fixed
            if (startDelta != 0L) {
                scrollState.dispatchRawDelta(-startDelta * pxPerMs)
            }
        } else {
            onTrimDrag(clipId, newStart, newEnd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBg)
    ) {
        // Header
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
            if (videoClips.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                val totalMs = videoClips.maxOfOrNull { it.endTimeMs } ?: 0L
                Text(
                    "${videoClips.size} clip${if (videoClips.size != 1) "s" else ""} · ${formatDurationMs(totalMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // Clip area with center-fixed playhead
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            val halfContainerDp = maxWidth / 2

            if (videoClips.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        onClick = onAddClips,
                        shape = RoundedCornerShape(12.dp),
                        color = AccentBlue.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, AccentBlue.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AccentBlue
                            )
                            Text(
                                "Add Video Clips",
                                color = AccentBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .horizontalScroll(scrollState),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start padding = half screen (playhead at center = time 0)
                    Spacer(Modifier.width(halfContainerDp))

                    videoClips.forEachIndexed { index, clip ->
                        // Transition button BETWEEN clips (sibling in Row, proper z-order)
                        if (index > 0) {
                            val hasTransition =
                                clip.entryTransitionType != null && clip.entryTransitionType != "CUT"
                            Box(
                                modifier = Modifier
                                    .width(TRANSITION_GAP_DP.dp)
                                    .height(56.dp)
                                    .zIndex(10f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedClipId == null) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hasTransition) AccentBlue
                                                else Color(0xFF2A2A2E)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (hasTransition) AccentBlue
                                                else Color.White.copy(alpha = 0.3f),
                                                CircleShape
                                            )
                                            .clickable {
                                                transitionPopupClipId =
                                                    if (transitionPopupClipId == clip.id) null
                                                    else clip.id
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (hasTransition) {
                                            Text(
                                                clip.entryTransitionType?.take(1) ?: "T",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Add transition",
                                                modifier = Modifier.size(14.dp),
                                                tint = Color.White.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                TransitionPickerPopup(
                                    expanded = transitionPopupClipId == clip.id,
                                    currentType = clip.entryTransitionType,
                                    onDismiss = { transitionPopupClipId = null },
                                    onSelect = { type, dur ->
                                        onSetEntryTransition(clip.id, type, dur)
                                        transitionPopupClipId = null
                                    }
                                )
                            }
                        }

                        val mediaItem = clip.mediaItemId?.let { mediaMap[it] }
                        FrameClipBlock(
                            clip = clip,
                            mediaItem = mediaItem,
                            isSelected = clip.id == selectedClipId,
                            onSelect = {
                                transitionPopupClipId = null
                                onSelectClip(
                                    if (clip.id == selectedClipId) null else clip.id
                                )
                            },
                            onBeginTrimDrag = { id ->
                                isTrimming = true
                                onBeginTrimDrag(id)
                            },
                            onTrimDrag = handleTrimDrag,
                            onCommitTrimDrag = { id ->
                                onCommitTrimDrag(id)
                                val ms = scrollPxToTime(scrollState.value, currentVideoClips, pxPerMs, gapPx)
                                onSeek(ms)
                                isTrimming = false
                            }
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Add clip button at end
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.5.dp,
                                Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isImporting, onClick = onAddClips),
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

                    // End padding = half screen
                    Spacer(Modifier.width(halfContainerDp))
                }

                // Center-fixed playhead line (always at center, does not scroll)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(PlayheadRed)
                        .zIndex(5f)
                )

                // Time indicator at playhead
                Text(
                    formatDurationMs(currentPositionMs),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp)
                        .background(PlayheadRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .zIndex(6f),
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7. FrameClipBlock — individual clip with frame strip and trim handles
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun FrameClipBlock(
    clip: TimelineClipState,
    mediaItem: MediaItemEntity?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onBeginTrimDrag: (UUID) -> Unit,
    onTrimDrag: (UUID, Long, Long) -> Unit,
    onCommitTrimDrag: (UUID) -> Unit
) {
    val clipDurationMs = clip.endTimeMs - clip.startTimeMs
    val widthDp = (clipDurationMs / 1000f * DP_PER_SECOND).dp.coerceAtLeast(24.dp)
    val borderColor = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.12f)
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val density = LocalDensity.current

    val currentClip by rememberUpdatedState(clip)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnBeginTrimDrag by rememberUpdatedState(onBeginTrimDrag)
    val currentOnTrimDrag by rememberUpdatedState(onTrimDrag)
    val currentOnCommitTrimDrag by rememberUpdatedState(onCommitTrimDrag)

    val mediaDurationMs = mediaItem?.durationMs ?: Long.MAX_VALUE

    val path = mediaItem?.let { it.localCopyPath.ifBlank { it.sourcePath } }
    val frames = if (path != null && clipDurationMs > 0) {
        rememberClipFrames(
            uri = path,
            durationMs = clipDurationMs,
            trimStartMs = clip.trimStartMs,
            trimEndMs = clip.trimEndMs
        )
    } else {
        emptyList()
    }

    val handleWidth = 16.dp

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .background(clip.color.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures { currentOnSelect() }
            }
    ) {
        // Frame strip
        if (frames.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxSize()) {
                frames.forEach { frame ->
                    if (frame != null) {
                        Image(
                            bitmap = frame,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(clip.color.copy(alpha = 0.2f))
                        )
                    }
                }
            }
        }

        // Label overlay
        Text(
            clip.label,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = handleWidth + 2.dp, bottom = 2.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Duration overlay
        Text(
            formatDurationMs(clipDurationMs),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = handleWidth + 2.dp, bottom = 2.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 8.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        // Left trim handle — only active when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(4f)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(AccentBlue.copy(alpha = 0.5f))
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { currentOnBeginTrimDrag(currentClip.id) },
                            onDragEnd = { currentOnCommitTrimDrag(currentClip.id) },
                            onDragCancel = { currentOnCommitTrimDrag(currentClip.id) },
                            onHorizontalDrag = { _, dragAmount ->
                                val c = currentClip
                                val deltaMs = with(density) {
                                    (dragAmount / density.density / DP_PER_SECOND * 1000f).toLong()
                                }
                                val minStart = c.endTimeMs - (mediaDurationMs - c.trimEndMs)
                                val maxStart = c.endTimeMs - 500L
                                val newStart = (c.startTimeMs + deltaMs).coerceIn(
                                    minStart, maxStart
                                )
                                currentOnTrimDrag(c.id, newStart, c.endTimeMs)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        // Right trim handle — only active when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .zIndex(4f)
                    .width(handleWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(AccentBlue.copy(alpha = 0.5f))
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { currentOnBeginTrimDrag(currentClip.id) },
                            onDragEnd = { currentOnCommitTrimDrag(currentClip.id) },
                            onDragCancel = { currentOnCommitTrimDrag(currentClip.id) },
                            onHorizontalDrag = { _, dragAmount ->
                                val c = currentClip
                                val deltaMs = with(density) {
                                    (dragAmount / density.density / DP_PER_SECOND * 1000f).toLong()
                                }
                                val maxEnd = c.startTimeMs + (mediaDurationMs - c.trimStartMs)
                                val minEnd = c.startTimeMs + 500L
                                val newEnd = (c.endTimeMs + deltaMs).coerceIn(minEnd, maxEnd)
                                currentOnTrimDrag(c.id, c.startTimeMs, newEnd)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 8. TransitionPickerPopup — dropdown with transition types
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TransitionPickerPopup(
    expanded: Boolean,
    currentType: String?,
    onDismiss: () -> Unit,
    onSelect: (String?, Long?) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        transitionOptions.forEach { option ->
            val isActive = when {
                option.type == null && (currentType == null || currentType == "CUT") -> true
                option.type != null && option.type == currentType -> true
                else -> false
            }
            DropdownMenuItem(
                text = {
                    Text(
                        option.label,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) AccentBlue else Color.Unspecified
                    )
                },
                onClick = {
                    onSelect(option.type, if (option.type != null) 800L else null)
                    onDismiss()
                }
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 10. TimelineScrubber — time display with seek
// ═════════════════════════════════════════════════════════════════════════════

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
            .background(SurfaceBg)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = { frac -> onSeek((frac * totalDurationMs).toLong()) },
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

// ═════════════════════════════════════════════════════════════════════════════
// 11. EffectPresetRow — horizontal row of 8 effect cards
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun EffectPresetRow(
    clip: TimelineClipState,
    mediaItem: MediaItemEntity?,
    onApplyPreset: (brightness: Float, contrast: Float, saturation: Float) -> Unit
) {
    val context = LocalContext.current
    val uri = mediaItem?.let { it.localCopyPath.ifBlank { it.sourcePath } }

    // Extract a single representative frame for effect previews
    var previewFrame by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val parsedUri = if (uri.startsWith("/")) Uri.fromFile(java.io.File(uri))
                                else Uri.parse(uri)
                retriever.setDataSource(context, parsedUri)
                previewFrame = retriever.getFrameAtTime(
                    clip.trimStartMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.asImageBitmap()
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        effectPresets.forEach { preset ->
            val isActive = clip.brightness == preset.brightness &&
                clip.contrast == preset.contrast &&
                clip.saturation == preset.saturation

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable {
                        onApplyPreset(preset.brightness, preset.contrast, preset.saturation)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp, 52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            if (isActive) 2.dp else 1.dp,
                            if (isActive) AccentBlue else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(CardBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewFrame != null) {
                        Image(
                            bitmap = previewFrame!!,
                            contentDescription = preset.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            colorFilter = ColorFilter.colorMatrix(
                                effectColorMatrix(
                                    preset.brightness,
                                    preset.contrast,
                                    preset.saturation
                                )
                            )
                        )
                    } else {
                        Text(
                            preset.name.take(1),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    preset.name,
                    fontSize = 10.sp,
                    color = if (isActive) AccentBlue else Color.White.copy(alpha = 0.6f),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Helper: rememberClipFrames — extract video frames for timeline thumbnails
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun rememberClipFrames(
    uri: String,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    frameCount: Int = 4
): List<ImageBitmap?> {
    val context = LocalContext.current
    var frames by remember(uri, durationMs, trimStartMs, trimEndMs) {
        mutableStateOf<List<ImageBitmap?>>(emptyList())
    }

    LaunchedEffect(uri, durationMs, trimStartMs, trimEndMs) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val parsedUri = if (uri.startsWith("/")) Uri.fromFile(java.io.File(uri))
                                else Uri.parse(uri)
                retriever.setDataSource(context, parsedUri)
                val effectiveDuration = durationMs.coerceAtLeast(1L)
                val step = if (frameCount > 1) effectiveDuration / frameCount else effectiveDuration
                val extracted = (0 until frameCount).map { i ->
                    val timeUs = (trimStartMs + i * step) * 1000L
                    try {
                        retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )?.asImageBitmap()
                    } catch (_: Exception) { null }
                }
                frames = extracted
            } catch (_: Exception) {
                frames = List(frameCount) { null }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// Helper: formatDurationMs
// ═════════════════════════════════════════════════════════════════════════════

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

// ═════════════════════════════════════════════════════════════════════════════
// Helper: effectColorMatrix — builds a ColorMatrix for brightness/contrast/sat
// ═════════════════════════════════════════════════════════════════════════════

private fun effectColorMatrix(
    brightness: Float,
    contrast: Float,
    saturation: Float
): ColorMatrix {
    val cm = ColorMatrix()
    cm.setToSaturation(saturation)

    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, 0.5f * (1f - contrast),
            0f, contrast, 0f, 0f, 0.5f * (1f - contrast),
            0f, 0f, contrast, 0f, 0.5f * (1f - contrast),
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm *= contrastMatrix

    val brightnessMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )
    )
    cm *= brightnessMatrix

    return cm
}
