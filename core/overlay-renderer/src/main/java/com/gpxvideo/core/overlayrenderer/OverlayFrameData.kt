package com.gpxvideo.core.overlayrenderer

/**
 * Unified frame data for overlay rendering.
 * Used by both preview and export pipelines.
 */
data class OverlayFrameData(
    val distance: Double = 0.0,           // meters
    val elevation: Double = 0.0,          // meters
    val elevationGain: Double = 0.0,      // meters (accumulated vertical gain)
    val speed: Double = 0.0,              // meters/second
    val pace: String = "—",              // formatted pace string (e.g. "5:23")
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val power: Int? = null,
    val temperature: Double? = null,
    val grade: Double = 0.0,
    val elapsedTime: Long = 0L,           // milliseconds
    val progress: Float = 0f,             // 0f..1f through the activity
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val distanceKm: String get() = "%.1f".format(distance / 1000.0)
    val elevationStr: String get() = "↑ %d".format(elevationGain.toInt())
    val heartRateStr: String get() = heartRate?.let { "♥ $it" } ?: "—"
    val gradeStr: String get() = "%.1f%%".format(grade)

    val elapsedTimeStr: String get() {
        val totalSec = elapsedTime / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
