package com.gpxvideo.feature.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.ClipSyncPoint
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.overlayrenderer.TemplateInfo
import com.gpxvideo.core.overlayrenderer.LottieOverlayRenderer
import com.gpxvideo.core.overlayrenderer.LottieTemplateLoader
import com.gpxvideo.core.overlayrenderer.LoadedTemplate
import com.gpxvideo.core.overlayrenderer.OverlayFrameData
import com.gpxvideo.lib.gpxparser.GpxStats
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.core.common.FormatUtils
import com.gpxvideo.core.ui.theme.AthleticCondensed
import com.gpxvideo.core.ui.theme.AthleticType
import com.gpxvideo.feature.preview.VideoPreview
import java.util.UUID

// ── Theme constants ──────────────────────────────────────────────────────
private val DarkBg = Color(0xFF0D0D12)
private val CardBg = Color(0xFF1A1A2E)
private val SurfaceBg = Color(0xFF16162A)
private val AccentBlue = Color(0xFF448AFF)

/**
 * Screen 2: "The Magic" — Style & Telemetry
 * Live video preview with overlay templates, timeline scrubber, sync modes, and export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleTelemetryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectIdStr = uiState.project?.id?.toString() ?: ""
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val videoDuration by viewModel.videoDuration.collectAsStateWithLifecycle()

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showTitleEditor by rememberSaveable { mutableStateOf(false) }
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showSyncSheet by rememberSaveable { mutableStateOf(false) }

    val playbackProgress by remember {
        derivedStateOf {
            if (videoDuration > 0) (currentPositionMs.toFloat() / videoDuration).coerceIn(0f, 1f)
            else 0f
        }
    }

    val sportType = uiState.project?.sportType ?: "CYCLING"
    val isRunning = sportType.uppercase().let { it == "RUNNING" || it == "TRAIL_RUNNING" || it == "HIKING" }

    // Live GPX interpolation
    val liveGpxValues by remember(uiState.gpxData, playbackProgress, uiState.storyMode, uiState.gpxStats, uiState.clipSyncPoints, currentPositionMs) {
        derivedStateOf {
            interpolateGpxAtProgress(
                gpxData = uiState.gpxData,
                gpxStats = uiState.gpxStats,
                progress = playbackProgress,
                storyMode = uiState.storyMode,
                clipSyncPoints = uiState.clipSyncPoints,
                mediaItems = uiState.mediaItems.filter { it.type == "VIDEO" },
                currentPositionMs = currentPositionMs,
                totalDurationMs = videoDuration
            )
        }
    }

    // Reload timeline-aware clips when entering this screen
    LaunchedEffect(Unit) {
        viewModel.reloadTimelinePreview()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    if (uiState.isLoading) {
        com.gpxvideo.core.ui.component.LoadingIndicator()
        return
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            StyleTopBar(
                selectedRatio = uiState.selectedAspectRatio,
                showAspectRatioMenu = showAspectRatioMenu,
                onToggleAspectRatioMenu = { showAspectRatioMenu = !showAspectRatioMenu },
                onAspectRatioSelected = {
                    viewModel.setAspectRatio(it)
                    showAspectRatioMenu = false
                },
                onNavigateBack = onNavigateBack,
                onColorPickerClick = { showColorPicker = true },
                onInfoClick = { showInfoSheet = true },
                hasGpxData = uiState.gpxData != null
            )
        },
        bottomBar = {
            StyleBottomBar(
                onExport = {
                    if (projectIdStr.isNotBlank()) onNavigateToExport(projectIdStr)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Template preview with video background (swipeable)
            TemplatePreviewSection(
                uiState = uiState,
                aspectRatio = uiState.selectedAspectRatio,
                accentColor = Color(uiState.accentColor),
                activityTitle = uiState.activityTitle,
                onTemplateSelected = viewModel::setStoryTemplate,
                onTitleClick = { showTitleEditor = true },
                previewEngine = viewModel.previewEngine,
                isPlaying = isPlaying,
                playbackProgress = playbackProgress,
                onTogglePlayback = viewModel::togglePlayback,
                liveGpxValues = liveGpxValues,
                storyMode = uiState.storyMode,
                isRunning = isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Sync toggle (3 modes)
            SyncToggle(
                selectedMode = uiState.storyMode,
                onModeSelected = { mode ->
                    viewModel.setStoryMode(mode)
                    if (mode == StoryMode.LIVE_SYNC.name) {
                        showSyncSheet = true
                    }
                },
                hasGpxData = uiState.gpxData != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Mini scrubber timeline
            StyleMiniScrubber(
                currentPositionMs = currentPositionMs,
                totalDurationMs = videoDuration,
                onSeek = viewModel::seekTo,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showColorPicker) {
        ColorPickerSheet(
            currentColor = uiState.accentColor,
            onColorSelected = { viewModel.setAccentColor(it) },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showTitleEditor) {
        TitleEditorDialog(
            currentTitle = uiState.activityTitle,
            onTitleChanged = { viewModel.setActivityTitle(it) },
            onDismiss = { showTitleEditor = false }
        )
    }

    if (showInfoSheet) {
        ActivityInfoBottomSheet(
            gpxStats = uiState.gpxStats,
            gpxData = uiState.gpxData,
            mediaItems = uiState.mediaItems,
            sportType = sportType,
            isRunning = isRunning,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showSyncSheet) {
        LiveSyncConfigSheet(
            gpxData = uiState.gpxData,
            mediaItems = uiState.mediaItems.filter { it.type == "VIDEO" },
            clipSyncPoints = uiState.clipSyncPoints,
            onSetSyncPoint = { clipId, syncPoint -> viewModel.setClipSyncPoint(clipId, syncPoint) },
            onDismiss = { showSyncSheet = false }
        )
    }
}

// ── Live GPX Interpolation ───────────────────────────────────────────────

data class LiveGpxValues(
    val distance: Double = 0.0,
    val elevation: Double = 0.0,
    val speed: Double = 0.0,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val temperature: Double? = null,
    val grade: Double = 0.0,
    val elapsedTime: Long = 0L,
    val gpxTimestamp: java.time.Instant? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gpxProgress: Float = 0f // Progress along the GPX track (0-1), used for chart/map positioning
)

private fun interpolateGpxAtProgress(
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    progress: Float,
    storyMode: String,
    clipSyncPoints: Map<java.util.UUID, ClipSyncPoint> = emptyMap(),
    mediaItems: List<com.gpxvideo.core.database.entity.MediaItemEntity> = emptyList(),
    currentPositionMs: Long = 0L,
    totalDurationMs: Long = 0L
): LiveGpxValues {
    if (gpxData == null) return LiveGpxValues()
    val points = gpxData.tracks.flatMap { it.segments }.flatMap { it.points }
    if (points.isEmpty()) return LiveGpxValues()

    // Static mode: use GpxStats (moving-time-based, matches Activity Info)
    if (storyMode == StoryMode.STATIC.name) {
        val avgSpeed = gpxStats?.avgSpeed
            ?: if (gpxData.totalDuration.seconds > 0) gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
        val avgHr = points.mapNotNull { it.heartRate }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgCad = points.mapNotNull { it.cadence }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgPow = points.mapNotNull { it.power }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgTemp = points.mapNotNull { it.temperature }.takeIf { it.isNotEmpty() }?.average()
        return LiveGpxValues(
            distance = gpxData.totalDistance,
            elevation = gpxStats?.totalElevationGain ?: gpxData.totalElevationGain,
            speed = avgSpeed,
            heartRate = avgHr,
            cadence = avgCad,
            power = avgPow,
            temperature = avgTemp,
            grade = 0.0,
            elapsedTime = (gpxStats?.movingDuration ?: gpxData.totalDuration).toMillis(),
            gpxProgress = 1f // Static mode shows final totals, full progress
        )
    }

    // For LIVE_SYNC: find the GPX point index based on clip sync mapping
    val idx: Int
    if (storyMode == StoryMode.LIVE_SYNC.name && clipSyncPoints.isNotEmpty() && mediaItems.isNotEmpty()) {
        idx = resolveliveSyncPointIndex(
            points, clipSyncPoints, mediaItems, currentPositionMs, totalDurationMs, gpxData.totalDistance
        )
    } else {
        // FAST_FORWARD: proportional mapping
        idx = ((progress * (points.size - 1)).toInt()).coerceIn(0, points.lastIndex)
    }

    val gpxProgress = idx.toFloat() / (points.size - 1).coerceAtLeast(1).toFloat()
    return computeLiveValuesAtIndex(gpxData, points, idx, gpxProgress)
}

/** For Live Sync: resolve which GPX point index corresponds to the current playback position. */
private fun resolveliveSyncPointIndex(
    points: List<GpxPoint>,
    clipSyncPoints: Map<java.util.UUID, ClipSyncPoint>,
    mediaItems: List<com.gpxvideo.core.database.entity.MediaItemEntity>,
    currentPositionMs: Long,
    totalDurationMs: Long,
    totalDistance: Double
): Int {
    if (mediaItems.isEmpty() || points.isEmpty()) return 0

    // Build a timeline of clip start/end positions in ms
    var cumulativeMs = 0L
    var activeClipIndex = -1
    var clipStartMs = 0L

    for (i in mediaItems.indices) {
        val clipDuration = mediaItems[i].durationMs ?: 0L
        if (currentPositionMs < cumulativeMs + clipDuration) {
            activeClipIndex = i
            clipStartMs = cumulativeMs
            break
        }
        cumulativeMs += clipDuration
    }
    if (activeClipIndex < 0) activeClipIndex = mediaItems.lastIndex

    val activeClip = mediaItems[activeClipIndex]
    val clipDuration = activeClip.durationMs ?: 1L
    val positionWithinClip = (currentPositionMs - clipStartMs).coerceAtLeast(0L)
    val clipProgress = (positionWithinClip.toFloat() / clipDuration).coerceIn(0f, 1f)

    val syncPoint = clipSyncPoints[activeClip.id]
    if (syncPoint == null || !syncPoint.isSynced) {
        // Not synced — fall back to proportional
        return ((currentPositionMs.toFloat() / totalDurationMs.coerceAtLeast(1L)) * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)
    }

    val startIdx = syncPoint.gpxPointIndex.coerceIn(0, points.lastIndex)

    // Determine how many GPX points this clip spans using timestamps
    val startTime = points[startIdx].time
    if (startTime != null && clipDuration > 0) {
        val clipEndTime = startTime.plusMillis(clipDuration)
        // Find the index closest to clipEndTime
        var endIdx = startIdx
        for (i in startIdx until points.size) {
            if (points[i].time != null && points[i].time!! <= clipEndTime) {
                endIdx = i
            } else break
        }
        if (endIdx <= startIdx) endIdx = (startIdx + 1).coerceAtMost(points.lastIndex)
        return (startIdx + (clipProgress * (endIdx - startIdx)).toInt()).coerceIn(0, points.lastIndex)
    }

    // Fallback: estimate span by distance
    val clipDistanceMeters = (clipDuration.toDouble() / 1000.0) * (totalDistance / (totalDurationMs.coerceAtLeast(1L).toDouble() / 1000.0))
    val endDist = syncPoint.gpxDistanceMeters + clipDistanceMeters
    var endIdx = startIdx
    for (i in startIdx until points.size) {
        val pointDist = totalDistance * i / points.size
        if (pointDist <= endDist) endIdx = i else break
    }
    return (startIdx + (clipProgress * (endIdx - startIdx)).toInt()).coerceIn(0, points.lastIndex)
}

