package com.gpxvideo.feature.export

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class ExportRoute(val projectId: String)

fun NavGraphBuilder.exportScreen(
    onNavigateBack: () -> Unit,
    onExportComplete: (String) -> Unit
) {
    composable<ExportRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ExportRoute>()
        ExportScreen(
            projectId = route.projectId,
            onNavigateBack = onNavigateBack,
            onExportComplete = onExportComplete
        )
    }
}
