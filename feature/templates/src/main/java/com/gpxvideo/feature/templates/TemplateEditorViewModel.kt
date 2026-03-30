package com.gpxvideo.feature.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.model.AudioCodec
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.OutputSettings
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.StylePreset
import com.gpxvideo.core.model.Template
import com.gpxvideo.core.model.TransitionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TemplateEditorState(
    val name: String = "",
    val description: String = "",
    val sportType: SportType? = null,
    val primaryColor: Long = 0xFFFF5722,
    val secondaryColor: Long = 0xFF4CAF50,
    val accentColor: Long = 0xFFFFC107,
    val fontFamily: String = "Inter",
    val transitionType: TransitionType = TransitionType.DISSOLVE,
    val transitionDurationMs: Long = 500,
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val format: ExportFormat = ExportFormat.MP4_H264,
    val isEditing: Boolean = false,
    val isBuiltIn: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val templateId: UUID? = savedStateHandle.get<String>("templateId")
        ?.takeIf { it != "null" && it.isNotBlank() }
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val _state = MutableStateFlow(TemplateEditorState())
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    private var existingTemplate: Template? = null

    init {
        if (templateId != null) {
            viewModelScope.launch {
                val template = templateRepository.getTemplateById(templateId)
                if (template != null) {
                    existingTemplate = template
                    _state.update {
                        it.copy(
                            name = template.name,
                            description = template.description ?: "",
                            sportType = template.sportType,
                            primaryColor = template.stylePreset.primaryColor,
                            secondaryColor = template.stylePreset.secondaryColor,
                            accentColor = template.stylePreset.accentColor,
                            fontFamily = template.stylePreset.fontFamily,
                            transitionType = template.stylePreset.transitionType,
                            transitionDurationMs = template.stylePreset.transitionDurationMs,
                            resolution = template.outputSettings.resolution,
                            frameRate = template.outputSettings.frameRate,
                            format = template.outputSettings.format,
                            isEditing = true,
                            isBuiltIn = template.isBuiltIn
                        )
                    }
                }
            }
        }
    }

    fun updateName(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun updateDescription(description: String) {
        _state.update { it.copy(description = description) }
    }

    fun updateSportType(sportType: SportType?) {
        _state.update { it.copy(sportType = sportType) }
    }

    fun updatePrimaryColor(color: Long) {
        _state.update { it.copy(primaryColor = color) }
    }

    fun updateSecondaryColor(color: Long) {
        _state.update { it.copy(secondaryColor = color) }
    }

    fun updateAccentColor(color: Long) {
        _state.update { it.copy(accentColor = color) }
    }

    fun updateFontFamily(fontFamily: String) {
        _state.update { it.copy(fontFamily = fontFamily) }
    }

    fun updateTransitionType(type: TransitionType) {
        _state.update { it.copy(transitionType = type) }
    }

    fun updateTransitionDuration(durationMs: Long) {
        _state.update { it.copy(transitionDurationMs = durationMs) }
    }

    fun updateResolution(resolution: Resolution) {
        _state.update { it.copy(resolution = resolution) }
    }

    fun updateFrameRate(frameRate: Int) {
        _state.update { it.copy(frameRate = frameRate) }
    }

    fun updateFormat(format: ExportFormat) {
        _state.update { it.copy(format = format) }
    }

    fun save(onComplete: () -> Unit) {
        val current = _state.value
        if (current.name.isBlank() || current.isSaving) return

        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val template = (existingTemplate ?: Template(name = current.name)).copy(
                name = current.name,
                description = current.description.ifBlank { null },
                sportType = current.sportType,
                isBuiltIn = false,
                trackLayout = existingTemplate?.trackLayout ?: emptyList(),
                overlayPresets = existingTemplate?.overlayPresets ?: emptyList(),
                outputSettings = OutputSettings(
                    resolution = current.resolution,
                    frameRate = current.frameRate,
                    format = current.format
                ),
                stylePreset = StylePreset(
                    primaryColor = current.primaryColor,
                    secondaryColor = current.secondaryColor,
                    accentColor = current.accentColor,
                    fontFamily = current.fontFamily,
                    transitionType = current.transitionType,
                    transitionDurationMs = current.transitionDurationMs
                )
            )
            templateRepository.saveTemplate(template)
            _state.update { it.copy(isSaving = false) }
            onComplete()
        }
    }
}
