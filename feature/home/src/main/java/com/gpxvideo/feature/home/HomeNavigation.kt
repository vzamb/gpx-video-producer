package com.gpxvideo.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
object HomeRoute

@Serializable
object OnboardingRoute

@Serializable
object SettingsRoute

fun NavGraphBuilder.homeScreen(
    onProjectClick: (UUID) -> Unit,
    onCreateProject: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    composable<HomeRoute> {
        HomeScreen(
            onProjectClick = onProjectClick,
            onCreateProject = onCreateProject,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

fun NavGraphBuilder.onboardingScreen(
    onComplete: () -> Unit
) {
    composable<OnboardingRoute> {
        OnboardingScreen(onComplete = onComplete)
    }
}

fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit
) {
    composable<SettingsRoute> {
        SettingsScreen(onNavigateBack = onNavigateBack)
    }
}
