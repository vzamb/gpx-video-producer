package com.gpxvideo.feature.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.gpxvideo.core.ui.component.GpxVideoTopAppBar
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onProjectCreated: (UUID) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            GpxVideoTopAppBar(
                title = "Create Project",
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Project name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Project Name") },
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { error -> { Text(error) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))

            // Sport type selector
            Text("Sport Type", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SportType.entries.forEach { sportType ->
                    FilterChip(
                        selected = uiState.sportType == sportType,
                        onClick = { viewModel.onSportTypeSelected(sportType) },
                        label = { Text("${sportType.icon} ${sportType.displayName}") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Output settings
            Text("Output Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            DropdownSelector(
                label = "Resolution",
                options = Resolution.entries,
                selectedOption = uiState.resolution,
                onOptionSelected = viewModel::onResolutionSelected,
                optionLabel = { it.displayName }
            )

            Spacer(Modifier.height(12.dp))

            DropdownSelector(
                label = "Frame Rate",
                options = listOf(24, 30, 60),
                selectedOption = uiState.frameRate,
                onOptionSelected = viewModel::onFrameRateSelected,
                optionLabel = { "${it} fps" }
            )

            Spacer(Modifier.height(12.dp))

            DropdownSelector(
                label = "Format",
                options = ExportFormat.entries,
                selectedOption = uiState.exportFormat,
                onOptionSelected = viewModel::onExportFormatSelected,
                optionLabel = { it.displayName }
            )

            Spacer(Modifier.height(32.dp))

            // Create button
            Button(
                onClick = { viewModel.createProject(onProjectCreated) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isCreating
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Project")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionLabel(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
