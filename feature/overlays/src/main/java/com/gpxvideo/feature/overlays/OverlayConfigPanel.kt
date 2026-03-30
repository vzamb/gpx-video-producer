package com.gpxvideo.feature.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.DynamicField
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.OverlayPosition
import com.gpxvideo.core.model.OverlaySize
import com.gpxvideo.core.model.OverlayStyle
import com.gpxvideo.core.model.StatField
import com.gpxvideo.core.model.StatsLayout
import com.gpxvideo.core.model.SyncMode

@Composable
fun OverlayConfigPanel(
    overlay: OverlayConfig,
    onUpdate: (OverlayConfig) -> Unit,
    onDelete: () -> Unit,
    onOpenSyncConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = overlay.name, style = MaterialTheme.typography.titleMedium)

        when (overlay) {
            is OverlayConfig.StaticAltitudeProfile -> AltitudeProfileConfig(overlay, onUpdate)
            is OverlayConfig.StaticMap -> StaticMapConfig(overlay, onUpdate)
            is OverlayConfig.StaticStats -> StaticStatsConfig(overlay, onUpdate)
            is OverlayConfig.DynamicAltitudeProfile -> DynamicAltitudeProfileConfig(
                overlay, onUpdate, onOpenSyncConfig
            )
            is OverlayConfig.DynamicMap -> DynamicMapConfig(overlay, onUpdate, onOpenSyncConfig)
            is OverlayConfig.DynamicStat -> DynamicStatConfig(overlay, onUpdate, onOpenSyncConfig)
        }

        SectionHeader("Position")
        LabeledSlider("X", overlay.position.x, 0f..1f) { value ->
            onUpdate(updatePosition(overlay, overlay.position.copy(x = value)))
        }
        LabeledSlider("Y", overlay.position.y, 0f..1f) { value ->
            onUpdate(updatePosition(overlay, overlay.position.copy(y = value)))
        }

        SectionHeader("Size")
        LabeledSlider("Width", overlay.size.width, 0.05f..1f) { value ->
            onUpdate(updateSize(overlay, overlay.size.copy(width = value)))
        }
        LabeledSlider("Height", overlay.size.height, 0.05f..1f) { value ->
            onUpdate(updateSize(overlay, overlay.size.copy(height = value)))
        }

        SectionHeader("Style")
        LabeledSlider("Opacity", overlay.style.opacity, 0f..1f) { value ->
            onUpdate(updateStyle(overlay, overlay.style.copy(opacity = value)))
        }
        LabeledSlider("Corner Radius", overlay.style.cornerRadius, 0f..32f) { value ->
            onUpdate(updateStyle(overlay, overlay.style.copy(cornerRadius = value)))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
            Text("Delete Overlay", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AltitudeProfileConfig(
    overlay: OverlayConfig.StaticAltitudeProfile,
    onUpdate: (OverlayConfig) -> Unit
) {
    SectionHeader("Altitude Profile Settings")
    ToggleRow("Show Grid", overlay.showGrid) {
        onUpdate(overlay.copy(showGrid = it))
    }
    ToggleRow("Show Labels", overlay.showLabels) {
        onUpdate(overlay.copy(showLabels = it))
    }
}

@Composable
private fun StaticMapConfig(
    overlay: OverlayConfig.StaticMap,
    onUpdate: (OverlayConfig) -> Unit
) {
    SectionHeader("Map Settings")
    LabeledSlider("Route Width", overlay.routeWidth, 1f..10f) {
        onUpdate(overlay.copy(routeWidth = it))
    }
    ToggleRow("Show Start/End", overlay.showStartEnd) {
        onUpdate(overlay.copy(showStartEnd = it))
    }
    MapStyleDropdown(overlay.mapStyle) {
        onUpdate(overlay.copy(mapStyle = it))
    }
}

@Composable
private fun StaticStatsConfig(
    overlay: OverlayConfig.StaticStats,
    onUpdate: (OverlayConfig) -> Unit
) {
    SectionHeader("Stats Fields")
    StatField.entries.forEach { field ->
        val checked = field in overlay.fields
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = checked, onCheckedChange = { isChecked ->
                val newFields = if (isChecked) overlay.fields + field else overlay.fields - field
                onUpdate(overlay.copy(fields = newFields))
            })
            Text(text = field.displayName, style = MaterialTheme.typography.bodyMedium)
        }
    }

    SectionHeader("Layout")
    StatsLayoutDropdown(overlay.layout) {
        onUpdate(overlay.copy(layout = it))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStyleDropdown(
    selected: MapStyle,
    onSelect: (MapStyle) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Map Style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.name) },
                    onClick = {
                        onSelect(style)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DynamicAltitudeProfileConfig(
    overlay: OverlayConfig.DynamicAltitudeProfile,
    onUpdate: (OverlayConfig) -> Unit,
    onOpenSyncConfig: () -> Unit
) {
    SectionHeader("Live Altitude Settings")
    SyncModeButton(overlay.syncMode.name, onOpenSyncConfig)
}

@Composable
private fun DynamicMapConfig(
    overlay: OverlayConfig.DynamicMap,
    onUpdate: (OverlayConfig) -> Unit,
    onOpenSyncConfig: () -> Unit
) {
    SectionHeader("Live Map Settings")
    MapStyleDropdown(overlay.mapStyle) {
        onUpdate(overlay.copy(mapStyle = it))
    }
    ToggleRow("Follow Position", overlay.followPosition) {
        onUpdate(overlay.copy(followPosition = it))
    }
    ToggleRow("Show Trail", overlay.showTrail) {
        onUpdate(overlay.copy(showTrail = it))
    }
    SyncModeButton(overlay.syncMode.name, onOpenSyncConfig)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicStatConfig(
    overlay: OverlayConfig.DynamicStat,
    onUpdate: (OverlayConfig) -> Unit,
    onOpenSyncConfig: () -> Unit
) {
    SectionHeader("Live Stat Settings")

    // Field selector
    var fieldExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = fieldExpanded, onExpandedChange = { fieldExpanded = it }) {
        OutlinedTextField(
            value = overlay.field.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Field") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldExpanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
            DynamicField.entries.forEach { field ->
                DropdownMenuItem(
                    text = { Text("${field.displayName} (${field.defaultUnit})") },
                    onClick = {
                        onUpdate(overlay.copy(field = field))
                        fieldExpanded = false
                    }
                )
            }
        }
    }

    // Format pattern
    OutlinedTextField(
        value = overlay.format,
        onValueChange = { onUpdate(overlay.copy(format = it)) },
        label = { Text("Format Pattern") },
        placeholder = { Text("e.g., %.1f") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    SyncModeButton(overlay.syncMode.name, onOpenSyncConfig)
}

@Composable
private fun SyncModeButton(currentMode: String, onOpenSyncConfig: () -> Unit) {
    Button(
        onClick = onOpenSyncConfig,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Sync: ${currentMode.replace("_", " ")}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsLayoutDropdown(
    selected: StatsLayout,
    onSelect: (StatsLayout) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.replace("_", " "),
            onValueChange = {},
            readOnly = true,
            label = { Text("Layout") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatsLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.name.replace("_", " ")) },
                    onClick = {
                        onSelect(layout)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = "%.2f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun updatePosition(config: OverlayConfig, position: OverlayPosition): OverlayConfig = when (config) {
    is OverlayConfig.StaticAltitudeProfile -> config.copy(position = position)
    is OverlayConfig.StaticMap -> config.copy(position = position)
    is OverlayConfig.StaticStats -> config.copy(position = position)
    is OverlayConfig.DynamicAltitudeProfile -> config.copy(position = position)
    is OverlayConfig.DynamicMap -> config.copy(position = position)
    is OverlayConfig.DynamicStat -> config.copy(position = position)
}

private fun updateSize(config: OverlayConfig, size: OverlaySize): OverlayConfig = when (config) {
    is OverlayConfig.StaticAltitudeProfile -> config.copy(size = size)
    is OverlayConfig.StaticMap -> config.copy(size = size)
    is OverlayConfig.StaticStats -> config.copy(size = size)
    is OverlayConfig.DynamicAltitudeProfile -> config.copy(size = size)
    is OverlayConfig.DynamicMap -> config.copy(size = size)
    is OverlayConfig.DynamicStat -> config.copy(size = size)
}

private fun updateStyle(config: OverlayConfig, style: OverlayStyle): OverlayConfig = when (config) {
    is OverlayConfig.StaticAltitudeProfile -> config.copy(style = style)
    is OverlayConfig.StaticMap -> config.copy(style = style)
    is OverlayConfig.StaticStats -> config.copy(style = style)
    is OverlayConfig.DynamicAltitudeProfile -> config.copy(style = style)
    is OverlayConfig.DynamicMap -> config.copy(style = style)
    is OverlayConfig.DynamicStat -> config.copy(style = style)
}
