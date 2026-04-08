package com.gpxvideo.feature.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.ClipSyncPoint
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.model.StoryTemplate
import com.gpxvideo.core.ui.theme.AthleticCondensed
import com.gpxvideo.core.ui.theme.AthleticType
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.lib.gpxparser.GpxStats
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
    val liveGpxValues by remember(uiState.gpxData, playbackProgress, uiState.storyMode) {
        derivedStateOf {
            interpolateGpxAtProgress(uiState.gpxData, playbackProgress, uiState.storyMode)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
    val elapsedTime: Long = 0L
)

private fun interpolateGpxAtProgress(
    gpxData: GpxData?,
    progress: Float,
    storyMode: String
): LiveGpxValues {
    if (gpxData == null) return LiveGpxValues()
    val points = gpxData.tracks.flatMap { it.segments }.flatMap { it.points }
    if (points.isEmpty()) return LiveGpxValues()

    // Static mode: always return final totals
    if (storyMode == StoryMode.STATIC.name) {
        val avgSpeed = if (gpxData.totalDuration.seconds > 0)
            gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
        val avgHr = points.mapNotNull { it.heartRate }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgCad = points.mapNotNull { it.cadence }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgPow = points.mapNotNull { it.power }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgTemp = points.mapNotNull { it.temperature }.takeIf { it.isNotEmpty() }?.average()
        return LiveGpxValues(
            distance = gpxData.totalDistance,
            elevation = gpxData.totalElevationGain,
            speed = avgSpeed,
            heartRate = avgHr,
            cadence = avgCad,
            power = avgPow,
            temperature = avgTemp,
            grade = 0.0,
            elapsedTime = gpxData.totalDuration.toMillis()
        )
    }

    // Fast Forward & Live Sync: progress-based interpolation
    val idx = ((progress * (points.size - 1)).toInt()).coerceIn(0, points.lastIndex)
    val point = points[idx]

    val distanceSoFar = gpxData.totalDistance * progress

    // Compute speed from neighboring points
    val pointSpeed = point.speed
    val speed: Double = if (pointSpeed != null && pointSpeed > 0) {
        pointSpeed
    } else if (idx > 0) {
        val prevPoint = points[idx - 1]
        val pointTime = point.time
        val prevTime = prevPoint.time
        val timeDiff = if (pointTime != null && prevTime != null)
            (pointTime.toEpochMilli() - prevTime.toEpochMilli()) / 1000.0 else 1.0
        val distChunk = gpxData.totalDistance / points.size
        if (timeDiff > 0) distChunk / timeDiff else 0.0
    } else {
        if (gpxData.totalDuration.seconds > 0)
            gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
    }

    // Grade from neighboring points
    val grade = if (idx > 0 && idx < points.lastIndex) {
        val prevElev = points[idx - 1].elevation ?: 0.0
        val nextElev = points[idx + 1].elevation ?: 0.0
        val elevChange = nextElev - prevElev
        val horizDist = gpxData.totalDistance / points.size * 2
        if (horizDist > 0) (elevChange / horizDist * 100.0) else 0.0
    } else 0.0

    val elapsedTime = (gpxData.totalDuration.toMillis() * progress).toLong()

    return LiveGpxValues(
        distance = distanceSoFar,
        elevation = point.elevation ?: 0.0,
        speed = speed,
        heartRate = point.heartRate,
        cadence = point.cadence,
        power = point.power,
        temperature = point.temperature,
        grade = grade,
        elapsedTime = elapsedTime
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
    val templates = StoryTemplate.entries
    val pagerState = rememberPagerState(
        initialPage = templates.indexOfFirst { it.name == uiState.storyTemplate }.coerceAtLeast(0),
        pageCount = { templates.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onTemplateSelected(templates[pagerState.currentPage].name)
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

        Spacer(Modifier.height(6.dp))
        Text(
            templates[pagerState.currentPage].displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 2.sp
        )
        Text(
            "Swipe to change template",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f)
        )

        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
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
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StyleTemplateCard(
    template: StoryTemplate,
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
        modifier = Modifier.fillMaxWidth().aspectRatio(cardAspectRatio),
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

            // Template overlay
            when (template) {
                StoryTemplate.CINEMATIC -> CinematicOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isAnimated, isPortrait, isLandscape, isRunning)
                StoryTemplate.HERO -> HeroOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isAnimated, isPortrait, isLandscape, isRunning)
                StoryTemplate.PRO_DASHBOARD -> ProDashboardOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isAnimated, isPortrait, isLandscape, isRunning)
            }

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

// ── Template Overlays ────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        content()
    }
}

