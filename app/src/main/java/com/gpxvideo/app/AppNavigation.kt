package com.gpxvideo.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gpxvideo.feature.home.homeScreen
import com.gpxvideo.feature.home.HomeRoute
import com.gpxvideo.feature.home.OnboardingRoute
import com.gpxvideo.feature.home.SettingsRoute
import com.gpxvideo.feature.home.onboardingScreen
import com.gpxvideo.feature.home.settingsScreen
import com.gpxvideo.feature.preview.PreviewRoute
import com.gpxvideo.feature.preview.previewScreen
import com.gpxvideo.feature.project.CreateProjectRoute
import com.gpxvideo.feature.project.ProjectEditorRoute
import com.gpxvideo.feature.project.createProjectScreen
import com.gpxvideo.feature.project.projectEditorScreen
import com.gpxvideo.feature.export.ExportRoute
import com.gpxvideo.feature.export.exportScreen
import com.gpxvideo.feature.templates.TemplateEditorRoute
import com.gpxvideo.feature.templates.TemplateGalleryRoute
import com.gpxvideo.feature.templates.templateEditorScreen
import com.gpxvideo.feature.templates.templateGalleryScreen

private const val ONBOARDING_PREFS = "onboarding_prefs"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
    }
    val onboardingComplete = remember { prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false) }
    val startDestination: Any = if (onboardingComplete) HomeRoute else OnboardingRoute

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        onboardingScreen(
            onComplete = {
                prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
                navController.navigate(HomeRoute) {
                    popUpTo<OnboardingRoute> { inclusive = true }
                }
            }
        )

        homeScreen(
            onProjectClick = { projectId ->
                navController.navigate(ProjectEditorRoute(projectId = projectId.toString()))
            },
            onCreateProject = {
                navController.navigate(CreateProjectRoute)
            },
            onNavigateToSettings = {
                navController.navigate(SettingsRoute)
            }
        )

        settingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        createProjectScreen(
            onProjectCreated = { projectId ->
                navController.navigate(ProjectEditorRoute(projectId = projectId.toString())) {
                    popUpTo<CreateProjectRoute> { inclusive = true }
                }
            },
            onNavigateBack = { navController.popBackStack() }
        )

        projectEditorScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        previewScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        templateGalleryScreen(
            onTemplateSelected = { templateId ->
                navController.navigate(TemplateEditorRoute(templateId = templateId.toString()))
            },
            onCreateTemplate = {
                navController.navigate(TemplateEditorRoute())
            },
            onNavigateBack = { navController.popBackStack() }
        )

        templateEditorScreen(
            onSave = { navController.popBackStack() },
            onNavigateBack = { navController.popBackStack() }
        )

        exportScreen(
            onNavigateBack = { navController.popBackStack() },
            onExportComplete = { navController.popBackStack() }
        )
    }
}