/** Compute all live values at a given GPX point index. */
private fun computeLiveValuesAtIndex(
    gpxData: GpxData,
    points: List<GpxPoint>,
    idx: Int,
    progress: Float
): LiveGpxValues {
    val point = points[idx]

    // Cumulative distance up to this point
    var distanceSoFar = 0.0
    for (i in 1..idx) {
        distanceSoFar += GpxStatistics.computeDistance(
            points[i - 1].latitude, points[i - 1].longitude,
            points[i].latitude, points[i].longitude
        )
    }

    // Accumulated vertical gain up to this point
    var cumulativeGain = 0.0
    for (i in 1..idx) {
        val diff = (points[i].elevation ?: 0.0) - (points[i - 1].elevation ?: 0.0)
        if (diff > 0) cumulativeGain += diff
    }

    // Speed: compute from actual point-to-point distances (not the GPX speed field)
    val windowSize = (points.size / 50).coerceIn(3, 15)
    val windowStart = (idx - windowSize).coerceAtLeast(0)
    val windowEnd = (idx + windowSize).coerceAtMost(points.lastIndex)
    val speed: Double = run {
        val startTime = points[windowStart].time
        val endTime = points[windowEnd].time
        if (startTime != null && endTime != null) {
            val timeDiffSec = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000.0
            if (timeDiffSec > 1.0) {
                var windowDist = 0.0
                for (i in windowStart + 1..windowEnd) {
                    windowDist += GpxStatistics.computeDistance(
                        points[i - 1].latitude, points[i - 1].longitude,
                        points[i].latitude, points[i].longitude
                    )
                }
                windowDist / timeDiffSec
            } else 0.0
        } else {
            // No time data — use average speed
            if (gpxData.totalDuration.seconds > 0)
                gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
        }
    }

    // Grade from neighboring points
    val grade = if (idx > 0 && idx < points.lastIndex) {
        val dist = GpxStatistics.computeDistance(
            points[idx - 1].latitude, points[idx - 1].longitude,
            points[idx + 1].latitude, points[idx + 1].longitude
        )
        if (dist > 1.0) {
            val elevChange = (points[idx + 1].elevation ?: 0.0) - (points[idx - 1].elevation ?: 0.0)
            (elevChange / dist * 100.0)
        } else 0.0
    } else 0.0

    // Elapsed time from GPX timestamps or proportional fallback
    val elapsedTime = if (points.first().time != null && point.time != null) {
        java.time.Duration.between(points.first().time, point.time).toMillis()
    } else {
        (gpxData.totalDuration.toMillis() * progress).toLong()
    }

    return LiveGpxValues(
        distance = distanceSoFar,
        elevation = cumulativeGain,
        speed = speed,
        heartRate = point.heartRate,
        cadence = point.cadence,
        power = point.power,
        temperature = point.temperature,
        grade = grade,
        elapsedTime = elapsedTime,
        gpxTimestamp = point.time,
        latitude = point.latitude,
        longitude = point.longitude,
        gpxProgress = progress
    )
}

