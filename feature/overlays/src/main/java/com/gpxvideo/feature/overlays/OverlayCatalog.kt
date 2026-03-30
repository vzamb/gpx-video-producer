package com.gpxvideo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

data class OverlayCatalogItem(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val category: OverlayCategory
)

enum class OverlayCategory(val displayName: String) {
    STATIC("Static"),
    DYNAMIC("Dynamic"),
    TEXT("Text")
}

object OverlayCatalog {
    val items: List<OverlayCatalogItem> = listOf(
        OverlayCatalogItem(
            type = "static_altitude_profile",
            displayName = "Altitude Profile",
            description = "Static elevation chart of the full route",
            icon = Icons.AutoMirrored.Filled.ShowChart,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "static_map",
            displayName = "GPS Map",
            description = "Static overview map of the full route",
            icon = Icons.Default.Map,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "static_stats",
            displayName = "Statistics",
            description = "Summary statistics grid (distance, time, etc.)",
            icon = Icons.Default.QueryStats,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "dynamic_altitude_profile",
            displayName = "Live Altitude",
            description = "Animated altitude profile with current position marker",
            icon = Icons.Default.Timeline,
            category = OverlayCategory.DYNAMIC
        ),
        OverlayCatalogItem(
            type = "dynamic_map",
            displayName = "Live Map",
            description = "Animated map tracking current position on route",
            icon = Icons.Default.Map,
            category = OverlayCategory.DYNAMIC
        ),
        OverlayCatalogItem(
            type = "dynamic_stat",
            displayName = "Live Stat",
            description = "Single live-updating metric (speed, HR, etc.)",
            icon = Icons.Default.Speed,
            category = OverlayCategory.DYNAMIC
        )
    )
}
