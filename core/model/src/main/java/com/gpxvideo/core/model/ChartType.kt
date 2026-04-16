package com.gpxvideo.core.model

/**
 * Available chart types for the overlay chart area.
 *
 * `null` means the chart is hidden (off).
 */
enum class ChartType(
    val displayName: String,
    val unitLabel: String
) {
    ELEVATION("Elevation", "m"),
    PACE("Pace", "min/km"),
    HEART_RATE("Heart Rate", "bpm"),
    POWER("Power", "W");

    companion object {
        /** Safe parse from stored string — returns null if the value is unknown. */
        fun fromName(name: String?): ChartType? =
            if (name == null) null else entries.firstOrNull { it.name == name }
    }
}