// ── Metric formatting ────────────────────────────────────────────────────

private fun formatMetricDistance(meters: Double): String = "%.1f".format(meters / 1000.0)
private fun formatMetricSpeed(metersPerSec: Double): String = "%.1f".format(metersPerSec * 3.6)
private fun formatMetricElevation(meters: Double): String = "%.0f".format(meters)

private fun formatPace(metersPerSec: Double): String {
    val speedKmh = metersPerSec * 3.6
    if (speedKmh <= 0.5) return "—"
    val paceMin = (60.0 / speedKmh).toInt()
    val paceSec = ((60.0 / speedKmh - paceMin) * 60).toInt()
    return "%d:%02d".format(paceMin, paceSec)
}

private fun formatGrade(grade: Double): String {
    return "%+.1f%%".format(grade)
}

private fun formatTemp(temp: Double?): String {
    if (temp == null) return "—"
    return "%.0f°".format(temp)
}

/** Choose speed or pace label depending on sport type. */
private fun speedOrPaceLabel(isRunning: Boolean): String = if (isRunning) "PACE" else "SPEED"
private fun speedOrPaceValue(metersPerSec: Double, isRunning: Boolean): String =
    if (isRunning) formatPace(metersPerSec) else formatMetricSpeed(metersPerSec)
private fun speedOrPaceUnit(isRunning: Boolean): String = if (isRunning) "min/km" else "km/h"

// ── Cinematic Overlay ────────────────────────────────────────────────────

