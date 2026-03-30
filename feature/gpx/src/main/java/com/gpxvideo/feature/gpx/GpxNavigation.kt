package com.gpxvideo.feature.gpx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.lib.gpxparser.GpxStats

/**
 * GPX tab content composable — embedded in the project editor.
 * Shows GPX visualization when data is available, otherwise shows import prompt.
 */
@Composable
fun GpxTabContent(
    gpxData: GpxData?,
    stats: GpxStats?,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (gpxData != null && stats != null) {
        GpxVisualizationScreen(gpxData = gpxData, stats = stats, modifier = modifier)
    } else {
        EmptyGpxState(onImportClick = onImportClick, modifier = modifier)
    }
}

@Composable
private fun EmptyGpxState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No GPX Data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Import a GPX or TCX file to visualize your activity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onImportClick) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Import GPX File")
            }
        }
    }
}
