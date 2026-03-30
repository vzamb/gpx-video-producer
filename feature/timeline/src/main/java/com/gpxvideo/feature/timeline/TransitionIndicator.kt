package com.gpxvideo.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.Transition

private val INDICATOR_SIZE = 24.dp

private val DiamondShape = GenericShape { size, _ ->
    val halfW = size.width / 2f
    val halfH = size.height / 2f
    moveTo(halfW, 0f)
    lineTo(size.width, halfH)
    lineTo(halfW, size.height)
    lineTo(0f, halfH)
    close()
}

@Composable
fun TransitionIndicator(
    transition: Transition,
    pxPerMs: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor = transition.type.toColor()

    Box(
        modifier = modifier
            .size(INDICATOR_SIZE)
            .clip(DiamondShape)
            .background(indicatorColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = transition.type.toIcon(),
            contentDescription = transition.type.toDisplayName(),
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}
