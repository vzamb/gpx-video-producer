package com.gpxvideo.feature.home

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.gpxvideo.lib.strava.StravaAuth
import com.gpxvideo.lib.strava.StravaTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

@Serializable
object HomeRoute

@Serializable
object OnboardingRoute

@Serializable
object SettingsRoute

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val stravaAuth: StravaAuth,
    val stravaTokenStore: StravaTokenStore
) : ViewModel()

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
        val viewModel: SettingsViewModel = hiltViewModel()
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            stravaAuth = viewModel.stravaAuth,
            stravaTokenStore = viewModel.stravaTokenStore
        )
    }
}
