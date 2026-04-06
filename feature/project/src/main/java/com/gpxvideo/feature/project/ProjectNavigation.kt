package com.gpxvideo.feature.project

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
object CreateProjectRoute

@Serializable
data class ProjectEditorRoute(val projectId: String)

fun NavGraphBuilder.createProjectScreen(
    onProjectCreated: (UUID) -> Unit,
    onNavigateBack: () -> Unit
) {
    composable<CreateProjectRoute> {
        CreateProjectScreen(
            onProjectCreated = onProjectCreated,
            onNavigateBack = onNavigateBack
        )
    }
}

fun NavGraphBuilder.projectEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit,
    onNavigateToExport: (String) -> Unit
) {
    composable<ProjectEditorRoute> {
        StoryCreatorScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToExport = onNavigateToExport
        )
    }
}
