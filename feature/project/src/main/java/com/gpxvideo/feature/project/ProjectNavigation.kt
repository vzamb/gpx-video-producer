package com.gpxvideo.feature.project

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
object CreateProjectRoute

@Serializable
data class ProjectEditorRoute(val projectId: String)

@Serializable
data class StyleRoute(val projectId: String)

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
    onNavigateToStyle: (String) -> Unit,
    onNavigateToExport: (String) -> Unit
) {
    composable<ProjectEditorRoute> {
        VideoAssemblyScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToStyle = onNavigateToStyle
        )
    }
}

fun NavGraphBuilder.styleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit
) {
    composable<StyleRoute> {
        StyleTelemetryScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToExport = onNavigateToExport
        )
    }
}
