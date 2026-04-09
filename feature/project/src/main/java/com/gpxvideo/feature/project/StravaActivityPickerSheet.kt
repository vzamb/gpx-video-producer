package com.gpxvideo.feature.project

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpxvideo.lib.strava.StravaActivity
import com.gpxvideo.lib.strava.StravaApi
import com.gpxvideo.lib.strava.StravaAuth
import com.gpxvideo.lib.strava.StravaTokenStore
import com.gpxvideo.lib.strava.StravaTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val StravaOrange = Color(0xFFFC4C02)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaActivityPickerSheet(
    isLinked: Boolean,
    stravaAuth: StravaAuth,
    stravaApi: StravaApi,
    onActivitySelected: (StravaActivity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activities = remember { mutableStateListOf<StravaActivity>() }
    var isLoading by remember { mutableStateOf(false) }
    var loadingActivityId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }

    fun loadActivities(page: Int) {
        scope.launch {
            isLoading = true
            error = null
            val result = stravaApi.listActivities(page = page, perPage = 30)
            result.onSuccess { list ->
                if (page == 1) activities.clear()
                activities.addAll(list)
                hasMore = list.size == 30
                currentPage = page
            }.onFailure {
                error = it.message ?: "Failed to load activities"
            }
            isLoading = false
        }
    }

    // Load on first display if linked
    LaunchedEffect(isLinked) {
        if (isLinked) loadActivities(1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Import from Strava",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (!isLinked) {
                // Not connected — show connect button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connect your Strava account to import activities directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Button(
                        onClick = {
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(context, stravaAuth.buildAuthUri())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StravaOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect with Strava", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Connected — show activity list
                if (error != null && activities.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { loadActivities(1) }) {
                            Text("Retry")
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= activities.size - 3 && !isLoading && hasMore
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) loadActivities(currentPage + 1)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activities, key = { it.id }) { activity ->
                            StravaActivityCard(
                                activity = activity,
                                isImporting = loadingActivityId == activity.id,
                                onClick = {
                                    loadingActivityId = activity.id
                                    onActivitySelected(activity)
                                }
                            )
                        }

                        if (isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = StravaOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StravaActivityCard(
    activity: StravaActivity,
    isImporting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isImporting) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = sportIcon(activity.type),
                contentDescription = null,
                tint = StravaOrange,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = formatDate(activity.startDateLocal),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatDistance(activity.distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatDuration(activity.movingTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = StravaOrange
                )
            }
        }
    }
}

private fun sportIcon(type: String): ImageVector = when (type.lowercase()) {
    "run", "trail run", "virtualrun" -> Icons.Outlined.DirectionsRun
    "ride", "virtualride", "ebikeride", "handcycle" -> Icons.Outlined.DirectionsBike
    "swim" -> Icons.Outlined.Pool
    "hike" -> Icons.Outlined.Hiking
    "walk" -> Icons.Outlined.DirectionsWalk
    "alpineski", "backcountryski", "nordicski", "snowboard" -> Icons.Outlined.DownhillSkiing
    "weighttraining", "crossfit", "yoga" -> Icons.Outlined.FitnessCenter
    else -> Icons.Outlined.Landscape
}

private fun formatDate(isoDate: String): String {
    return try {
        val instant = Instant.parse(isoDate.replace(" ", "T"))
        val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        local.format(DateTimeFormatter.ofPattern("MMM d"))
    } catch (_: Exception) {
        try {
            val local = LocalDateTime.parse(isoDate.take(19))
            local.format(DateTimeFormatter.ofPattern("MMM d"))
        } catch (_: Exception) {
            isoDate.take(10)
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        "%.1f km".format(meters / 1000)
    } else {
        "${meters.toInt()} m"
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
