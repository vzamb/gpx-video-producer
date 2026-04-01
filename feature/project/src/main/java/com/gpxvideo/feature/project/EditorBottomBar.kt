package com.gpxvideo.feature.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class EditorTool(
    val label: String,
    val icon: ImageVector
) {
    MEDIA("Media", Icons.Default.PhotoLibrary),
    GPX_OVERLAYS("Overlays", Icons.Default.Route),
    GPX_FILE("GPX", Icons.Default.SpaceDashboard),
    TEXT("Text", Icons.Default.TextFields),
    EFFECTS("Effects", Icons.Default.AutoAwesome)
}

@Composable
fun EditorBottomBar(
    selectedTool: EditorTool?,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorTool.entries.forEach { tool ->
            val selected = selectedTool == tool
            val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToolSelected(tool) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.label,
                    modifier = Modifier.size(22.dp),
                    tint = tint
                )
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}
