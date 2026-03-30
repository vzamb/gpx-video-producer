package com.gpxvideo.feature.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gpxvideo.core.ui.component.GpxVideoTopAppBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val UNITS = stringPreferencesKey("units")
    val SPORT_TYPE = stringPreferencesKey("default_sport_type")
    val THEME = stringPreferencesKey("theme")
    val PREVIEW_QUALITY = intPreferencesKey("preview_quality")
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableLongStateOf(calculateCacheSize(context)) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var units by remember { mutableStateOf("Metric") }
    var theme by remember { mutableStateOf("System") }
    var previewQuality by remember { mutableStateOf("Medium") }
    var defaultSportType by remember { mutableStateOf("Cycling") }

    // Load settings
    androidx.compose.runtime.LaunchedEffect(Unit) {
        context.settingsDataStore.data.first().let { prefs ->
            units = prefs[SettingsKeys.UNITS] ?: "Metric"
            theme = prefs[SettingsKeys.THEME] ?: "System"
            defaultSportType = prefs[SettingsKeys.SPORT_TYPE] ?: "Cycling"
            previewQuality = when (prefs[SettingsKeys.PREVIEW_QUALITY]) {
                0 -> "Low"
                2 -> "High"
                else -> "Medium"
            }
        }
    }

    Scaffold(
        topBar = {
            GpxVideoTopAppBar(
                title = "Settings",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Units
            SettingsSection(title = "General") {
                SettingsCycleItem(
                    title = "Units",
                    currentValue = units,
                    options = listOf("Metric", "Imperial"),
                    onValueChange = { newValue ->
                        units = newValue
                        scope.launch {
                            context.settingsDataStore.edit { it[SettingsKeys.UNITS] = newValue }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsCycleItem(
                    title = "Default Sport",
                    currentValue = defaultSportType,
                    options = listOf("Cycling", "Running", "Hiking", "Skiing", "Other"),
                    onValueChange = { newValue ->
                        defaultSportType = newValue
                        scope.launch {
                            context.settingsDataStore.edit { it[SettingsKeys.SPORT_TYPE] = newValue }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsCycleItem(
                    title = "Theme",
                    currentValue = theme,
                    options = listOf("Light", "Dark", "System"),
                    onValueChange = { newValue ->
                        theme = newValue
                        scope.launch {
                            context.settingsDataStore.edit { it[SettingsKeys.THEME] = newValue }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preview & Export
            SettingsSection(title = "Preview & Export") {
                SettingsCycleItem(
                    title = "Preview Quality",
                    currentValue = previewQuality,
                    options = listOf("Low", "Medium", "High"),
                    onValueChange = { newValue ->
                        previewQuality = newValue
                        val qualityInt = when (newValue) {
                            "Low" -> 0
                            "High" -> 2
                            else -> 1
                        }
                        scope.launch {
                            context.settingsDataStore.edit { it[SettingsKeys.PREVIEW_QUALITY] = qualityInt }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cache
            SettingsSection(title = "Storage") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearCacheDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clear Cache",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = formatFileSize(cacheSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GPX Video Producer",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Version 0.1.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will remove all cached files. Your projects will not be affected.") },
            confirmButton = {
                TextButton(onClick = {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                    cacheSize = calculateCacheSize(context)
                    showClearCacheDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("GPX Video Producer") },
            text = {
                Column {
                    Text("Version 0.1.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create stunning GPS-enhanced videos with real-time data overlays.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Open Source Licenses",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsCycleItem(
    title: String,
    currentValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val currentIndex = options.indexOf(currentValue)
                val nextIndex = (currentIndex + 1) % options.size
                onValueChange(options[nextIndex])
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun calculateCacheSize(context: Context): Long {
    return context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
