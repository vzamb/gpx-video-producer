package com.gpxvideo.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.TransitionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    templateId: String?,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isEditing) "Edit Template" else "New Template")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Template Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            // Sport type selector
            SportTypeSelector(
                selectedSport = state.sportType,
                onSportSelected = { viewModel.updateSportType(it) }
            )

            // Style section
            SectionHeader("Style")

            ColorField(
                label = "Primary Color",
                colorHex = state.primaryColor.toHexString(),
                onColorChanged = { viewModel.updatePrimaryColor(it) }
            )
            ColorField(
                label = "Secondary Color",
                colorHex = state.secondaryColor.toHexString(),
                onColorChanged = { viewModel.updateSecondaryColor(it) }
            )
            ColorField(
                label = "Accent Color",
                colorHex = state.accentColor.toHexString(),
                onColorChanged = { viewModel.updateAccentColor(it) }
            )

            FontSelector(
                selectedFont = state.fontFamily,
                onFontSelected = { viewModel.updateFontFamily(it) }
            )

            // Transitions
            SectionHeader("Transitions")

            TransitionTypeSelector(
                selectedType = state.transitionType,
                onTypeSelected = { viewModel.updateTransitionType(it) }
            )

            Text(
                text = "Duration: ${state.transitionDurationMs}ms",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = state.transitionDurationMs.toFloat(),
                onValueChange = { viewModel.updateTransitionDuration(it.toLong()) },
                valueRange = 0f..2000f,
                steps = 19
            )

            // Output settings
            SectionHeader("Output Settings")

            ResolutionSelector(
                selectedResolution = state.resolution,
                onResolutionSelected = { viewModel.updateResolution(it) }
            )

            FrameRateSelector(
                selectedFrameRate = state.frameRate,
                onFrameRateSelected = { viewModel.updateFrameRate(it) }
            )

            FormatSelector(
                selectedFormat = state.format,
                onFormatSelected = { viewModel.updateFormat(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = { viewModel.save(onSave) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.name.isNotBlank() && !state.isSaving && !state.isBuiltIn,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (state.isSaving) "Saving…" else "Save Template")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportTypeSelector(
    selectedSport: SportType?,
    onSportSelected: (SportType?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf<SportType?>(null) + SportType.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedSport?.let { it.displayName } ?: "Universal",
            onValueChange = {},
            readOnly = true,
            label = { Text("Sport Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { sport ->
                DropdownMenuItem(
                    text = {
                        Text(sport?.let { it.displayName } ?: "Universal")
                    },
                    onClick = {
                        onSportSelected(sport)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionTypeSelector(
    selectedType: TransitionType,
    onTypeSelected: (TransitionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedType.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text("Transition Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TransitionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.replace('_', ' ')) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionSelector(
    selectedResolution: Resolution,
    onResolutionSelected: (Resolution) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedResolution.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Resolution") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Resolution.entries.forEach { res ->
                DropdownMenuItem(
                    text = { Text(res.displayName) },
                    onClick = {
                        onResolutionSelected(res)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrameRateSelector(
    selectedFrameRate: Int,
    onFrameRateSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(24, 25, 30, 50, 60)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "${selectedFrameRate} fps",
            onValueChange = {},
            readOnly = true,
            label = { Text("Frame Rate") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { fps ->
                DropdownMenuItem(
                    text = { Text("$fps fps") },
                    onClick = {
                        onFrameRateSelected(fps)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSelector(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedFormat.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Export Format") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ExportFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.displayName) },
                    onClick = {
                        onFormatSelected(format)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSelector(
    selectedFont: String,
    onFontSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fonts = listOf("Inter", "Roboto", "Montserrat", "Open Sans", "Lato", "Source Sans Pro")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedFont,
            onValueChange = {},
            readOnly = true,
            label = { Text("Font") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            fonts.forEach { font ->
                DropdownMenuItem(
                    text = { Text(font) },
                    onClick = {
                        onFontSelected(font)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorField(
    label: String,
    colorHex: String,
    onColorChanged: (Long) -> Unit
) {
    var text by remember(colorHex) { mutableStateOf(colorHex) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val cleaned = newText.removePrefix("0x").removePrefix("#")
            cleaned.toLongOrNull(16)?.let { onColorChanged(it) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        placeholder = { Text("0xFFRRGGBB") }
    )
}

private fun Long.toHexString(): String =
    "0x${toString(16).uppercase().padStart(8, '0')}"
