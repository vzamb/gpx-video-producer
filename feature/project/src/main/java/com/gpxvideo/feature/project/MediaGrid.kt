package com.gpxvideo.feature.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.gpxvideo.core.common.formatDuration
import com.gpxvideo.core.database.entity.MediaItemEntity
import java.io.File
import java.util.UUID

@Composable
fun MediaGrid(
    mediaItems: List<MediaItemEntity>,
    onDeleteMedia: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    val context = LocalContext.current
    val thumbnailDir = File(context.cacheDir, "thumbnails")

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = mediaItems,
            key = { it.id }
        ) { item ->
            MediaGridItem(
                mediaItem = item,
                thumbnailFile = File(thumbnailDir, "${item.id}.jpg"),
                onDelete = { onDeleteMedia(item.id) }
            )
        }
    }
}

@Composable
private fun MediaGridItem(
    mediaItem: MediaItemEntity,
    thumbnailFile: File,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
    ) {
        // Thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailFile)
                .build(),
            contentDescription = "Media thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Delete button (top-right)
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete media",
                modifier = Modifier.size(16.dp)
            )
        }

        // Bottom overlay: media type icon + duration
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = if (mediaItem.type == "VIDEO") {
                    Icons.Default.Videocam
                } else {
                    Icons.Default.Image
                },
                contentDescription = mediaItem.type,
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )

            if (mediaItem.type == "VIDEO") {
                val duration = mediaItem.durationMs
                if (duration != null) {
                    Text(
                        text = duration.formatDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}
