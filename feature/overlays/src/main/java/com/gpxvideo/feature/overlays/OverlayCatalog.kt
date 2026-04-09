package com.gpxvideo.feature.overlays

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector

data class OverlayCatalogItem(
    val type: String,
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val category: OverlayCategory,
    val requiresGpx: Boolean = true
)

enum class OverlayCategory(val displayName: String, val helperText: String) {
    STATIC("Static", "Full-route visuals for intros, recaps, and summary cards."),
    MOVING("Moving", "Animated route elements that travel across the whole activity."),
    LIVE("Live", "Timestamp-aware overlays synced to the video position.")
}

enum class OverlayStylePreset(val displayName: String, val description: String) {
    CLEAN("Clean", "Sharp dark cards with cool highlights."),
    BROADCAST("Broadcast", "Sporty TV-style contrast and bright accents."),
    GLASS("Glass", "Softer translucent cards for premium recaps.")
}

enum class OverlayFormatPreset(val displayName: String, val description: String) {
    COMPACT("Compact", "Small footprint for busy edits."),
    CARD("Card", "Balanced default for most layouts."),
    WIDE("Wide", "Expanded panels for map and graph focus.")
}

object OverlayCatalog {
    val gpxItems: List<OverlayCatalogItem> = listOf(
        OverlayCatalogItem(
            type = "static_altitude_profile",
            displayName = "Altitude Profile",
            description = "A full-route elevation chart for recap scenes.",
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "static_map",
            displayName = "Route Map",
            description = "A static overview of the entire GPX track.",
            icon = Icons.Outlined.Map,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "static_stats",
            displayName = "Summary Stats",
            description = "Distance, time, elevation, and speed in a recap card.",
            icon = Icons.Outlined.QueryStats,
            category = OverlayCategory.STATIC
        ),
        OverlayCatalogItem(
            type = "moving_altitude_profile",
            displayName = "Moving Profile",
            description = "A runner dot advances across the full elevation chart.",
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            category = OverlayCategory.MOVING
        ),
        OverlayCatalogItem(
            type = "moving_map",
            displayName = "Moving Map",
            description = "A travel marker moves across the whole route map.",
            icon = Icons.Outlined.Map,
            category = OverlayCategory.MOVING
        ),
        OverlayCatalogItem(
            type = "moving_stat",
            displayName = "Moving Metric",
            description = "Animated progress-based stat tied to clip progression.",
            icon = Icons.Outlined.Speed,
            category = OverlayCategory.MOVING
        ),
        OverlayCatalogItem(
            type = "dynamic_altitude_profile",
            displayName = "Live Altitude",
            description = "A timestamp-synced marker shows where you are on the profile.",
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            category = OverlayCategory.LIVE
        ),
        OverlayCatalogItem(
            type = "dynamic_map",
            displayName = "Live Map",
            description = "Show the exact current GPX position at the active video time.",
            icon = Icons.Outlined.Map,
            category = OverlayCategory.LIVE
        ),
        OverlayCatalogItem(
            type = "dynamic_stat",
            displayName = "Live Metric",
            description = "Display a live timestamp-linked metric like speed or heart rate.",
            icon = Icons.Outlined.Speed,
            category = OverlayCategory.LIVE
        )
    )
}