// ── Top Bar ──────────────────────────────────────────────────────────────

@Composable
private fun StyleTopBar(
    selectedRatio: SocialAspectRatio,
    showAspectRatioMenu: Boolean,
    onToggleAspectRatioMenu: () -> Unit,
    onAspectRatioSelected: (SocialAspectRatio) -> Unit,
    onNavigateBack: () -> Unit,
    onColorPickerClick: () -> Unit,
    onInfoClick: () -> Unit,
    hasGpxData: Boolean
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = "Style & Overlays",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
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

            if (hasGpxData) {
                IconButton(onClick = onInfoClick) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            IconButton(onClick = onColorPickerClick) {
                Icon(Icons.Default.Palette, contentDescription = "Accent Color", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

// ── Bottom Bar ───────────────────────────────────────────────────────────

@Composable
private fun StyleBottomBar(onExport: () -> Unit) {
    Surface(color = DarkBg, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Button(
                onClick = onExport,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export Story", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ── Template Preview with Swipe ──────────────────────────────────────────

@Composable
private fun TemplatePreviewSection(
    uiState: ProjectEditorUiState,
    aspectRatio: SocialAspectRatio,
    accentColor: Color,
    activityTitle: String,
    onTemplateSelected: (String) -> Unit,
    onTitleClick: () -> Unit,
    previewEngine: com.gpxvideo.feature.preview.PreviewEngine,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlayback: () -> Unit,
    liveGpxValues: LiveGpxValues,
    storyMode: String,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { LottieTemplateLoader(context) }
    val templates = remember { loader.discoverTemplates() }
    val pagerState = rememberPagerState(
        initialPage = templates.indexOfFirst { it.id.equals(uiState.storyTemplate, ignoreCase = true) }.coerceAtLeast(0),
        pageCount = { templates.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onTemplateSelected(templates[pagerState.currentPage].id)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 0
        ) { page ->
            val isActivePage = pagerState.currentPage == page
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                StyleTemplateCard(
                    template = templates[page],
                    gpxData = uiState.gpxData,
                    aspectRatio = aspectRatio,
                    accentColor = accentColor,
                    activityTitle = activityTitle,
                    onTitleClick = onTitleClick,
                    previewEngine = previewEngine,
                    isActivePage = isActivePage,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    onTogglePlayback = onTogglePlayback,
                    liveGpxValues = liveGpxValues,
                    storyMode = storyMode,
                    isRunning = isRunning
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            templates[pagerState.currentPage].displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            templates.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) AccentBlue
                            else Color.White.copy(alpha = 0.2f)
                        )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun StyleTemplateCard(
    template: TemplateInfo,
    gpxData: GpxData?,
    aspectRatio: SocialAspectRatio,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    previewEngine: com.gpxvideo.feature.preview.PreviewEngine,
    isActivePage: Boolean,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlayback: () -> Unit,
    liveGpxValues: LiveGpxValues,
    storyMode: String,
    isRunning: Boolean
) {
    val cardAspectRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
    val isAnimated = storyMode != StoryMode.STATIC.name
    val isPortrait = aspectRatio == SocialAspectRatio.PORTRAIT_9_16 || aspectRatio == SocialAspectRatio.PORTRAIT_4_5
    val isLandscape = aspectRatio == SocialAspectRatio.LANDSCAPE_16_9

    Card(
        modifier = if (cardAspectRatio < 1f) {
            // Portrait ratios (9:16, 4:5): constrain height first so card fits vertically
            Modifier.aspectRatio(cardAspectRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier.fillMaxWidth().aspectRatio(cardAspectRatio)
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isActivePage) {
                VideoPreview(
                    previewEngine = previewEngine,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(CardBg, Color(0xFF16213E), Color(0xFF0F3460))))
                )
            }

            // Template overlay — rendered via Lottie
            LottieOverlayPreview(
                template = template,
                aspectRatio = aspectRatio,
                gpxData = gpxData,
                accentColor = accentColor,
                activityTitle = activityTitle,
                progress = playbackProgress,
                liveValues = liveGpxValues,
                isRunning = isRunning,
                onTitleClick = onTitleClick
            )

            // Play/pause overlay
            if (isActivePage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onTogglePlayback),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = AccentBlue,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
        }
    }
}

// ── Metric formatting ────────────────────────────────────────────────────

private fun formatMetricDistance(meters: Double): String = "%.1f".format(meters / 1000.0)
private fun formatMetricSpeed(metersPerSec: Double): String = "%.1f".format(metersPerSec * 3.6)
private fun formatMetricElevation(meters: Double): String = "%.0f".format(meters)

private fun formatPace(metersPerSec: Double) = FormatUtils.formatPaceFromSpeed(metersPerSec)
private fun formatGrade(grade: Double) = FormatUtils.formatGrade(grade)
private fun formatTemp(temp: Double?) = FormatUtils.formatTemp(temp)

/** Always show pace (min/km) as the primary speed metric. */
private fun speedOrPaceLabel(isRunning: Boolean): String = "PACE"
private fun speedOrPaceValue(metersPerSec: Double, isRunning: Boolean): String = formatPace(metersPerSec)
private fun speedOrPaceUnit(isRunning: Boolean): String = "min/km"

// ── Lottie Overlay Preview ────────────────────────────────────────────────

@Composable
private fun LottieOverlayPreview(
    template: TemplateInfo,
    aspectRatio: SocialAspectRatio,
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    progress: Float,
    liveValues: LiveGpxValues,
    isRunning: Boolean,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { LottieTemplateLoader(context) }
    val renderer = remember { LottieOverlayRenderer() }

    // Convert Compose Color to ARGB int
    val accentArgb = remember(accentColor) { accentColor.toArgb() }

    // Use actual output dimensions for template resolution
    val outputW = aspectRatio.width
    val outputH = aspectRatio.height

    var loadedTemplate by remember { mutableStateOf<LoadedTemplate?>(null) }

    // Load the Lottie composition using the project aspect ratio
    LaunchedEffect(template, aspectRatio) {
        loadedTemplate = loader.load(template.id, outputW, outputH)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clickable(onClick = onTitleClick)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx().toInt() }

        val tmpl = loadedTemplate
        if (tmpl != null && widthPx > 0 && heightPx > 0) {
            val frameData = remember(progress, liveValues) {
                OverlayFrameData(
                    distance = liveValues.distance,
                    elevation = liveValues.elevation,
                    elevationGain = liveValues.elevation,
                    speed = liveValues.speed,
                    pace = speedOrPaceValue(liveValues.speed, isRunning),
                    heartRate = liveValues.heartRate,
                    cadence = liveValues.cadence,
                    power = liveValues.power,
                    temperature = liveValues.temperature,
                    grade = liveValues.grade,
                    elapsedTime = liveValues.elapsedTime,
                    progress = liveValues.gpxProgress, // Use GPX track progress for chart/map positioning
                    latitude = liveValues.latitude,
                    longitude = liveValues.longitude
                )
            }

            val bitmap = remember(tmpl, widthPx, heightPx, frameData, activityTitle, accentArgb) {
                try {
                    renderer.render(
                        composition = tmpl.composition,
                        jsonString = tmpl.jsonString,
                        width = widthPx,
                        height = heightPx,
                        frameData = frameData,
                        gpxData = gpxData,
                        accentColor = accentArgb,
                        activityTitle = activityTitle
                    )
                } catch (e: Exception) {
                    android.util.Log.e("OverlayPreview", "Render failed for ${template.id} ${aspectRatio}: ${e.message}", e)
                    android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
                }
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Overlay",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── Sync Toggle (3 modes) ────────────────────────────────────────────────

@Composable
private fun SyncToggle(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    hasGpxData: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SyncToggleOption(
                label = "📊 Static",
                description = "Final totals",
                isSelected = selectedMode == StoryMode.STATIC.name,
                onClick = { onModeSelected(StoryMode.STATIC.name) },
                modifier = Modifier.weight(1f)
            )
            SyncToggleOption(
                label = "⚡ Fast Fwd",
                description = "Animated journey",
                isSelected = selectedMode == StoryMode.FAST_FORWARD.name,
                onClick = { onModeSelected(StoryMode.FAST_FORWARD.name) },
                modifier = Modifier.weight(1f)
            )
            SyncToggleOption(
                label = "🔗 Live Sync",
                description = if (hasGpxData) "Per-clip sync" else "Needs GPX",
                isSelected = selectedMode == StoryMode.LIVE_SYNC.name,
                enabled = hasGpxData,
                onClick = { if (hasGpxData) onModeSelected(StoryMode.LIVE_SYNC.name) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SyncToggleOption(
    label: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.4f
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) AccentBlue.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)) else null,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = (if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)).copy(alpha = alpha),
                maxLines = 1
            )
            Text(
                description, style = MaterialTheme.typography.labelSmall,
                color = (if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f)).copy(alpha = alpha),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 9.sp
            )
        }
    }
}

// ── Mini Scrubber (timeline bar for Style screen) ────────────────────────

@Composable
private fun StyleMiniScrubber(
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .background(SurfaceBg)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDurationMs(currentPositionMs),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
            Text(
                formatDurationMs(totalDurationMs),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.3f),
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(totalDurationMs) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((frac * totalDurationMs).toLong())
                    }
                }
                .pointerInput(totalDurationMs) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek((frac * totalDurationMs).toLong())
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(AccentBlue.copy(alpha = 0.7f))
            )
            // Thumb — positioned at end of filled fraction
            if (totalDurationMs > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceAtLeast(0.005f))
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentBlue)
                            .align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

// ── Color Picker Sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val presetColors = listOf(
        0xFF448AFF.toInt() to "Blue", 0xFF00C853.toInt() to "Green",
        0xFFFF6D00.toInt() to "Orange", 0xFFEF5350.toInt() to "Red",
        0xFFAB47BC.toInt() to "Purple", 0xFF26A69A.toInt() to "Teal",
        0xFFFFD600.toInt() to "Yellow", 0xFFEC407A.toInt() to "Pink",
        0xFFFFFFFF.toInt() to "White"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = CardBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()
        ) {
            Text("Accent Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
            Text("Choose the highlight color for your overlay", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presetColors.take(5).forEach { (color, name) ->
                    ColorSwatch(color, name, color == currentColor, { onColorSelected(color); onDismiss() }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                presetColors.drop(5).forEach { (color, name) ->
                    ColorSwatch(color, name, color == currentColor, { onColorSelected(color); onDismiss() }, Modifier.weight(1f))
                }
                repeat(5 - presetColors.drop(5).size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, name: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(color))
                .then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (color == 0xFFFFFFFF.toInt() || color == 0xFFFFD600.toInt()) Color.Black else Color.White)
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = if (isSelected) 0.8f else 0.4f), modifier = Modifier.padding(top = 4.dp), fontSize = 9.sp)
    }
}

// ── Title Editor ─────────────────────────────────────────────────────────

@Composable
private fun TitleEditorDialog(currentTitle: String, onTitleChanged: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Activity Title", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("e.g. Morning Ride, Trail Run…", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = AccentBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onTitleChanged(text); onDismiss() }) { Text("Save", color = AccentBlue, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.5f)) } }
    )
}

