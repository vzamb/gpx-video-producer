package com.gpxvideo.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import android.net.Uri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.model.StoryTemplate
import com.gpxvideo.core.ui.component.GpxVideoTopAppBar
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.core.ui.theme.AthleticCondensed
import com.gpxvideo.core.ui.theme.AthleticType

/**
 * The new Story Creator — replaces the old 3-tab editor.
 * Wizard flow: Import → Sync Mode → Template Preview → Export
 */
@Composable
fun StoryCreatorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    val projectIdStr = uiState.project?.id?.toString() ?: ""

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importMedia(uris)
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importGpxFile(it) }
    }

    if (uiState.isLoading) {
        LoadingIndicator()
        return
    }

    Scaffold(
        topBar = {
            StoryTopBar(
                title = uiState.project?.name ?: "New Story",
                currentStep = currentStep,
                onNavigateBack = {
                    if (currentStep > 0) currentStep-- else onNavigateBack()
                }
            )
        },
        bottomBar = {
            StoryBottomBar(
                currentStep = currentStep,
                canAdvance = canAdvanceFromStep(currentStep, uiState),
                isLastStep = currentStep == 3,
                onNext = {
                    if (currentStep < 3) currentStep++
                    else onNavigateToExport(projectIdStr)
                },
                onBack = { if (currentStep > 0) currentStep-- }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "step_transition"
        ) { step ->
            when (step) {
                0 -> ImportStep(
                    uiState = uiState,
                    onPickMedia = {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    onPickGpx = { gpxPickerLauncher.launch(arrayOf("*/*")) },
                    onDeleteMedia = viewModel::deleteMedia
                )
                1 -> SyncModeStep(
                    selectedMode = uiState.storyMode,
                    onModeSelected = viewModel::setStoryMode
                )
                2 -> TemplateStep(
                    selectedTemplate = uiState.storyTemplate,
                    onTemplateSelected = viewModel::setStoryTemplate,
                    gpxData = uiState.gpxData,
                    mediaItems = uiState.mediaItems
                )
                3 -> ReviewStep(
                    uiState = uiState,
                    onExport = { onNavigateToExport(projectIdStr) }
                )
            }
        }
    }
}

private fun canAdvanceFromStep(step: Int, uiState: ProjectEditorUiState): Boolean {
    return when (step) {
        0 -> uiState.mediaItems.isNotEmpty() && uiState.gpxData != null
        1 -> true
        2 -> true
        3 -> true
        else -> false
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────

@Composable
private fun StoryTopBar(
    title: String,
    currentStep: Int,
    onNavigateBack: () -> Unit
) {
    val steps = listOf("Import", "Sync", "Style", "Review")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // Step indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            steps.forEachIndexed { index, label ->
                StepIndicator(
                    label = label,
                    stepNumber = index + 1,
                    isActive = index == currentStep,
                    isCompleted = index < currentStep,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StepIndicator(
    label: String,
    stepNumber: Int,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when {
        isActive -> MaterialTheme.colorScheme.primary
        isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isActive || isCompleted) color else Color.Transparent)
                .then(
                    if (!isActive && !isCompleted) Modifier.border(
                        1.dp,
                        color,
                        CircleShape
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            } else {
                Text(
                    "$stepNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White else color
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ── Bottom Bar ───────────────────────────────────────────────────────────

@Composable
private fun StoryBottomBar(
    currentStep: Int,
    canAdvance: Boolean,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                OutlinedButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Back")
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Button(
                onClick = onNext,
                enabled = canAdvance,
                colors = if (isLastStep) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(
                    if (isLastStep) "Export Story" else "Next",
                    fontWeight = FontWeight.Bold
                )
                if (!isLastStep) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ── Step 0: Import ───────────────────────────────────────────────────────

@Composable
private fun ImportStep(
    uiState: ProjectEditorUiState,
    onPickMedia: () -> Unit,
    onPickGpx: () -> Unit,
    onDeleteMedia: (java.util.UUID) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Add your footage & route",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Import the video clips and GPX track from your activity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Media section
        ImportSectionCard(
            icon = Icons.Default.Movie,
            title = "Video Clips",
            subtitle = if (uiState.mediaItems.isEmpty()) "No clips added yet"
            else "${uiState.mediaItems.size} clip${if (uiState.mediaItems.size > 1) "s" else ""} added",
            hasContent = uiState.mediaItems.isNotEmpty(),
            isLoading = uiState.isImporting,
            onAction = onPickMedia,
            actionLabel = if (uiState.mediaItems.isEmpty()) "Add Clips" else "Add More"
        )

        Spacer(Modifier.height(8.dp))

        // Media thumbnails
        if (uiState.mediaItems.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(uiState.mediaItems) { media ->
                MediaChip(media = media, onDelete = { onDeleteMedia(media.id) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // GPX section
        ImportSectionCard(
            icon = Icons.Default.Route,
            title = "GPX Route",
            subtitle = when {
                uiState.isImportingGpx -> "Importing…"
                uiState.gpxData != null -> {
                    val distKm = uiState.gpxData!!.totalDistance / 1000.0
                    "%.1f km • %s".format(
                        distKm,
                        formatDuration(uiState.gpxData!!.totalDuration.toMillis())
                    )
                }
                else -> "No route file added"
            },
            hasContent = uiState.gpxData != null,
            isLoading = uiState.isImportingGpx,
            onAction = onPickGpx,
            actionLabel = if (uiState.gpxData == null) "Import GPX" else "Replace"
        )

        // GPX stats preview
        if (uiState.gpxStats != null) {
            Spacer(Modifier.height(12.dp))
            GpxStatsPreview(uiState.gpxStats!!)
        }
    }
}

@Composable
private fun ImportSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    hasContent: Boolean,
    isLoading: Boolean,
    onAction: () -> Unit,
    actionLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasContent)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (hasContent) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (hasContent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                    if (hasContent) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FilledTonalButton(onClick = onAction, enabled = !isLoading) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MediaChip(media: MediaItemEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .height(64.dp)
            .width(80.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    if (media.type == "VIDEO") Icons.Default.Movie else Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (media.durationMs != null) {
                    Text(
                        formatDuration(media.durationMs!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun GpxStatsPreview(stats: com.gpxvideo.lib.gpxparser.GpxStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatChip("📏", "%.1f km".format(stats.totalDistance / 1000.0))
        StatChip("⬆️", "%.0f m".format(stats.totalElevationGain))
        StatChip("⏱️", formatDuration(stats.movingDuration.toMillis()))
        if (stats.avgSpeed > 0) {
            StatChip("🏃", "%.1f km/h".format(stats.avgSpeed * 3.6))
        }
    }
}

@Composable
private fun StatChip(emoji: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Step 1: Sync Mode ────────────────────────────────────────────────────

@Composable
private fun SyncModeStep(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Choose your story style",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "How should your telemetry data sync with the video?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        StoryMode.entries.forEach { mode ->
            SyncModeCard(
                mode = mode,
                isSelected = selectedMode == mode.name,
                onSelect = { onModeSelected(mode.name) }
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SyncModeCard(
    mode: StoryMode,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val icon = when (mode) {
        StoryMode.DOCUMENTARY -> Icons.Default.Timer
        StoryMode.HYPER_LAPSE -> Icons.Default.Speed
    }
    val detailText = when (mode) {
        StoryMode.DOCUMENTARY -> "Data updates in real-time within each clip. " +
                "If clip 1 was at km 12 and clip 2 at km 40, telemetry jumps at the scene cut."
        StoryMode.HYPER_LAPSE -> "The entire activity is compressed across your video. " +
                "Distance spins rapidly, elevation chart draws itself start-to-finish."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isSelected) BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary
        ) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isSelected) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mode.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    detailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

// ── Step 2: Template Selection ───────────────────────────────────────────

@Composable
private fun TemplateStep(
    selectedTemplate: String,
    onTemplateSelected: (String) -> Unit,
    gpxData: com.gpxvideo.core.model.GpxData?,
    mediaItems: List<MediaItemEntity> = emptyList()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Pick your look",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Swipe through pre-designed templates",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Template cards
        val templates = StoryTemplate.entries
        val pagerState = rememberPagerState(
            initialPage = templates.indexOfFirst { it.name == selectedTemplate }
                .coerceAtLeast(0),
            pageCount = { templates.size }
        )

        // Sync pager page with selection
        androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
            onTemplateSelected(templates[pagerState.currentPage].name)
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp
        ) { page ->
            TemplatePreviewCard(
                template = templates[page],
                isSelected = templates[page].name == selectedTemplate,
                gpxData = gpxData,
                mediaItems = mediaItems
            )
        }

        // Page dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            templates.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

@Composable
private fun TemplatePreviewCard(
    template: StoryTemplate,
    isSelected: Boolean,
    gpxData: com.gpxvideo.core.model.GpxData?,
    mediaItems: List<MediaItemEntity> = emptyList()
) {
    val firstVideoPath = mediaItems
        .firstOrNull { it.type == "VIDEO" }
        ?.let { it.localCopyPath.ifBlank { it.sourcePath } }
        ?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        border = if (isSelected) BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (firstVideoPath != null) {
                // Real video background
                VideoBackground(
                    videoPath = firstVideoPath,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A1A2E),
                                    Color(0xFF16213E),
                                    Color(0xFF0F3460)
                                )
                            )
                        )
                )
            }

            // Template overlay preview
            when (template) {
                StoryTemplate.CINEMATIC -> CinematicTemplatePreview(gpxData)
                StoryTemplate.HERO -> HeroTemplatePreview(gpxData)
                StoryTemplate.PRO_DASHBOARD -> ProDashboardTemplatePreview(gpxData)
            }

            // Template name badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    template.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── Template Previews (Glassmorphism) ────────────────────────────────────

@Composable
private fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        content()
    }
}

@Composable
private fun CinematicTemplatePreview(gpxData: com.gpxvideo.core.model.GpxData?) {
    // Minimalist: small data cards nestled in bottom-left
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            GlassmorphicCard {
                Column {
                    Text(
                        "DISTANCE",
                        style = AthleticType.metricLabel,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Text(
                        gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "42.5",
                        style = AthleticType.largeMetric,
                        color = Color.White,
                    )
                    Text(
                        "km",
                        style = AthleticType.metricLabel,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassmorphicCard(modifier = Modifier.width(60.dp)) {
                    Column {
                        Text(
                            "ELEV",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                        Text(
                            gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "820 m",
                            style = AthleticType.smallMetric,
                            color = Color.White,
                        )
                    }
                }
                GlassmorphicCard(modifier = Modifier.width(60.dp)) {
                    Column {
                        Text(
                            "PACE",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.6f),
                        )
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
                            } ?: "5:24",
                            style = AthleticType.smallMetric,
                            color = Color.White,
                        )
                    }
                }
            }

            // Mini elevation profile with real data
            Spacer(Modifier.height(8.dp))
            MiniElevationChart(
                gpxData = gpxData,
                progress = 0.65f
            )
        }
    }
}

@Composable
private fun HeroTemplatePreview(gpxData: com.gpxvideo.core.model.GpxData?) {
    // Massive distance centered on screen
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "DISTANCE",
                style = AthleticType.metricLabel,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 3.sp,
                fontSize = 10.sp
            )
            Text(
                gpxData?.let { "%.1f".format(it.totalDistance / 1000.0) } ?: "42.5",
                style = AthleticType.heroMetric,
                color = Color.White,
            )
            Text(
                "KM",
                fontFamily = AthleticCondensed,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 16.sp,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(24.dp))

            // Secondary metrics row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassmorphicCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⬆️", fontSize = 14.sp)
                        Text(
                            gpxData?.let { "%.0f".format(it.totalElevationGain) } ?: "820",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text(
                            "m gain",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
                GlassmorphicCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱️", fontSize = 14.sp)
                        Text(
                            gpxData?.let {
                                formatDuration(it.totalDuration.toMillis())
                            } ?: "3:42:15",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text(
                            "time",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
                GlassmorphicCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💓", fontSize = 14.sp)
                        Text(
                            gpxData?.tracks?.flatMap { t -> t.segments }
                                ?.flatMap { s -> s.points }
                                ?.mapNotNull { p -> p.heartRate }
                                ?.takeIf { hrs -> hrs.isNotEmpty() }
                                ?.let { hrs -> "%.0f".format(hrs.average()) }
                                ?: "—",
                            style = AthleticType.mediumMetric,
                            color = Color.White
                        )
                        Text(
                            "avg bpm",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        // Bottom elevation chart with real data
        MiniElevationChart(
            gpxData = gpxData,
            progress = 0.65f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ProDashboardTemplatePreview(gpxData: com.gpxvideo.core.model.GpxData?) {
    // Side panel with comprehensive metrics
    Row(modifier = Modifier.fillMaxSize()) {
        // Video area (left)
        Box(modifier = Modifier
            .weight(0.6f)
            .fillMaxSize()
        ) {
            MiniElevationChart(
                gpxData = gpxData,
                progress = 0.65f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }

        // Dashboard panel (right)
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardMetric(
                    "DISTANCE",
                    gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "42.5 km",
                    Color(0xFF448AFF)
                )
                DashboardMetric(
                    "ELEVATION",
                    gpxData?.let { "%.0f m".format(it.totalElevationGain) } ?: "820 m",
                    Color(0xFF66BB6A)
                )
                DashboardMetric(
                    "SPEED",
                    gpxData?.let {
                        val speedMs = if (it.totalDuration.seconds > 0)
                            it.totalDistance / it.totalDuration.seconds.toDouble() else 0.0
                        "%.1f km/h".format(speedMs * 3.6)
                    } ?: "— km/h",
                    Color(0xFFFFAB40)
                )
                DashboardMetric(
                    "HEART RATE",
                    gpxData?.tracks?.flatMap { t -> t.segments }
                        ?.flatMap { s -> s.points }
                        ?.mapNotNull { p -> p.heartRate }
                        ?.takeIf { hrs -> hrs.isNotEmpty() }
                        ?.let { hrs -> "%.0f bpm".format(hrs.average()) }
                        ?: "— bpm",
                    Color(0xFFEF5350)
                )
                DashboardMetric(
                    "TIME",
                    gpxData?.let { formatDuration(it.totalDuration.toMillis()) } ?: "3:42",
                    Color(0xFF26A69A)
                )

                // Mini route trace
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🗺️ Route",
                            style = AthleticType.metricLabel,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(label: String, value: String, accentColor: Color) {
    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                label,
                style = AthleticType.metricLabel,
                color = accentColor.copy(alpha = 0.8f),
            )
            Text(
                value,
                style = AthleticType.smallMetric,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun MiniElevationChart(
    gpxData: com.gpxvideo.core.model.GpxData?,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "elevation_progress"
    )

    // Extract elevation points from GPX data
    val elevations = gpxData?.tracks
        ?.flatMap { it.segments }
        ?.flatMap { it.points }
        ?.mapNotNull { it.elevation }
        ?: emptyList()

    if (elevations.size >= 2) {
        // Real elevation profile
        val minElev = elevations.min()
        val maxElev = elevations.max()
        val range = (maxElev - minElev).coerceAtLeast(1.0)

        // Downsample to ~80 points for smooth rendering
        val sampled = if (elevations.size > 80) {
            val step = elevations.size.toFloat() / 80f
            (0 until 80).map { i ->
                elevations[(i * step).toInt().coerceAtMost(elevations.lastIndex)]
            }
        } else elevations

        val tealColor = Color(0xFF26A69A)
        val blueColor = Color(0xFF448AFF)

        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            val w = size.width
            val h = size.height
            val progressX = w * animatedProgress

            // Draw background track
            drawRect(Color.White.copy(alpha = 0.05f))

            // Draw filled elevation profile
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

            // Full profile in dim color
            drawPath(path, Color.White.copy(alpha = 0.08f), style = Fill)

            // Clip the colored portion up to progress
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, progressX, h)
            drawPath(
                path,
                Brush.horizontalGradient(listOf(tealColor.copy(alpha = 0.6f), blueColor.copy(alpha = 0.6f))),
                style = Fill
            )
            drawContext.canvas.restore()

            // Draw elevation line on top
            val linePath = Path().apply {
                sampled.forEachIndexed { i, elev ->
                    val x = (i.toFloat() / (sampled.size - 1)) * w
                    val normalized = ((elev - minElev) / range).toFloat()
                    val y = h - (normalized * h * 0.85f) - (h * 0.05f)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

            drawPath(
                linePath,
                Brush.horizontalGradient(listOf(tealColor, blueColor)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )

            // Progress indicator dot
            if (animatedProgress > 0.01f) {
                val progressIdx = ((animatedProgress * (sampled.size - 1)).toInt())
                    .coerceIn(0, sampled.lastIndex)
                val dotElev = sampled[progressIdx]
                val dotX = progressX
                val dotNorm = ((dotElev - minElev) / range).toFloat()
                val dotY = h - (dotNorm * h * 0.85f) - (h * 0.05f)
                drawCircle(Color.White, radius = 3f, center = Offset(dotX, dotY))
            }
        }
    } else {
        // Fallback: simple progress bar when no elevation data
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
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF26A69A), Color(0xFF448AFF))
                        )
                    )
            )
        }
    }
}

// ── Video Background Player ──────────────────────────────────────────────

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun VideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoPath) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val uri = if (videoPath.startsWith("content://"))
                Uri.parse(videoPath) else Uri.fromFile(java.io.File(videoPath))
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(videoPath) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { textureView ->
                exoPlayer.setVideoTextureView(textureView)
            }
        },
        modifier = modifier
    )
}

// ── Step 3: Review ───────────────────────────────────────────────────────

@Composable
private fun ReviewStep(
    uiState: ProjectEditorUiState,
    onExport: () -> Unit
) {
    val mode = StoryMode.entries.find { it.name == uiState.storyMode } ?: StoryMode.HYPER_LAPSE
    val template = StoryTemplate.entries.find { it.name == uiState.storyTemplate }
        ?: StoryTemplate.CINEMATIC

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Ready to create your story",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Review your choices and export",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        ReviewItem("📹", "Media", "${uiState.mediaItems.size} clips")
        ReviewItem(
            "📍",
            "Route",
            uiState.gpxData?.let { "%.1f km".format(it.totalDistance / 1000.0) } ?: "—"
        )
        ReviewItem("🔄", "Sync Mode", mode.displayName)
        ReviewItem("🎨", "Template", template.displayName)

        Spacer(Modifier.height(16.dp))

        // Compact template preview
        TemplatePreviewCard(
            template = template,
            isSelected = true,
            gpxData = uiState.gpxData
        )
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReviewItem(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Utilities ────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
