package com.gpxvideo.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.math.roundToInt

private val TRIM_HANDLE_WIDTH = 8.dp
private val CLIP_CORNER_RADIUS = 6.dp

@Composable
fun TimelineClipView(
    clip: TimelineClipState,
    pxPerMs: Float,
    isSelected: Boolean,
    isTrackVisible: Boolean,
    onSelected: () -> Unit,
    onMoved: (deltaMs: Long) -> Unit,
    onTrimStart: (deltaMs: Long) -> Unit,
    onTrimEnd: (deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipWidthPx = ((clip.endTimeMs - clip.startTimeMs) * pxPerMs)
    val clipWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        clipWidthPx.toDp()
    }

    val shape = RoundedCornerShape(CLIP_CORNER_RADIUS)

    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .width(clipWidthDp)
            .fillMaxHeight()
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .alpha(if (isTrackVisible) 1f else 0.4f)
            .clip(shape)
            .background(clip.color.copy(alpha = 0.85f))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.White, shape)
                } else {
                    Modifier
                }
            )
            .pointerInput(clip.id) {
                detectTapGestures { onSelected() }
            }
            .pointerInput(clip.id, pxPerMs) {
                detectDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragEnd = {
                        val deltaMs = (dragOffsetX / pxPerMs).toLong()
                        dragOffsetX = 0f
                        if (deltaMs != 0L) onMoved(deltaMs)
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                    }
                )
            }
    ) {
        // Clip label
        Text(
            text = clip.label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp)
        )

        // Left trim handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(TRIM_HANDLE_WIDTH)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
                .pointerInput(clip.id, pxPerMs) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount.x / pxPerMs).toLong()
                            if (deltaMs != 0L) onTrimStart(deltaMs)
                        }
                    )
                }
        )

        // Right trim handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(TRIM_HANDLE_WIDTH)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
                .pointerInput(clip.id, pxPerMs) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMs = (dragAmount.x / pxPerMs).toLong()
                            if (deltaMs != 0L) onTrimEnd(deltaMs)
                        }
                    )
                }
        )
    }
}
