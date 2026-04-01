package com.gpxvideo.feature.gpx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpxvideo.lib.gpxparser.GpxStats
import java.time.Duration

@Composable
fun StatsGrid(
    stats: GpxStats,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    val items = buildStatItems(stats)

    Column(modifier = modifier) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { item ->
                    StatCard(
                        label = item.label,
                        value = item.value,
                        unit = item.unit,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private data class StatItem(val label: String, val value: String, val unit: String)

private fun buildStatItems(stats: GpxStats): List<StatItem> {
    val items = mutableListOf<StatItem>()

    items.add(StatItem("Distance", formatDistance(stats.totalDistance), ""))
    items.add(StatItem("Duration", formatDuration(stats.totalDuration), ""))
    items.add(StatItem("Moving Time", formatDuration(stats.movingDuration), ""))
    items.add(StatItem("Avg Speed", "%.1f".format(stats.avgSpeed * 3.6), "km/h"))
    items.add(StatItem("Max Speed", "%.1f".format(stats.maxSpeed * 3.6), "km/h"))
    items.add(StatItem("Avg Pace", formatPace(stats.avgPace), "min/km"))
    items.add(StatItem("Elevation ↑", "%.0f".format(stats.totalElevationGain), "m"))
    items.add(StatItem("Elevation ↓", "%.0f".format(stats.totalElevationLoss), "m"))

    stats.avgHeartRate?.let {
        items.add(StatItem("Avg HR", "%.0f".format(it), "bpm"))
    }
    stats.maxHeartRate?.let {
        items.add(StatItem("Max HR", it.toString(), "bpm"))
    }
    stats.avgCadence?.let {
        items.add(StatItem("Avg Cadence", "%.0f".format(it), "rpm"))
    }
    stats.avgPower?.let {
        items.add(StatItem("Avg Power", "%.0f".format(it), "W"))
    }
    stats.maxPower?.let {
        items.add(StatItem("Max Power", it.toString(), "W"))
    }
    stats.avgTemperature?.let {
        items.add(StatItem("Avg Temp", "%.1f".format(it), "°C"))
    }
    items.add(StatItem("Avg Grade", "%.1f".format(stats.avgGrade), "%"))
    items.add(StatItem("Max Grade", "%.1f".format(stats.maxGrade), "%"))

    return items
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) "%.2f km".format(meters / 1000)
    else "%.0f m".format(meters)
}

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

private fun formatPace(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0 || paceMinPerKm.isInfinite() || paceMinPerKm.isNaN()) return "--:--"
    val minutes = paceMinPerKm.toInt()
    val seconds = ((paceMinPerKm - minutes) * 60).toInt()
    return "%d:%02d".format(minutes, seconds)
}
