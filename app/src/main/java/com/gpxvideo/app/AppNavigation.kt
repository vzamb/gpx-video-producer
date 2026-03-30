package com.gpxvideo.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gpxvideo.feature.home.homeScreen
import com.gpxvideo.feature.home.HomeRoute
import com.gpxvideo.feature.project.CreateProjectRoute
import com.gpxvideo.feature.project.ProjectEditorRoute
import com.gpxvideo.feature.project.createProjectScreen
import com.gpxvideo.feature.project.projectEditorScreen

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
    }
}
