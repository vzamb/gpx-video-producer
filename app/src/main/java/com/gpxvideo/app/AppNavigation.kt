package com.gpxvideo.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.gpxvideo.feature.home.homeScreen
import com.gpxvideo.feature.home.HomeRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        homeScreen(
            onProjectClick = { /* TODO: navigate to project */ },
            onCreateProject = { /* TODO: navigate to create project */ }
        )
    }
}
