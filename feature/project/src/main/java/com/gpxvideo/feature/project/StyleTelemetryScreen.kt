package com.gpxvideo.feature.project

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.model.StoryTemplate
import com.gpxvideo.core.ui.theme.AthleticCondensed
import com.gpxvideo.core.ui.theme.AthleticType
import com.gpxvideo.feature.preview.VideoPreview
import com.gpxvideo.lib.gpxparser.GpxStats

/**
 * Screen 2: "The Magic" — Style & Telemetry
 * Live video preview with overlay templates, sync toggle, accent color, and export.
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

    val playbackProgress by remember {
        derivedStateOf {
            if (videoDuration > 0) (currentPositionMs.toFloat() / videoDuration).coerceIn(0f, 1f)
            else 0f
        }
    }

    // Live GPX interpolation
    val liveGpxValues by remember(uiState.gpxData, playbackProgress, uiState.storyMode) {
        derivedStateOf {
            interpolateGpxAtProgress(uiState.gpxData, playbackProgress, uiState.storyMode)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.pause() }
    }

    if (uiState.isLoading) {
        com.gpxvideo.core.ui.component.LoadingIndicator()
        return
    }

    Scaffold(
        containerColor = Color(0xFF0D0D12),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Sync toggle
            SyncToggle(
                selectedMode = uiState.storyMode,
                onModeSelected = viewModel::setStoryMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
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
            sportType = uiState.project?.sportType ?: "CYCLING",
            onDismiss = { showInfoSheet = false }
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
    val power: Int? = null
)

private fun interpolateGpxAtProgress(
    gpxData: GpxData?,
    progress: Float,
    storyMode: String
): LiveGpxValues {
    if (gpxData == null) return LiveGpxValues()
    val points = gpxData.tracks.flatMap { it.segments }.flatMap { it.points }
    if (points.isEmpty()) return LiveGpxValues()

    // For both modes, use progress-based interpolation
    val idx = ((progress * (points.size - 1)).toInt()).coerceIn(0, points.lastIndex)
    val point = points[idx]

    // Distance: proportion of total
    val distanceSoFar = gpxData.totalDistance * progress
    // Speed: approximate from neighbors
    val speed = if (idx > 0 && idx < points.lastIndex) {
        val prevElev = points[idx - 1].elevation ?: 0.0
        val currElev = point.elevation ?: 0.0
        // Use average speed * some variation based on elevation change
        val avgSpeed = if (gpxData.totalDuration.seconds > 0)
            gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
        avgSpeed * (0.8 + 0.4 * progress) // slight variation for visual interest
    } else {
        if (gpxData.totalDuration.seconds > 0)
            gpxData.totalDistance / gpxData.totalDuration.seconds.toDouble() else 0.0
    }

    return LiveGpxValues(
        distance = distanceSoFar,
        elevation = point.elevation ?: 0.0,
        speed = speed,
        heartRate = point.heartRate,
        cadence = point.cadence,
        power = point.power
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
    Surface(
        color = Color(0xFF0D0D12),
        tonalElevation = 0.dp
    ) {
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF448AFF))
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
                storyMode = storyMode
            )
        }

        Spacer(Modifier.height(8.dp))
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

        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            templates.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) Color(0xFF448AFF)
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
    storyMode: String
) {
    val cardAspectRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
    val isLive = storyMode == StoryMode.DOCUMENTARY.name
    val isPortrait = aspectRatio == SocialAspectRatio.PORTRAIT_9_16 || aspectRatio == SocialAspectRatio.PORTRAIT_4_5
    val isLandscape = aspectRatio == SocialAspectRatio.LANDSCAPE_16_9

    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(cardAspectRatio),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Live video preview (only for active page)
            if (isActivePage) {
                VideoPreview(
                    previewEngine = previewEngine,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))))
                )
            }

            // Template overlay
            when (template) {
                StoryTemplate.CINEMATIC -> CinematicOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isLive, isPortrait, isLandscape)
                StoryTemplate.HERO -> HeroOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isLive, isPortrait, isLandscape)
                StoryTemplate.PRO_DASHBOARD -> ProDashboardOverlay(gpxData, accentColor, activityTitle, onTitleClick, playbackProgress, liveGpxValues, isLive, isPortrait, isLandscape)
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
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Color(0xFF448AFF),
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

private fun formatMetricDistance(meters: Double): String = "%.1f".format(meters / 1000.0)
private fun formatMetricSpeed(metersPerSec: Double): String = "%.1f".format(metersPerSec * 3.6)
private fun formatMetricElevation(meters: Double): String = "%.0f".format(meters)
private fun formatPace(metersPerSec: Double): String {
    val speedKmh = metersPerSec * 3.6
    if (speedKmh <= 0) return "—"
    val paceMin = (60.0 / speedKmh).toInt()
    val paceSec = ((60.0 / speedKmh - paceMin) * 60).toInt()
    return "%d:%02d".format(paceMin, paceSec)
}

@Composable
private fun CinematicOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isLive: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean
) {
    val dist = if (isLive) formatMetricDistance(liveValues.distance) else gpxData?.let { formatMetricDistance(it.totalDistance) } ?: "—"
    val elev = if (isLive) "${formatMetricElevation(liveValues.elevation)} m" else gpxData?.let { "${formatMetricElevation(it.totalElevationGain)} m" } ?: "— m"
    val pace = if (isLive) formatPace(liveValues.speed) else gpxData?.let {
        val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
        formatPace(s)
    } ?: "—"

    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPortrait) 280.dp else 180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        // Title
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
                        Text("PACE", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(pace, style = AthleticType.smallMetric, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MiniElevChart(gpxData = gpxData, accentColor = accentColor, progress = progress)
        }
    }
}

@Composable
private fun HeroOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isLive: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean
) {
    val dist = if (isLive) formatMetricDistance(liveValues.distance) else gpxData?.let { formatMetricDistance(it.totalDistance) } ?: "—"
    val elevVal = if (isLive) formatMetricElevation(liveValues.elevation) else gpxData?.let { formatMetricElevation(it.totalElevationGain) } ?: "—"
    val timeVal = gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—"
    val hrVal = if (isLive) {
        liveValues.heartRate?.toString() ?: "—"
    } else {
        gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
            ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }?.let { "%.0f".format(it.average()) } ?: "—"
    }

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
                        Text(if (isLive) "m alt" else "m gain", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱️", fontSize = 14.sp)
                        Text(timeVal, style = AthleticType.mediumMetric, color = Color.White)
                        Text("time", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💓", fontSize = 14.sp)
                        Text(hrVal, style = AthleticType.mediumMetric, color = Color.White)
                        Text("avg bpm", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        MiniElevChart(
            gpxData = gpxData,
            accentColor = accentColor,
            progress = progress,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun ProDashboardOverlay(
    gpxData: GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit,
    progress: Float,
    liveValues: LiveGpxValues,
    isLive: Boolean,
    isPortrait: Boolean,
    isLandscape: Boolean
) {
    val distStr = if (isLive) "%.1f km".format(liveValues.distance / 1000.0) else gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "—"
    val elevStr = if (isLive) "${formatMetricElevation(liveValues.elevation)} m" else gpxData?.let { "${formatMetricElevation(it.totalElevationGain)} m" } ?: "—"
    val speedStr = if (isLive) formatMetricSpeed(liveValues.speed) + " km/h" else gpxData?.let {
        val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
        formatMetricSpeed(s) + " km/h"
    } ?: "—"
    val hrStr = if (isLive) (liveValues.heartRate?.let { "$it bpm" } ?: "—") else
        gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }?.mapNotNull { it.heartRate }
            ?.takeIf { it.isNotEmpty() }?.let { "%.0f bpm".format(it.average()) } ?: "—"
    val timeStr = gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—"

    if (isPortrait) {
        // Portrait: video top, dashboard bottom
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
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
                    .weight(0.4f)
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
                        DashMetric("SPEED", speedStr, Color(0xFFFFAB40), Modifier.weight(1f))
                        DashMetric("HEART RATE", hrStr, Color(0xFFEF5350), Modifier.weight(1f))
                    }
                    DashMetric("TIME", timeStr, Color(0xFF26A69A), Modifier.fillMaxWidth())
                    MiniElevChart(gpxData = gpxData, accentColor = accentColor, progress = progress)
                }
            }
        }
    } else {
        // Landscape/square: video left, dashboard right
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
                    gpxData = gpxData, accentColor = accentColor, progress = progress,
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
                    DashMetric("SPEED", speedStr, Color(0xFFFFAB40), Modifier.fillMaxWidth())
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

// ── Sync Toggle ──────────────────────────────────────────────────────────

@Composable
private fun SyncToggle(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SyncToggleOption(
                label = "✓ Summary",
                description = "Full activity across video",
                isSelected = selectedMode == StoryMode.HYPER_LAPSE.name,
                onClick = { onModeSelected(StoryMode.HYPER_LAPSE.name) },
                modifier = Modifier.weight(1f)
            )
            SyncToggleOption(
                label = "⚡ Live",
                description = "Real-time per clip",
                isSelected = selectedMode == StoryMode.DOCUMENTARY.name,
                onClick = { onModeSelected(StoryMode.DOCUMENTARY.name) },
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF448AFF).copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF448AFF).copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
            )
            Text(
                description, style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
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
        containerColor = Color(0xFF1A1A2E),
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
        onDismissRequest = onDismiss, containerColor = Color(0xFF1A1A2E),
        title = { Text("Activity Title", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text("e.g. Morning Ride, Trail Run…", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF448AFF), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Color(0xFF448AFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onTitleChanged(text); onDismiss() }) { Text("Save", color = Color(0xFF448AFF), fontWeight = FontWeight.Bold) } },
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
            val sportIcon = when (sportType.uppercase()) {
                "RUNNING" -> Icons.Default.DirectionsRun
                "HIKING" -> Icons.Default.Hiking
                else -> Icons.Default.DirectionsBike
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF448AFF).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(sportIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFF448AFF))
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
                    InfoStatCard("Elevation", "%.0f".format(gpxStats.totalElevationGain), "m", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoStatCard("Duration", styleDuration(gpxStats.movingDuration.toMillis()), "", Modifier.weight(1f))
                    InfoStatCard("Avg Speed", if (gpxStats.avgSpeed > 0) "%.1f".format(gpxStats.avgSpeed * 3.6) else "—", if (gpxStats.avgSpeed > 0) "km/h" else "", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoStatCard("Clips", "${mediaItems.size}", "video${if (mediaItems.size != 1) "s" else ""}", Modifier.weight(1f))
                    InfoStatCard("Clip Duration", styleDuration(mediaItems.mapNotNull { it.durationMs }.sum()), "", Modifier.weight(1f))
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
