package com.gpxvideo.core.common

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

fun Instant.formatRelative(): String {
    val now = Instant.now()
    val duration = Duration.between(this, now)

    return when {
        duration.isNegative -> "just now"
        duration.seconds < 60 -> "just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() == 1L -> "yesterday"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
        duration.toDays() < 365 -> "${duration.toDays() / 30}mo ago"
        else -> "${duration.toDays() / 365}y ago"
    }
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun Double.formatDistance(): String {
    return if (this >= 1000) {
        "%.1f km".format(this / 1000)
    } else {
        "%.0f m".format(this)
    }
}

fun Double.formatSpeed(): String = "%.1f km/h".format(this)

fun Double.formatPace(): String {
    if (this <= 0) return "--:-- min/km"
    val paceMinPerKm = 60.0 / this
    val minutes = paceMinPerKm.toInt()
    val seconds = ((paceMinPerKm - minutes) * 60).toInt()
    return "%d:%02d min/km".format(minutes, seconds)
}
