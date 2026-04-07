package com.gpxvideo.feature.project

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.model.StoryTemplate
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.core.ui.theme.AthleticCondensed
import com.gpxvideo.core.ui.theme.AthleticType

/**
 * Screen 2: "The Magic" — Style & Telemetry
 * Full video preview with overlay templates, sync toggle, accent color, and export.
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
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showTitleEditor by rememberSaveable { mutableStateOf(false) }

    if (uiState.isLoading) {
        LoadingIndicator()
        return
    }

    Scaffold(
        containerColor = Color(0xFF0D0D12),
        topBar = {
            StyleTopBar(
                selectedRatio = uiState.selectedAspectRatio,
                onNavigateBack = onNavigateBack,
                onColorPickerClick = { showColorPicker = true }
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

    // Color picker bottom sheet
    if (showColorPicker) {
        ColorPickerSheet(
            currentColor = uiState.accentColor,
            onColorSelected = { viewModel.setAccentColor(it) },
            onDismiss = { showColorPicker = false }
        )
    }

    // Title editor dialog
    if (showTitleEditor) {
        TitleEditorDialog(
            currentTitle = uiState.activityTitle,
            onTitleChanged = { viewModel.setActivityTitle(it) },
            onDismiss = { showTitleEditor = false }
        )
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────

@Composable
private fun StyleTopBar(
    selectedRatio: SocialAspectRatio,
    onNavigateBack: () -> Unit,
    onColorPickerClick: () -> Unit
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

            // Format badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Text(
                    selectedRatio.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            IconButton(onClick = onColorPickerClick) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = "Accent Color",
                    tint = Color.White.copy(alpha = 0.7f)
                )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF448AFF)
                )
            ) {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Export Story",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Template pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 0
        ) { page ->
            StyleTemplateCard(
                template = templates[page],
                gpxData = uiState.gpxData,
                mediaItems = uiState.mediaItems,
                aspectRatio = aspectRatio,
                accentColor = accentColor,
                activityTitle = activityTitle,
                onTitleClick = onTitleClick
            )
        }

        // Template name + page indicator
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

        // Page dots
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
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
    gpxData: com.gpxvideo.core.model.GpxData?,
    mediaItems: List<MediaItemEntity>,
    aspectRatio: SocialAspectRatio,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit
) {
    val cardAspectRatio = aspectRatio.width.toFloat() / aspectRatio.height.toFloat()
    val firstVideoPath = mediaItems
        .firstOrNull { it.type == "VIDEO" }
        ?.let { it.localCopyPath.ifBlank { it.sourcePath } }
        ?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardAspectRatio),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Video background
            if (firstVideoPath != null) {
                StyleVideoThumbnail(
                    videoPath = firstVideoPath,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
                            )
                        )
                )
            }

            // Template overlay
            when (template) {
                StoryTemplate.CINEMATIC -> CinematicOverlay(gpxData, accentColor, activityTitle, onTitleClick)
                StoryTemplate.HERO -> HeroOverlay(gpxData, accentColor, activityTitle, onTitleClick)
                StoryTemplate.PRO_DASHBOARD -> ProDashboardOverlay(gpxData, accentColor, activityTitle, onTitleClick)
            }
        }
    }
}

// ── Template Overlays ────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
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

@Composable
private fun CinematicOverlay(
    gpxData: com.gpxvideo.core.model.GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // Title (tappable)
        if (activityTitle.isNotBlank()) {
            Text(
                activityTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clickable(onClick = onTitleClick)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            GlassCard {
                Column {
                    Text("DISTANCE", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                    Text(
                        gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—",
                        style = AthleticType.largeMetric,
                        color = Color.White
                    )
                    Text("km", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassCard(modifier = Modifier.width(60.dp)) {
                    Column {
                        Text("ELEV", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(
                            gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "— m",
                            style = AthleticType.smallMetric,
                            color = Color.White
                        )
                    }
                }
                GlassCard(modifier = Modifier.width(60.dp)) {
                    Column {
                        Text("PACE", style = AthleticType.metricLabel, color = accentColor.copy(alpha = 0.7f))
                        Text(
                            gpxData?.let {
                                val speedMs = if (it.totalDuration.seconds > 0)
                                    it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
                                val speedKmh = speedMs * 3.6
                                if (speedKmh > 0) {
                                    val paceMin = (60.0 / speedKmh).toInt()
                                    val paceSec = ((60.0 / speedKmh - paceMin) * 60).toInt()
                                    "%d:%02d".format(paceMin, paceSec)
                                } else "—"
                            } ?: "—",
                            style = AthleticType.smallMetric,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MiniElevChart(gpxData = gpxData, accentColor = accentColor)
        }
    }
}

@Composable
private fun HeroOverlay(
    gpxData: com.gpxvideo.core.model.GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Editable title
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
            Text(
                gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "—",
                style = AthleticType.heroMetric,
                color = Color.White
            )
            Text(
                "KM",
                fontFamily = AthleticCondensed,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontSize = 16.sp,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️", fontSize = 14.sp)
                        Text(
                            gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "—",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text("m gain", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱️", fontSize = 14.sp)
                        Text(
                            gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text("time", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                GlassCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💓", fontSize = 14.sp)
                        Text(
                            gpxData?.tracks?.flatMap { t -> t.segments }
                                ?.flatMap { s -> s.points }
                                ?.mapNotNull { p -> p.heartRate }
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { "%.0f".format(it.average()) } ?: "—",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text("avg bpm", style = AthleticType.metricLabel, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        MiniElevChart(
            gpxData = gpxData,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ProDashboardOverlay(
    gpxData: com.gpxvideo.core.model.GpxData?,
    accentColor: Color,
    activityTitle: String,
    onTitleClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Video area (left)
        Box(modifier = Modifier.weight(0.6f).fillMaxSize()) {
            if (activityTitle.isNotBlank()) {
                Text(
                    activityTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clickable(onClick = onTitleClick)
                )
            }
            MiniElevChart(
                gpxData = gpxData,
                accentColor = accentColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            )
        }

        // Dashboard panel (right)
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                DashMetric("DISTANCE", gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "—", accentColor)
                DashMetric("ELEVATION", gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "—", Color(0xFF66BB6A))
                DashMetric("SPEED", gpxData?.let {
                    val s = if (it.totalDuration.seconds > 0) it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
                    "%.1f km/h".format(s * 3.6)
                } ?: "—", Color(0xFFFFAB40))
                DashMetric("HEART RATE", gpxData?.tracks?.flatMap { it.segments }?.flatMap { it.points }
                    ?.mapNotNull { it.heartRate }?.takeIf { it.isNotEmpty() }
                    ?.let { "%.0f bpm".format(it.average()) } ?: "—", Color(0xFFEF5350))
                DashMetric("TIME", gpxData?.let { styleDuration(it.totalDuration.toMillis()) } ?: "—", Color(0xFF26A69A))
            }
        }
    }
}

@Composable
private fun DashMetric(label: String, value: String, color: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
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
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        0xFF448AFF.toInt() to "Blue",
        0xFF00C853.toInt() to "Green",
        0xFFFF6D00.toInt() to "Orange",
        0xFFEF5350.toInt() to "Red",
        0xFFAB47BC.toInt() to "Purple",
        0xFF26A69A.toInt() to "Teal",
        0xFFFFD600.toInt() to "Yellow",
        0xFFEC407A.toInt() to "Pink",
        0xFFFFFFFF.toInt() to "White"
    )

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
            Text(
                "Accent Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Choose the highlight color for your overlay",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Color grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetColors.take(5).forEach { (color, name) ->
                    ColorSwatch(
                        color = color,
                        name = name,
                        isSelected = color == currentColor,
                        onClick = {
                            onColorSelected(color)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetColors.drop(5).forEach { (color, name) ->
                    ColorSwatch(
                        color = color,
                        name = name,
                        isSelected = color == currentColor,
                        onClick = {
                            onColorSelected(color)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Spacers for alignment
                repeat(5 - presetColors.drop(5).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(color))
                .then(
                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (color == 0xFFFFFFFF.toInt() || color == 0xFFFFD600.toInt())
                        Color.Black else Color.White
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = if (isSelected) 0.8f else 0.4f),
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 9.sp
        )
    }
}

// ── Title Editor ─────────────────────────────────────────────────────────

@Composable
private fun TitleEditorDialog(
    currentTitle: String,
    onTitleChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        title = {
            Text("Activity Title", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. Morning Ride, Trail Run…", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF448AFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Color(0xFF448AFF)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onTitleChanged(text)
                onDismiss()
            }) {
                Text("Save", color = Color(0xFF448AFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}

// ── Mini Elevation Chart ─────────────────────────────────────────────────

@Composable
private fun MiniElevChart(
    gpxData: com.gpxvideo.core.model.GpxData?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    progress: Float = 0.65f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elev_progress"
    )

    val elevations = gpxData?.tracks
        ?.flatMap { it.segments }
        ?.flatMap { it.points }
        ?.mapNotNull { it.elevation }
        ?: emptyList()

    if (elevations.size >= 2) {
        val minElev = elevations.min()
        val maxElev = elevations.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)

        val sampled = if (elevations.size > 80) {
            val step = elevations.size.toFloat() / 80f
            (0 until 80).map { i -> elevations[(i * step).toInt().coerceAtMost(elevations.lastIndex)] }
        } else elevations

        val tealColor = accentColor.copy(alpha = 0.8f)

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
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
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.6f))
            )
        }
    }
}

// ── Video Thumbnail ──────────────────────────────────────────────────────

@Composable
private fun StyleVideoThumbnail(
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

private fun styleDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
