package com.gpxvideo.feature.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.ui.component.GpxVideoTopAppBar
import com.gpxvideo.core.ui.component.LoadingIndicator
import com.gpxvideo.feature.gpx.GpxTabContent
import com.gpxvideo.feature.preview.TimelineWithPreview
import java.util.UUID

@Composable
fun ProjectEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMedia(uris)
        }
    }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importGpxFile(it) }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Media", "GPX", "Editor")
    val projectIdStr = uiState.project?.id?.toString() ?: ""

    Scaffold(
        topBar = {
            GpxVideoTopAppBar(
                title = uiState.project?.name ?: "Project",
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add media"
                    )
                }
                1 -> FloatingActionButton(
                    onClick = { gpxPickerLauncher.launch(arrayOf("*/*")) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "Import GPX"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    LoadingIndicator()
                }
                else -> {
                    when (selectedTab) {
                        0 -> MediaTabContent(
                            uiState = uiState,
                            onDeleteMedia = viewModel::deleteMedia
                        )
                        1 -> GpxTabContent(
                            gpxData = uiState.gpxData,
                            stats = uiState.gpxStats,
                            onImportClick = { gpxPickerLauncher.launch(arrayOf("*/*")) }
                        )
                        2 -> EditorTabContent(
                            projectId = projectIdStr,
                            hasMedia = uiState.mediaItems.isNotEmpty(),
                            onNavigateToPreview = { onNavigateToPreview(projectIdStr) },
                            onNavigateToExport = { onNavigateToExport(projectIdStr) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTabContent(
    projectId: String,
    hasMedia: Boolean,
    onNavigateToPreview: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    if (!hasMedia) {
        EditorEmptyState()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Action bar with preview/export buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            FilledTonalButton(onClick = onNavigateToPreview) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("Preview", modifier = Modifier.padding(start = 4.dp))
            }
            FilledTonalButton(onClick = onNavigateToExport) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("Export", modifier = Modifier.padding(start = 4.dp))
            }
        }

        // Timeline with inline preview
        TimelineWithPreview(
            projectId = UUID.fromString(projectId),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EditorEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "No media to edit",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Import videos or photos in the Media tab first",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MediaTabContent(
    uiState: ProjectEditorUiState,
    onDeleteMedia: (java.util.UUID) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isImporting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .size(24.dp),
                strokeWidth = 2.dp
            )
        }

        if (uiState.mediaItems.isEmpty() && !uiState.isImporting) {
            MediaEmptyState(modifier = Modifier.fillMaxSize())
        } else {
            MediaGrid(
                mediaItems = uiState.mediaItems,
                onDeleteMedia = onDeleteMedia,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun MediaEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "No media yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Tap + to import photos and videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

