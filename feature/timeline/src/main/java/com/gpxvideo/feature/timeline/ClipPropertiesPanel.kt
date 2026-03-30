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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.Transition

@Composable
fun ClipPropertiesPanel(
    clip: TimelineClipState,
    trackType: TrackType,
    hasAdjacentClips: Boolean,
    entryTransition: Transition?,
    exitTransition: Transition?,
    waveformData: List<Float>,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onEntryTransitionChanged: (Transition?) -> Unit,
    onExitTransitionChanged: (Transition?) -> Unit,
    onKenBurnsConfigChanged: ((KenBurnsConfig) -> Unit)?,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier,
    kenBurnsConfig: KenBurnsConfig? = null
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

            // Audio waveform display
            if (waveformData.isNotEmpty()) {
                Text(
                    text = "Waveform",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                AudioWaveform(
                    waveformData = waveformData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    color = if (trackType == TrackType.AUDIO) Color(0xFF4CAF50) else Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

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

            // Volume control (always visible for VIDEO and AUDIO clips)
            if (trackType == TrackType.VIDEO || trackType == TrackType.AUDIO) {
                Text(
                    text = "Volume",
                    style = MaterialTheme.typography.labelSmall
                )
                VolumeControl(
                    volume = 1.0f,
                    onVolumeChange = onVolumeChange,
                    accentColor = if (trackType == TrackType.AUDIO) Color(0xFF4CAF50)
                    else Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Transitions section (when clip has adjacent clips)
            if (hasAdjacentClips) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Transitions",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Entry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TransitionEditor(
                    transition = entryTransition,
                    onTransitionChanged = onEntryTransitionChanged
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TransitionEditor(
                    transition = exitTransition,
                    onTransitionChanged = onExitTransitionChanged
                )
            }

            // Ken Burns section for IMAGE clips
            if (trackType == TrackType.IMAGE && onKenBurnsConfigChanged != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                KenBurnsEditor(
                    config = kenBurnsConfig ?: KenBurnsConfig(),
                    onConfigChanged = onKenBurnsConfigChanged
                )
            }

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