// ── Activity Info Bottom Sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityInfoBottomSheet(
    gpxStats: GpxStats?,
    gpxData: GpxData?,
    mediaItems: List<com.gpxvideo.core.database.entity.MediaItemEntity>,
    sportType: String,
    isRunning: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = CardBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
            val sportIcon = when (sportType.uppercase()) {
                "RUNNING", "TRAIL_RUNNING" -> Icons.Default.DirectionsRun
                "HIKING" -> Icons.Default.Hiking
                else -> Icons.Default.DirectionsBike
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(AccentBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(sportIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = AccentBlue)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Activity Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(sportType.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
                }
            }

            if (gpxStats != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoStatCard("Distance", "%.1f".format(gpxStats.totalDistance / 1000.0), "km", Modifier.weight(1f))
                    InfoStatCard("Elevation", "%.0f".format(gpxStats.totalElevationGain), "m ↑", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoStatCard("Duration", styleDuration(gpxStats.movingDuration.toMillis()), "", Modifier.weight(1f))
                    if (isRunning) {
                        val avgPace = if (gpxStats.avgSpeed > 0) formatPace(gpxStats.avgSpeed) else "—"
                        InfoStatCard("Avg Pace", avgPace, "min/km", Modifier.weight(1f))
                    } else {
                        InfoStatCard("Avg Speed", if (gpxStats.avgSpeed > 0) "%.1f".format(gpxStats.avgSpeed * 3.6) else "—", "km/h", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoStatCard("Clips", "${mediaItems.size}", "video${if (mediaItems.size != 1) "s" else ""}", Modifier.weight(1f))
                    if (gpxStats.maxSpeed > 0) {
                        if (isRunning) {
                            InfoStatCard("Best Pace", formatPace(gpxStats.maxSpeed), "min/km", Modifier.weight(1f))
                        } else {
                            InfoStatCard("Max Speed", "%.1f".format(gpxStats.maxSpeed * 3.6), "km/h", Modifier.weight(1f))
                        }
                    } else {
                        InfoStatCard("Elev Loss", "%.0f".format(gpxStats.totalElevationLoss), "m ↓", Modifier.weight(1f))
                    }
                }
                // Extra metrics if available
                val hrVal = gpxStats.avgHeartRate
                val cadVal = gpxStats.avgCadence
                val hasHr = hrVal != null && hrVal > 0
                val hasCad = cadVal != null && cadVal > 0
                if (hasHr || hasCad) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hasHr && hrVal != null) InfoStatCard("Avg HR", "%.0f".format(hrVal), "bpm", Modifier.weight(1f))
                        if (hasCad && cadVal != null) InfoStatCard("Avg Cadence", "%.0f".format(cadVal), if (isRunning) "spm" else "rpm", Modifier.weight(1f))
                        if (!hasHr || !hasCad) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                Text("No activity data loaded yet.\nImport a GPX file to see stats.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
            }
        }
    }
}

