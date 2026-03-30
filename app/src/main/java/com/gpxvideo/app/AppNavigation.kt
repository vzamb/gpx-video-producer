package com.gpxvideo.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gpxvideo.feature.home.homeScreen
import com.gpxvideo.feature.home.HomeRoute
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        homeScreen(
            onProjectClick = { projectId ->
                navController.navigate(ProjectEditorRoute(projectId = projectId.toString()))
            },
            onCreateProject = {
                navController.navigate(CreateProjectRoute)
            }
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
