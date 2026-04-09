package com.gpxvideo.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Kayaking
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Snowboarding
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.core.model.SportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxVideoTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun SportTypeIcon(
    sportType: SportType,
    modifier: Modifier = Modifier,
    size: Int = 24
) {
    Icon(
        imageVector = sportType.outlinedIcon(),
        contentDescription = sportType.displayName,
        modifier = modifier.size(size.dp)
    )
}

fun SportType.outlinedIcon(): ImageVector = when (this) {
    SportType.CYCLING -> Icons.Outlined.DirectionsBike
    SportType.RUNNING -> Icons.Outlined.DirectionsRun
    SportType.HIKING -> Icons.Outlined.Hiking
    SportType.TRAIL_RUNNING -> Icons.Outlined.Terrain
    SportType.SKIING -> Icons.Outlined.DownhillSkiing
    SportType.SNOWBOARDING -> Icons.Outlined.Snowboarding
    SportType.SWIMMING -> Icons.Outlined.Pool
    SportType.KAYAKING -> Icons.Outlined.Kayaking
    SportType.CLIMBING -> Icons.Outlined.Terrain
    SportType.MULTI_SPORT -> Icons.Outlined.SportsScore
    SportType.OTHER -> Icons.Outlined.FitnessCenter
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
