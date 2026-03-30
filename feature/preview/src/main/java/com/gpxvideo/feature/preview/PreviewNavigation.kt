package com.gpxvideo.feature.preview

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class PreviewRoute(val projectId: String)

fun NavGraphBuilder.previewScreen(onNavigateBack: () -> Unit) {
    composable<PreviewRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<PreviewRoute>()
        PreviewScreen(
            projectId = route.projectId,
            onNavigateBack = onNavigateBack
        )
    }
}
