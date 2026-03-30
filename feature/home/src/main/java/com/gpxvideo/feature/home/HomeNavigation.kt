package com.gpxvideo.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
object HomeRoute

fun NavGraphBuilder.homeScreen(
    onProjectClick: (UUID) -> Unit,
    onCreateProject: () -> Unit
) {
    composable<HomeRoute> {
        HomeScreen(
            onProjectClick = onProjectClick,
            onCreateProject = onCreateProject
        )
    }
}
