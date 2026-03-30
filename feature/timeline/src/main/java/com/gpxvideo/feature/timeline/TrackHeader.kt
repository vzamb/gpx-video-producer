package com.gpxvideo.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.TrackType

internal val TRACK_HEADER_WIDTH = 120.dp
private val TRACK_ROW_HEIGHT = 56.dp

@Composable
fun TrackHeader(
    track: TimelineTrackState,
    isSelected: Boolean,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Row(
        modifier = modifier
            .width(TRACK_HEADER_WIDTH)
            .height(TRACK_ROW_HEIGHT)
            .background(bgColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = track.type.toIcon(),
            contentDescription = track.type.name,
            modifier = Modifier.size(18.dp),
            tint = track.type.toColor()
        )

        Text(
            text = track.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp)
        )

        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = if (track.isVisible) Icons.Default.Visibility
                else Icons.Default.VisibilityOff,
                contentDescription = "Toggle visibility",
                modifier = Modifier.size(14.dp)
            )
        }

        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Toggle lock",
                modifier = Modifier.size(14.dp)
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete track",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun TrackType.toIcon(): ImageVector = when (this) {
    TrackType.VIDEO -> Icons.Default.Videocam
    TrackType.IMAGE -> Icons.Default.Image
    TrackType.AUDIO -> Icons.Default.Audiotrack
    TrackType.OVERLAY -> Icons.Default.Layers
    TrackType.TEXT -> Icons.Default.TextFields
}
