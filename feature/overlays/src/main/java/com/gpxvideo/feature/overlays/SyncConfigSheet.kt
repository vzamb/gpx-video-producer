package com.gpxvideo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.SyncMode
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConfigSheet(
    syncMode: SyncMode,
    timeOffsetMs: Long,
    keyframes: List<SyncKeyframe>,
    gpxData: GpxData,
    onSyncModeChanged: (SyncMode) -> Unit,
    onTimeOffsetChanged: (Long) -> Unit,
    onKeyframeAdded: (SyncKeyframe) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tabs = listOf("Auto (Timestamp)", "Manual (Keyframes)")
    var selectedTab by remember {
        mutableIntStateOf(if (syncMode == SyncMode.GPX_TIMESTAMP) 0 else 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "GPX Sync Configuration",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // GPX start time hint
            gpxData.startTime?.let { startTime ->
                val formatted = DateTimeFormatter.ISO_INSTANT.format(startTime)
                Text(
                    text = "GPX starts at: $formatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            val mode = if (index == 0) SyncMode.GPX_TIMESTAMP else SyncMode.MANUAL_KEYFRAMES
                            onSyncModeChanged(mode)
                        },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> AutoSyncTab(timeOffsetMs, onTimeOffsetChanged)
                1 -> ManualSyncTab(keyframes, gpxData, onKeyframeAdded)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AutoSyncTab(
    timeOffsetMs: Long,
    onTimeOffsetChanged: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Time Offset",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Adjust the offset between video timeline and GPX timestamps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Current offset display
        val sign = if (timeOffsetMs >= 0) "+" else "-"
        val absMs = abs(timeOffsetMs)
        val hours = absMs / 3_600_000
        val minutes = (absMs % 3_600_000) / 60_000
        val seconds = (absMs % 60_000) / 1_000
        val offsetText = "${sign}${hours}h ${minutes}m ${seconds}s"

        Text(
            text = offsetText,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Coarse: ±12 hours
        Text(text = "Hours", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = (timeOffsetMs / 3_600_000f).coerceIn(-12f, 12f),
            onValueChange = { hrs ->
                val remainder = timeOffsetMs % 3_600_000
                onTimeOffsetChanged((hrs * 3_600_000).roundToLong() + remainder)
            },
            valueRange = -12f..12f,
            steps = 23
        )

        // Fine: ±59 minutes
        Text(text = "Minutes", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = ((timeOffsetMs % 3_600_000) / 60_000f).coerceIn(-59f, 59f),
            onValueChange = { mins ->
                val hourPart = (timeOffsetMs / 3_600_000) * 3_600_000
                val secPart = timeOffsetMs % 60_000
                onTimeOffsetChanged(hourPart + (mins * 60_000).roundToLong() + secPart)
            },
            valueRange = -59f..59f,
            steps = 117
        )

        // Fine: ±59 seconds
        Text(text = "Seconds", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = ((timeOffsetMs % 60_000) / 1_000f).coerceIn(-59f, 59f),
            onValueChange = { secs ->
                val mainPart = timeOffsetMs - (timeOffsetMs % 60_000)
                onTimeOffsetChanged(mainPart + (secs * 1_000).roundToLong())
            },
            valueRange = -59f..59f,
            steps = 117
        )
    }
}

@Composable
private fun ManualSyncTab(
    keyframes: List<SyncKeyframe>,
    gpxData: GpxData,
    onKeyframeAdded: (SyncKeyframe) -> Unit
) {
    val allPoints = remember(gpxData) {
        gpxData.tracks.flatMap { it.segments.flatMap { s -> s.points } }
    }
    val totalPoints = allPoints.size

    var newVideoTimeMs by remember { mutableLongStateOf(0L) }
    var newGpxPointIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Keyframes",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Map specific video times to GPX points for precise synchronization.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Existing keyframes
        if (keyframes.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(keyframes) { _, kf ->
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Video: ${formatMs(kf.videoTimeMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "→ Point #${kf.gpxPointIndex}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No keyframes defined yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add new keyframe
        Text(text = "New Keyframe", style = MaterialTheme.typography.titleSmall)

        Text(text = "Video time (seconds)", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = (newVideoTimeMs / 1000f),
            onValueChange = { newVideoTimeMs = (it * 1000).roundToLong() },
            valueRange = 0f..3600f
        )
        Text(
            text = formatMs(newVideoTimeMs),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (totalPoints > 0) {
            Text(text = "GPX point index (0..${totalPoints - 1})", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = newGpxPointIndex.toFloat(),
                onValueChange = { newGpxPointIndex = it.toInt() },
                valueRange = 0f..(totalPoints - 1).coerceAtLeast(1).toFloat()
            )
            Text(
                text = "Point #$newGpxPointIndex",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FilledTonalButton(
            onClick = {
                onKeyframeAdded(SyncKeyframe(newVideoTimeMs, newGpxPointIndex))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add keyframe")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Keyframe")
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = abs(ms) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
