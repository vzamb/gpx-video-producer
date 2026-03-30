package com.gpxvideo.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClipPropertiesPanel(
    clip: TimelineClipState,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMs = clip.endTimeMs - clip.startTimeMs

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Clip info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = clip.label,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed slider
            Text(
                text = "Speed: %.2fx".format(1.0f),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = 1.0f,
                onValueChange = onSpeedChange,
                valueRange = 0.25f..4.0f,
                steps = 14,
                modifier = Modifier.fillMaxWidth()
            )

            // Volume slider
            Text(
                text = "Volume: %d%%".format(100),
                style = MaterialTheme.typography.labelSmall
            )
            Slider(
                value = 1.0f,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onSplit) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Split"
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