@Composable
private fun CinematicOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isAnimated: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean,
    isRunning: Boolean
) {
    val dist = if (isAnimated) formatMetricDistance(liveValues.distance) else gpxData?.let { formatMetricDistance(it.totalDistance) } ?: "—"
    val elev = if (isAnimated) "${formatMetricElevation(liveValues.elevation)} m"
              else gpxData?.let { "${formatMetricElevation(it.totalElevationGain)} m" } ?: "— m"
    val speedPace = if (isAnimated) speedOrPaceValue(liveValues.speed, isRunning)
                    else gpxData?.let {
                        val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
                        speedOrPaceValue(s, isRunning)
                    } ?: "—"

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPortrait) 280.dp else 180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        if (activityTitle.isNotBlank()) {
            Text(
                activityTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable(onClick = onTitleClick)
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(if (isLandscape) 20.dp else 14.dp)) {
            GlassCard(modifier = if (isLandscape) Modifier.width(160.dp) else Modifier) {
                Column {
                    Text("DISTANCE", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                    Text(dist, style = AthleticType.largeMetric, color = Color.White)
                    Text("km", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassCard(modifier = if (isLandscape) Modifier.width(80.dp) else Modifier.width(60.dp)) {
                    Column {
                        Text("ELEV", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(elev, style = AthleticType.smallMetric, color = Color.White)
                    }
                }
                GlassCard(modifier = if (isLandscape) Modifier.width(80.dp) else Modifier.width(60.dp)) {
                    Column {
                        Text(speedOrPaceLabel(isRunning), style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(speedPace, style = AthleticType.smallMetric, color = Color.White)
                        Text(speedOrPaceUnit(isRunning), style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.3f), fontSize = 7.sp)
                    }
                }
                // Show HR or grade as third card
                val thirdLabel = if (liveValues.heartRate != null) "HR" else "GRADE"
                val thirdValue = if (liveValues.heartRate != null) "${liveValues.heartRate}" else formatGrade(liveValues.grade)
                val thirdUnit = if (liveValues.heartRate != null) "bpm" else ""
                GlassCard(modifier = if (isLandscape) Modifier.width(80.dp) else Modifier.width(60.dp)) {
                    Column {
                        Text(thirdLabel, style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(thirdValue, style = AthleticType.smallMetric, color = Color.White)
                        if (thirdUnit.isNotBlank()) {
                            Text(thirdUnit, style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.3f), fontSize = 7.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MiniElevChart(gpxData = gpxData, accentColor = accentColor, progress = if (isAnimated) progress else 1f)
        }
    }
}

// ── Hero Overlay ─────────────────────────────────────────────────────────

@Composable
private fun HeroOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isAnimated: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean,
    isRunning: Boolean
) {
    val dist = if (isAnimated) formatMetricDistance(liveValues.distance) else gpxData?.let { formatMetricDistance(it.totalDistance) } ?: "—"
    val elevVal = if (isAnimated) formatMetricElevation(liveValues.elevation) else gpxData?.let { formatMetricElevation(it.totalElevationGain) } ?: "—"
    val timeVal = if (isAnimated) styleDuration(liveValues.elapsedTime) else gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—"
    val speedPace = if (isAnimated) speedOrPaceValue(liveValues.speed, isRunning)
                    else gpxData?.let {
                        val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
                        speedOrPaceValue(s, isRunning)
                    } ?: "—"

    val heroFontSize = when {
        isLandscape -> 72.sp
        isPortrait -> 56.sp
        else -> 64.sp
    }
    val cardSpacing = if (isLandscape) 16.dp else 10.dp

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (activityTitle.isNotBlank()) {
                Text(
                    activityTitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.8f),
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onTitleClick)
                )
                Spacer(Modifier.height(4.dp))
            }

            Text("DISTANCE", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f), letterSpacing = 3.sp, fontSize = 10.sp)
            Text(dist, fontSize = heroFontSize, fontFamily = AthleticCondensed, fontWeight = FontWeight.Black, color = Color.White)
            Text("KM", fontFamily = AthleticCondensed, fontWeight = FontWeight.Bold, color = accentColor, fontSize = 16.sp, letterSpacing = 4.sp)

            Spacer(Modifier.height(if (isPortrait) 16.dp else 24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(cardSpacing)) {
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️", fontSize = 14.sp)
                        Text(elevVal, style = AthleticType.mediumMetric, color = Color.White)
                        Text(if (isAnimated) "m alt" else "m gain", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isRunning) "🏃" else "⏱️", fontSize = 14.sp)
                        Text(if (isRunning) speedPace else timeVal, style = AthleticType.mediumMetric, color = Color.White)
                        Text(if (isRunning) speedOrPaceUnit(true) else "time", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱️", fontSize = 14.sp)
                        Text(timeVal, style = AthleticType.mediumMetric, color = Color.White)
                        Text("time", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        MiniElevChart(
            gpxData = gpxData,
            accentColor = accentColor,
            progress = if (isAnimated) progress else 1f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

// ── Pro Dashboard Overlay ────────────────────────────────────────────────

@Composable
private fun ProDashboardOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isAnimated: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean,
    isRunning: Boolean
) {
    val distStr = if (isAnimated) "%.1f km".format(liveValues.distance / 1000.0) else gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "—"
    val elevStr = if (isAnimated) "${formatMetricElevation(liveValues.elevation)} m" else gpxData?.let { "${formatMetricElevation(it.totalElevationGain)} m" } ?: "—"
    val speedStr = speedOrPaceLabel(isRunning) + ": " + (if (isAnimated) speedOrPaceValue(liveValues.speed, isRunning) + " " + speedOrPaceUnit(isRunning) else gpxData?.let {
        val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
        speedOrPaceValue(s, isRunning) + " " + speedOrPaceUnit(isRunning)
    } ?: "—")
    val hrStr = if (isAnimated) (liveValues.heartRate?.let { "$it bpm" } ?: "—") else
        gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }?.mapNotNull { it.heartRate }
            ?.takeIf { it.isNotEmpty() }?.let { "%.0f bpm".format(it.average()) } ?: "—"
    val timeStr = if (isAnimated) styleDuration(liveValues.elapsedTime) else gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—"
    val gradeStr = if (isAnimated) formatGrade(liveValues.grade) else "—"
    val tempStr = if (isAnimated) formatTemp(liveValues.temperature) else
        gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }?.mapNotNull { it.temperature }
            ?.takeIf { it.isNotEmpty() }?.let { formatTemp(it.average()) } ?: ""

    if (isPortrait) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.55f).fillMaxWidth()) {
                if (activityTitle.isNotBlank()) {
                    Text(
                        activityTitle, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable(onClick = onTitleClick)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashMetric("DISTANCE", distStr, accentColor, Modifier.weight(1f))
                        DashMetric("ELEVATION", elevStr, Color(0xFF66BB6A), Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashMetric(speedOrPaceLabel(isRunning), speedStr.substringAfter(": "), Color(0xFFFFAB40), Modifier.weight(1f))
                        DashMetric("HEART RATE", hrStr, Color(0xFFEF5350), Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DashMetric("TIME", timeStr, Color(0xFF26A69A), Modifier.weight(1f))
                        if (isAnimated) {
                            DashMetric("GRADE", gradeStr, Color(0xFF7E57C2), Modifier.weight(1f))
                        } else if (tempStr.isNotBlank()) {
                            DashMetric("TEMP", tempStr, Color(0xFF7E57C2), Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    MiniElevChart(gpxData = gpxData, accentColor = accentColor, progress = if (isAnimated) progress else 1f)
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.6f).fillMaxSize()) {
                if (activityTitle.isNotBlank()) {
                    Text(
                        activityTitle, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable(onClick = onTitleClick)
                    )
                }
                MiniElevChart(
                    gpxData = gpxData, accentColor = accentColor,
                    progress = if (isAnimated) progress else 1f,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    DashMetric("DISTANCE", distStr, accentColor, Modifier.fillMaxWidth())
                    DashMetric("ELEVATION", elevStr, Color(0xFF66BB6A), Modifier.fillMaxWidth())
                    DashMetric(speedOrPaceLabel(isRunning), speedStr.substringAfter(": "), Color(0xFFFFAB40), Modifier.fillMaxWidth())
                    DashMetric("HEART RATE", hrStr, Color(0xFFEF5350), Modifier.fillMaxWidth())
                    DashMetric("TIME", timeStr, Color(0xFF26A69A), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DashMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column {
            Text(label, style = AthleticType.metricLabel, color = color.copy(alpha = 0.8f))
            Text(value, style = AthleticType.smallMetric, color = Color.White)
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
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
            // Thumb
            if (totalDurationMs > 0) {
                Box(
                    modifier = Modifier
                        .padding(start = ((progress * 100).coerceIn(0f, 100f)).dp) // approximate
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AccentBlue)
                )
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
                // Elevation chart reference
                MiniElevChart(gpxData = gpxData, accentColor = AccentBlue, progress = 1f, modifier = Modifier.padding(bottom = 12.dp))

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
                // Show current position on track
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f km".format(sliderValue * totalDistance / 1000.0),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSynced) AccentBlue else Color.White.copy(alpha = 0.5f)
                    )
                    // Elevation at this point
                    val elevations = gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }?.mapNotNull { it.elevation }
                    if (elevations != null && elevations.isNotEmpty()) {
                        val elevIdx = (sliderValue * (elevations.size - 1)).toInt().coerceIn(0, elevations.lastIndex)
                        Text(
                            "%.0f m".format(elevations[elevIdx]),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.3f)
                        )
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
                // Mini elevation background
                MiniElevChart(gpxData = gpxData, accentColor = AccentBlue, progress = sliderValue)

                // Pin indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = (sliderValue * 100).coerceIn(0f, 96f).dp)
                            .size(width = 3.dp, height = 24.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White)
                    )
                }
            }
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

// ── Utilities ────────────────────────────────────────────────────────────

private fun styleDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