@Composable
private fun InfoStatCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.06f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                if (unit.isNotEmpty()) {
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

// ── Live Sync Config Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSyncConfigSheet(
    gpxData: GpxData?,
    mediaItems: List<com.gpxvideo.core.database.entity.MediaItemEntity>,
    clipSyncPoints: Map<UUID, ClipSyncPoint>,
    onSetSyncPoint: (UUID, ClipSyncPoint) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allPoints = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: emptyList()
    val totalDistance = gpxData?.totalDistance ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = CardBg,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Live Sync — Clip Alignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                "Map each video clip to a point on your GPX track. Drag the slider to set where on the route each clip was recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (allPoints.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFAB40), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No GPX data loaded. Import a GPX file first.", color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                // Collect clip fractions for map dots
                val clipFractions = mediaItems.mapIndexed { index, media ->
                    val syncPoint = clipSyncPoints[media.id]
                    val currentDist = syncPoint?.gpxDistanceMeters ?: (totalDistance * index / mediaItems.size.coerceAtLeast(1))
                    if (totalDistance > 0) (currentDist / totalDistance).toFloat().coerceIn(0f, 1f) else 0f
                }

                // Always show the route map at top with numbered clip dots
                ClipOverviewMap(
                    gpxData = gpxData,
                    accentColor = AccentBlue,
                    clipFractions = clipFractions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(bottom = 12.dp)
                )

                mediaItems.forEachIndexed { index, media ->
                    val clipId = media.id
                    val syncPoint = clipSyncPoints[clipId]
                    val isSynced = syncPoint?.isSynced == true
                    val currentDist = syncPoint?.gpxDistanceMeters ?: (totalDistance * index / mediaItems.size.coerceAtLeast(1))
                    val distFraction = if (totalDistance > 0) (currentDist / totalDistance).toFloat().coerceIn(0f, 1f) else 0f

                    ClipSyncRow(
                        clipIndex = index + 1,
                        clipName = media.localCopyPath.substringAfterLast("/").take(20),
                        durationMs = media.durationMs ?: 0L,
                        isSynced = isSynced,
                        distanceFraction = distFraction,
                        totalDistance = totalDistance,
                        gpxData = gpxData,
                        onDistanceChanged = { newFraction ->
                            val newDist = newFraction * totalDistance
                            val pointIdx = allPoints.indices.minByOrNull { i ->
                                val pointDist = totalDistance * i / allPoints.size
                                kotlin.math.abs(pointDist - newDist)
                            } ?: 0
                            onSetSyncPoint(clipId, ClipSyncPoint(
                                clipId = clipId,
                                gpxPointIndex = pointIdx,
                                gpxDistanceMeters = newDist.toDouble(),
                                isSynced = true
                            ))
                        }
                    )

                    if (index < mediaItems.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClipSyncRow(
    clipIndex: Int,
    clipName: String,
    durationMs: Long,
    isSynced: Boolean,
    distanceFraction: Float,
    totalDistance: Double,
    gpxData: GpxData?,
    onDistanceChanged: (Float) -> Unit
) {
    var sliderValue by remember(distanceFraction) { mutableStateOf(distanceFraction) }
    val allPoints = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: emptyList()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, if (isSynced) AccentBlue.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isSynced) AccentBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSynced) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.LinkOff, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Clip $clipIndex",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "${styleDuration(durationMs)} • $clipName",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Show current position on track + GPX timestamp
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f km".format(sliderValue * totalDistance / 1000.0),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSynced) AccentBlue else Color.White.copy(alpha = 0.5f)
                    )
                    if (allPoints.isNotEmpty()) {
                        val pointIdx = (sliderValue * (allPoints.size - 1)).toInt().coerceIn(0, allPoints.lastIndex)
                        val pt = allPoints[pointIdx]
                        // Show GPX timestamp
                        pt.time?.let { time ->
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                                .withZone(java.time.ZoneId.systemDefault())
                            Text(
                                formatter.format(time),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentBlue.copy(alpha = 0.6f)
                            )
                        }
                        // Show elevation
                        pt.elevation?.let { elev ->
                            Text(
                                "%.0f m".format(elev),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Slider with elevation chart background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newFrac = (offset.x / size.width).coerceIn(0f, 1f)
                            sliderValue = newFrac
                            onDistanceChanged(newFrac)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                            sliderValue = newFrac
                            onDistanceChanged(newFrac)
                        }
                    }
            ) {
                // Mini elevation chart as slider background
                MiniElevChart(gpxData = gpxData, accentColor = AccentBlue, progress = sliderValue)

                // Pin indicator using fractional width
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = sliderValue.coerceIn(0.01f, 1f))
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.dp, height = 24.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

// ── Clip Overview Map (with numbered clip dots) ─────────────────────────

@Composable
private fun ClipOverviewMap(
    gpxData: GpxData?,
    accentColor: Color,
    clipFractions: List<Float>,
    modifier: Modifier = Modifier
) {
    val allPoints = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: emptyList()
    if (allPoints.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)))
        return
    }

    val bounds = gpxData?.bounds ?: return
    val latRange = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(0.0001)
    val lonRange = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(0.0001)

    val sampled = if (allPoints.size > 300) {
        val step = allPoints.size.toFloat() / 300f
        (0 until 300).map { i -> allPoints[(i * step).toInt().coerceAtMost(allPoints.lastIndex)] }
    } else allPoints

    val tealColor = accentColor.copy(alpha = 0.8f)

    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        val w = size.width
        val h = size.height
        val padding = 16f

        drawRect(Color.White.copy(alpha = 0.06f))

        fun projectX(lon: Double) = padding + ((lon - bounds.minLongitude) / lonRange).toFloat() * (w - 2 * padding)
        fun projectY(lat: Double) = h - padding - ((lat - bounds.minLatitude) / latRange).toFloat() * (h - 2 * padding)

        // Full route
        val fullPath = Path().apply {
            sampled.forEachIndexed { i, pt ->
                val x = projectX(pt.longitude)
                val y = projectY(pt.latitude)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(fullPath, Color.White.copy(alpha = 0.2f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        drawPath(fullPath, tealColor, style = Stroke(width = 2f, cap = StrokeCap.Round))

        // Clip position dots with numbers
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 22f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        clipFractions.forEachIndexed { idx, frac ->
            val ptIdx = (frac * (sampled.size - 1)).toInt().coerceIn(0, sampled.lastIndex)
            val pt = sampled[ptIdx]
            val cx = projectX(pt.longitude)
            val cy = projectY(pt.latitude)
            val dotRadius = 14f

            drawCircle(Color.White, radius = dotRadius + 2f, center = Offset(cx, cy))
            drawCircle(tealColor, radius = dotRadius, center = Offset(cx, cy))
            drawContext.canvas.nativeCanvas.drawText("${idx + 1}", cx, cy + 8f, textPaint)
        }
    }
}

// ── Mini Elevation Chart ─────────────────────────────────────────────────

@Composable
private fun MiniElevChart(
    gpxData: GpxData?,
    accentColor: Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elev_progress"
    )

    val elevations = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }?.mapNotNull { it.elevation } ?: emptyList()

    if (elevations.size >= 2) {
        val minElev = elevations.min()
        val maxElev = elevations.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)
        val sampled = if (elevations.size > 80) {
            val step = elevations.size.toFloat() / 80f
            (0 until 80).map { i -> elevations[(i * step).toInt().coerceAtMost(elevations.lastIndex)] }
        } else elevations

        val tealColor = accentColor.copy(alpha = 0.8f)

        Canvas(modifier = modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(4.dp))) {
            val w = size.width
            val h = size.height
            val progressX = w * animatedProgress

            drawRect(Color.White.copy(alpha = 0.05f))

            val path = Path().apply {
                moveTo(0f, h)
                sampled.forEachIndexed { i, elev ->
                    val x = (i.toFloat() / (sampled.size - 1)) * w
                    val normalized = ((elev - minElev) / range).toFloat()
                    val y = h - (normalized * h * 0.85f) - (h * 0.05f)
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }

            drawPath(path, Color.White.copy(alpha = 0.08f), style = Fill)

            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, progressX, h)
            drawPath(path, tealColor.copy(alpha = 0.5f), style = Fill)
            drawContext.canvas.restore()

            val linePath = Path().apply {
                sampled.forEachIndexed { i, elev ->
                    val x = (i.toFloat() / (sampled.size - 1)) * w
                    val normalized = ((elev - minElev) / range).toFloat()
                    val y = h - (normalized * h * 0.85f) - (h * 0.05f)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(linePath, tealColor, style = Stroke(width = 1.5f))

            if (animatedProgress > 0.01f) {
                val idx = ((animatedProgress * (sampled.size - 1)).toInt()).coerceIn(0, sampled.lastIndex)
                val dotElev = sampled[idx]
                val dotNorm = ((dotElev - minElev) / range).toFloat()
                val dotY = h - (dotNorm * h * 0.85f) - (h * 0.05f)
                drawCircle(Color.White, radius = 3f, center = Offset(progressX, dotY))
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.fillMaxWidth(animatedProgress).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accentColor.copy(alpha = 0.6f)))
        }
    }
}

