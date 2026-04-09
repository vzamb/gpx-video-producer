package com.gpxvideo.feature.project

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.common.settingsDataStore
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.entity.ProjectEntity
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SportType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CreateProjectUiState(
    val name: String = "",
    val sportType: SportType = SportType.CYCLING,
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val exportFormat: ExportFormat = ExportFormat.MP4_H264,
    val nameError: String? = null,
    val isCreating: Boolean = false
)

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projectDao: ProjectDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val prefs = context.settingsDataStore.data.first()
                val savedSport = prefs[stringPreferencesKey("default_sport_type")]
                if (savedSport != null) {
                    val sportType = SportType.entries.find {
                        it.displayName.equals(savedSport, ignoreCase = true) || it.name.equals(savedSport, ignoreCase = true)
                    }
                    if (sportType != null) {
                        _uiState.update { it.copy(sportType = sportType) }
                    }
                }
            } catch (_: Exception) { /* use default */ }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onSportTypeSelected(sportType: SportType) {
        _uiState.update { it.copy(sportType = sportType) }
    }

    fun onResolutionSelected(resolution: Resolution) {
        _uiState.update { it.copy(resolution = resolution) }
    }

    fun onFrameRateSelected(frameRate: Int) {
        _uiState.update { it.copy(frameRate = frameRate) }
    }

    fun onExportFormatSelected(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun createProject(onSuccess: (UUID) -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Project name is required") }
            return
        }

        _uiState.update { it.copy(isCreating = true) }

        viewModelScope.launch {
            val projectId = UUID.randomUUID()
            val entity = ProjectEntity(
                id = projectId,
                name = state.name.trim(),
                sportType = state.sportType.name,
                resolutionWidth = state.resolution.width,
                resolutionHeight = state.resolution.height,
                frameRate = state.frameRate,
                exportFormat = state.exportFormat.name
            )
            projectDao.insert(entity)
            onSuccess(projectId)
        }
    }
}
