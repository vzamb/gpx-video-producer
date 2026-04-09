package com.gpxvideo.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.Transition
import com.gpxvideo.core.model.TransitionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionEditor(
    transition: Transition?,
    onTransitionChanged: (Transition?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedType = transition?.type
    val durationMs = transition?.durationMs ?: 500L

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Transition",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transition type selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedType?.toDisplayName() ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        selectedType?.let {
                            Icon(
                                imageVector = it.toIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // "None" option
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            onTransitionChanged(null)
                            expanded = false
                        }
                    )

                    TransitionType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = type.toIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(type.toDisplayName())
                                }
                            },
                            onClick = {
                                onTransitionChanged(Transition(type = type, durationMs = durationMs))
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Duration slider (only shown when a transition is selected)
            if (selectedType != null && selectedType != TransitionType.CUT) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Duration: ${durationMs}ms",
                    style = MaterialTheme.typography.labelSmall
                )

                Slider(
                    value = durationMs.toFloat(),
                    onValueChange = { newDuration ->
                        onTransitionChanged(
                            Transition(
                                type = selectedType,
                                durationMs = newDuration.toLong()
                            )
                        )
                    },
                    valueRange = 100f..2000f,
                    steps = 18,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("100ms", style = MaterialTheme.typography.labelSmall)
                    Text("2000ms", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (transition != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { onTransitionChanged(null) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Remove Transition")
                }
            }
        }
    }
}

internal fun TransitionType.toDisplayName(): String = when (this) {
    TransitionType.CUT -> "Cut"
    TransitionType.FADE -> "Fade"
    TransitionType.DISSOLVE -> "Dissolve"
    TransitionType.SLIDE_LEFT -> "Slide Left"
    TransitionType.SLIDE_RIGHT -> "Slide Right"
    TransitionType.WIPE_LEFT -> "Wipe Left"
    TransitionType.WIPE_RIGHT -> "Wipe Right"
}

internal fun TransitionType.toIcon(): ImageVector = when (this) {
    TransitionType.CUT -> Icons.Outlined.ContentCut
    TransitionType.FADE -> Icons.Outlined.Gradient
    TransitionType.DISSOLVE -> Icons.Outlined.BlurOn
    TransitionType.SLIDE_LEFT -> Icons.AutoMirrored.Outlined.ArrowBack
    TransitionType.SLIDE_RIGHT -> Icons.AutoMirrored.Outlined.ArrowForward
    TransitionType.WIPE_LEFT -> Icons.AutoMirrored.Outlined.ArrowBack
    TransitionType.WIPE_RIGHT -> Icons.AutoMirrored.Outlined.ArrowForward
}

internal fun TransitionType.toColor(): androidx.compose.ui.graphics.Color = when (this) {
    TransitionType.CUT -> androidx.compose.ui.graphics.Color(0xFF9E9E9E)
    TransitionType.FADE -> androidx.compose.ui.graphics.Color(0xFFFF9800)
    TransitionType.DISSOLVE -> androidx.compose.ui.graphics.Color(0xFF2196F3)
    TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    TransitionType.WIPE_LEFT, TransitionType.WIPE_RIGHT -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
}