// ── Mini Route Map ───────────────────────────────────────────────────────

@Composable
private fun MiniRouteMap(
    gpxData: GpxData?,
    accentColor: Color,
    progress: Float,
    modifier: Modifier = Modifier,
    isSquare: Boolean = false
) {
    val allPoints = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points } ?: emptyList()
    if (allPoints.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.05f)))
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "map_progress"
    )

    val bounds = gpxData?.bounds ?: return
    val latRange = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(0.0001)
    val lonRange = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(0.0001)

    // Sample points for rendering
    val sampled = if (allPoints.size > 200) {
        val step = allPoints.size.toFloat() / 200f
        (0 until 200).map { i -> allPoints[(i * step).toInt().coerceAtMost(allPoints.lastIndex)] }
    } else allPoints

    val tealColor = accentColor.copy(alpha = 0.8f)
    val cornerRadius = if (isSquare) 8.dp else 4.dp
    val canvasModifier = if (isSquare) {
        modifier.clip(RoundedCornerShape(cornerRadius))
    } else {
        modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(cornerRadius))
    }

    Canvas(modifier = canvasModifier) {
        val w = size.width
        val h = size.height
        val padding = if (isSquare) 6f else 2f

        drawRect(Color.White.copy(alpha = if (isSquare) 0.08f else 0.05f))

        fun projectX(lon: Double) = padding + ((lon - bounds.minLongitude) / lonRange).toFloat() * (w - 2 * padding)
        fun projectY(lat: Double) = h - padding - ((lat - bounds.minLatitude) / latRange).toFloat() * (h - 2 * padding)

        // Draw full route (dimmed)
        val fullPath = Path().apply {
            sampled.forEachIndexed { i, pt ->
                val x = projectX(pt.longitude)
                val y = projectY(pt.latitude)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(fullPath, Color.White.copy(alpha = 0.15f), style = Stroke(width = if (isSquare) 2f else 1.5f, cap = StrokeCap.Round))

        // Draw progress portion
        val progressIdx = ((animatedProgress * (sampled.size - 1)).toInt()).coerceIn(0, sampled.lastIndex)
        if (progressIdx > 0) {
            val progressPath = Path().apply {
                for (i in 0..progressIdx) {
                    val x = projectX(sampled[i].longitude)
                    val y = projectY(sampled[i].latitude)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(progressPath, tealColor, style = Stroke(width = if (isSquare) 2.5f else 2f, cap = StrokeCap.Round))
        }

        // Current position dot
        if (animatedProgress > 0.01f) {
            val pt = sampled[progressIdx]
            val dotRadius = if (isSquare) 5f else 4f
            drawCircle(Color.White, radius = dotRadius, center = Offset(projectX(pt.longitude), projectY(pt.latitude)))
            drawCircle(tealColor, radius = dotRadius - 1f, center = Offset(projectX(pt.longitude), projectY(pt.latitude)))
        }
    }
}

// ── Utilities ────────────────────────────────────────────────────────────

private fun styleDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatDurationMs(ms: Long) = FormatUtils.formatDurationMs(ms)
