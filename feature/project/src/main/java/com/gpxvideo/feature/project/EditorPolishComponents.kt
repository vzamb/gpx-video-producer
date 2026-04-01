package com.gpxvideo.feature.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.core.database.entity.GpxFileEntity
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.feature.gpx.AltitudeProfileCanvas
import com.gpxvideo.feature.gpx.GpxRouteCanvas
import com.gpxvideo.feature.gpx.StatsGrid
import com.gpxvideo.feature.timeline.ClipContentMode
import com.gpxvideo.feature.timeline.TimelineClipState
import com.gpxvideo.lib.gpxparser.GpxStats
import java.util.UUID

internal data class AspectRatioPreset(
    val label: String,
    val icon: String,  // emoji
    val width: Int,
    val height: Int
)

internal val ProjectAspectRatioPresets = listOf(
    AspectRatioPreset("YouTube", "\uD83C\uDFAC", 1920, 1080),       // 🎬 16:9
    AspectRatioPreset("TikTok / Reels", "\uD83D\uDCF1", 1080, 1920),// 📱 9:16
    AspectRatioPreset("Instagram", "\uD83D\uDCF7", 1080, 1080),     // 📷 1:1
    AspectRatioPreset("IG Feed", "\uD83D\uDCF8", 1080, 1350)        // 📸 4:5
)

internal fun formatAspectRatioLabel(width: Int, height: Int): String {
    val gcd = greatestCommonDivisor(width, height).coerceAtLeast(1)
    return "${width / gcd}:${height / gcd}"
}

/**
 * Compact dropdown popup anchored to the Canvas button.
 */
@Composable
internal fun AspectRatioDropdown(
    expanded: Boolean,
    currentWidth: Int,
    currentHeight: Int,
    onSelectPreset: (AspectRatioPreset) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        ProjectAspectRatioPresets.forEach { preset ->
            val isSelected = preset.width == currentWidth && preset.height == currentHeight
            val ratio = formatAspectRatioLabel(preset.width, preset.height)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                ratio,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                onClick = {
                    onSelectPreset(preset)
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GpxWorkspaceSheet(
    currentFile: GpxFileEntity?,
    gpxData: GpxData?,
    gpxStats: GpxStats?,
    isImporting: Boolean,
    onImportOrReplace: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var nameValue by remember(currentFile?.id, currentFile?.name) {
        mutableStateOf(currentFile?.name.orEmpty())
    }
    val allPoints = gpxData?.tracks?.flatMap { track -> track.segments.flatMap { it.points } }.orEmpty()
    val segmentCount = gpxData?.tracks?.sumOf { it.segments.size } ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPX Data", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onImportOrReplace, enabled = !isImporting) {
                        Text(if (currentFile == null) "Import" else "Replace")
                    }
                    if (currentFile != null) {
                        TextButton(onClick = onDelete) { Text("Remove") }
                    }
                }
            }

            if (gpxData != null && gpxStats != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Route map — prominent card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Color(0xFF0D1117),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    GpxRouteCanvas(
                        points = allPoints,
                        bounds = gpxData.bounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Altitude profile — prominent card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Color(0xFF0D1117),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AltitudeProfileCanvas(
                        points = allPoints,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Statistics in visual grid
                Text("Activity Stats", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                StatsGrid(
                    stats = gpxStats,
                    modifier = Modifier.fillMaxWidth()
                )

                // File info
                if (currentFile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nameValue,
                            onValueChange = { nameValue = it },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = { onRename(nameValue) },
                            enabled = nameValue.isNotBlank() && nameValue != currentFile.name
                        ) {
                            Text("Save")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetadataChip("${gpxData.tracks.size} tracks")
                    MetadataChip("$segmentCount segments")
                    MetadataChip("${allPoints.size} points")
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (currentFile == null) "No GPX attached yet."
                        else "GPX data could not be parsed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetadataChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
internal fun ClipFramingControls(
    selectedClip: TimelineClipState,
    selectedTrackType: TrackType?,
    onContentModeChange: (UUID, ClipContentMode) -> Unit,
    onFrameTransformChange: (UUID, Float, Float, Float, Float) -> Unit
) {
    val isVisualClip = selectedTrackType == TrackType.VIDEO || selectedTrackType == TrackType.IMAGE
    if (!isVisualClip) return

    Spacer(modifier = Modifier.height(12.dp))
    Text("Framing", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClipContentMode.entries.forEach { mode ->
            val label = when (mode) {
                ClipContentMode.FIT -> "Adapt"
                ClipContentMode.FILL -> "Fill"
                ClipContentMode.CROP -> "Crop"
            }
            val icon = when (mode) {
                ClipContentMode.FIT -> "↔"
                ClipContentMode.FILL -> "⬛"
                ClipContentMode.CROP -> "✂"
            }
            Column(
                modifier = Modifier
                    .clickable { onContentModeChange(selectedClip.id, mode) }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    var scaleValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.scale.coerceAtLeast(0.1f)) }
    var rotationValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.rotation) }
    var panXValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.positionX) }
    var panYValue by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.positionY) }

    Spacer(modifier = Modifier.height(8.dp))
    FramingSliderRow("Rotation", rotationValue, -180f..180f, { "${it.toInt()}°" },
        onValueChange = { rotationValue = it },
        onValueChangeFinished = { onFrameTransformChange(selectedClip.id, panXValue, panYValue, scaleValue, rotationValue) })
    FramingSliderRow("Zoom", scaleValue, 0.1f..3f, { "${"%.1f".format(it)}x" },
        onValueChange = { scaleValue = it },
        onValueChangeFinished = { onFrameTransformChange(selectedClip.id, panXValue, panYValue, scaleValue, rotationValue) })
    FramingSliderRow("Pan X", panXValue, 0f..1f, { "${"%.0f".format(it * 100)}%" },
        onValueChange = { panXValue = it },
        onValueChangeFinished = { onFrameTransformChange(selectedClip.id, panXValue, panYValue, scaleValue, rotationValue) })
    FramingSliderRow("Pan Y", panYValue, 0f..1f, { "${"%.0f".format(it * 100)}%" },
        onValueChange = { panYValue = it },
        onValueChangeFinished = { onFrameTransformChange(selectedClip.id, panXValue, panYValue, scaleValue, rotationValue) })
}

@Composable
private fun FramingSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatValue: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.weight(1f).height(32.dp)
        )
        Text(
            text = formatValue(value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
    }
}

private fun greatestCommonDivisor(a: Int, b: Int): Int {
    var x = kotlin.math.abs(a)
    var y = kotlin.math.abs(b)
    while (y != 0) {
        val temp = x % y
        x = y
        y = temp
    }
    return x
}
