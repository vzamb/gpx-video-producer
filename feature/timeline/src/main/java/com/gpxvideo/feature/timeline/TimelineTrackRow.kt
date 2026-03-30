package com.gpxvideo.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt

private val TRACK_HEIGHT = 56.dp

@Composable
fun TimelineTrackRow(
    track: TimelineTrackState,
    zoomLevel: Float,
    pxPerMs: Float,
    selectedClipId: UUID?,
    onClipSelected: (UUID) -> Unit,
    onClipMoved: (UUID, Long) -> Unit,
    onClipTrimmed: (UUID, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
    ) {
        track.clips.forEach { clip ->
            val offsetPx = clip.startTimeMs * pxPerMs

            TimelineClipView(
                clip = clip,
                pxPerMs = pxPerMs,
                isSelected = clip.id == selectedClipId,
                isTrackVisible = track.isVisible,
                onSelected = { onClipSelected(clip.id) },
                onMoved = { deltaMs ->
                    onClipMoved(clip.id, clip.startTimeMs + deltaMs)
                },
                onTrimStart = { deltaMs ->
                    onClipTrimmed(
                        clip.id,
                        clip.startTimeMs + deltaMs,
                        clip.endTimeMs
                    )
                },
                onTrimEnd = { deltaMs ->
                    onClipTrimmed(
                        clip.id,
                        clip.startTimeMs,
                        clip.endTimeMs + deltaMs
                    )
                },
                modifier = Modifier.offset {
                    IntOffset(offsetPx.roundToInt(), 0)
                }
            )
        }
    }
}
