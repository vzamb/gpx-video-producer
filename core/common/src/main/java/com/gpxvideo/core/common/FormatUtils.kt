package com.gpxvideo.core.common

import java.time.Duration

/**
 * Shared formatting utilities for GPX data display.
 * Used by overlay renderers, UI screens, and export pipeline.
 */
object FormatUtils {

    /** Format a [Duration] as "H:MM:SS" or "M:SS". */
    fun formatDuration(duration: Duration): String {
        val totalSeconds = duration.seconds
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%d:%02d".format(minutes, seconds)
        }
    }

    /** Format milliseconds as "H:MM:SS" or "M:SS". */
    fun formatDurationMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    /** Format distance in meters as "X.XX km" or "X m". */
    fun formatDistance(meters: Double): String {
        return if (meters >= 1000) "%.2f km".format(meters / 1000)
        else "%.0f m".format(meters)
    }

    /**
     * Format pace given in min/km.
     * Returns "--:--" for invalid values.
     */
    fun formatPace(paceMinPerKm: Double): String {
        if (paceMinPerKm <= 0 || paceMinPerKm.isInfinite() || paceMinPerKm.isNaN()) return "--:--"
        val minutes = paceMinPerKm.toInt()
        val seconds = ((paceMinPerKm - minutes) * 60).toInt()
        return "%d:%02d".format(minutes, seconds)
    }

    /**
     * Format pace from speed in m/s.
     * Returns "—" for speeds below 0.3 m/s.
     */
    fun formatPaceFromSpeed(metersPerSec: Double): String {
        if (metersPerSec < 0.3) return "—"
        val kmh = metersPerSec * 3.6
        val paceMin = (60.0 / kmh).toInt()
        val paceSec = ((60.0 / kmh - paceMin) * 60).toInt()
        return "%d:%02d".format(paceMin, paceSec)
    }

    /** Format grade as "+X.X%" or "-X.X%". */
    fun formatGrade(grade: Double): String = "%+.1f%%".format(grade)

    /** Format temperature. Returns "—" for null. */
    fun formatTemp(temp: Double?): String {
        if (temp == null) return "—"
        return "%.0f°".format(temp)
    }
}
