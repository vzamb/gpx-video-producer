package com.gpxvideo.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.UUID

data class OverlayPosition(
    val x: Float,
    val y: Float
)

data class OverlaySize(
    val width: Float,
    val height: Float
)

data class OverlayStyle(
    val backgroundColor: Color = Color(0x44FFFFFF),
    val borderColor: Color = Color.White,
    val label: String = ""
)

data class OverlayRenderData(
    val id: UUID,
    val type: String,
    val position: OverlayPosition,
    val size: OverlaySize,
    val style: OverlayStyle,
    val isVisible: Boolean,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Composable
fun PreviewOverlayRenderer(
    overlays: List<OverlayRenderData>,
    currentPositionMs: Long,
    videoWidth: Int,
    videoHeight: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Box(modifier = modifier) {
        overlays
            .filter { it.isVisible && currentPositionMs in it.startTimeMs..it.endTimeMs }
            .forEach { overlay ->
                val offsetX = with(density) { (overlay.position.x * videoWidth).toDp() }
                val offsetY = with(density) { (overlay.position.y * videoHeight).toDp() }
                val width = with(density) { (overlay.size.width * videoWidth).toDp() }
                val height = with(density) { (overlay.size.height * videoHeight).toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .size(width = width, height = height)
                        .background(overlay.style.backgroundColor)
                        .border(1.dp, overlay.style.borderColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = overlay.style.label.ifEmpty { overlay.type },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
    }
}
