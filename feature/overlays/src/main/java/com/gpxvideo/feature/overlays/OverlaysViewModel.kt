package com.gpxvideo.feature.overlays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.StatField
import com.gpxvideo.core.model.StatsLayout
import com.gpxvideo.core.model.SyncMode
import com.gpxvideo.lib.gpxparser.GpxParser
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.lib.gpxparser.GpxStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OverlaysViewModel @Inject constructor(
    private val overlayRepository: OverlayRepository,
    private val gpxFileDao: GpxFileDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: UUID = savedStateHandle.get<String>("projectId")
        ?.let { UUID.fromString(it) }
        ?: UUID.randomUUID()

    val overlays: StateFlow<List<OverlayConfig>> = overlayRepository
        .getOverlaysForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedOverlay = MutableStateFlow<OverlayConfig?>(null)
    val selectedOverlay: StateFlow<OverlayConfig?> = _selectedOverlay.asStateFlow()

    private val _gpxData = MutableStateFlow<GpxData?>(null)
    val gpxData: StateFlow<GpxData?> = _gpxData.asStateFlow()

    private val _gpxStats = MutableStateFlow<GpxStats?>(null)
    val gpxStats: StateFlow<GpxStats?> = _gpxStats.asStateFlow()

    private val _syncEngine = MutableStateFlow<GpxTimeSyncEngine?>(null)
    val syncEngine: StateFlow<GpxTimeSyncEngine?> = _syncEngine.asStateFlow()

    private val _syncMode = MutableStateFlow(SyncMode.GPX_TIMESTAMP)
    val syncMode: StateFlow<SyncMode> = _syncMode.asStateFlow()

    private val _timeOffsetMs = MutableStateFlow(0L)
    val timeOffsetMs: StateFlow<Long> = _timeOffsetMs.asStateFlow()

    private val _keyframes = MutableStateFlow<List<SyncKeyframe>>(emptyList())
    val keyframes: StateFlow<List<SyncKeyframe>> = _keyframes.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentPoint = MutableStateFlow<InterpolatedPoint?>(null)
    val currentPoint: StateFlow<InterpolatedPoint?> = _currentPoint.asStateFlow()

    init {
        loadGpxData(projectId)
    }

    fun addOverlay(type: String, timelineClipId: UUID) {
        viewModelScope.launch {
            val overlay = createDefaultOverlay(type, projectId, timelineClipId)
            if (overlay != null) {
                overlayRepository.addOverlay(overlay)
                _selectedOverlay.value = overlay
            }
        }
    }

    fun updateOverlay(overlay: OverlayConfig) {
        viewModelScope.launch {
            overlayRepository.updateOverlay(overlay)
            if (_selectedOverlay.value?.id == overlay.id) {
                _selectedOverlay.value = overlay
            }
        }
    }

    fun deleteOverlay(overlayId: UUID) {
        viewModelScope.launch {
            overlayRepository.deleteOverlay(overlayId)
            if (_selectedOverlay.value?.id == overlayId) {
                _selectedOverlay.value = null
            }
        }
    }

    fun selectOverlay(overlayId: UUID?) {
        _selectedOverlay.value = if (overlayId == null) {
            null
        } else {
            overlays.value.find { it.id == overlayId }
        }
    }

    fun updateSyncMode(mode: SyncMode) {
        _syncMode.value = mode
        rebuildSyncEngine()
    }

    fun updateTimeOffset(offsetMs: Long) {
        _timeOffsetMs.value = offsetMs
        rebuildSyncEngine()
    }

    fun addKeyframe(keyframe: SyncKeyframe) {
        _keyframes.value = (_keyframes.value + keyframe).sortedBy { it.videoTimeMs }
        rebuildSyncEngine()
    }

    fun updateVideoPosition(positionMs: Long) {
        _currentPositionMs.value = positionMs
        _syncEngine.value?.let { engine ->
            _currentPoint.value = engine.getPointAtVideoTime(positionMs)
        }
    }

    fun loadGpxData(projectId: UUID) {
        viewModelScope.launch {
            gpxFileDao.getByProjectId(projectId).collect { gpxFiles ->
                val firstFile = gpxFiles.firstOrNull() ?: return@collect
                try {
                    val file = File(firstFile.filePath)
                    if (file.exists()) {
                        val data = file.inputStream().use { GpxParser.parse(it) }
                        _gpxData.value = data
                        _gpxStats.value = GpxStatistics.computeFullStats(data)
                        rebuildSyncEngine()
                    }
                } catch (_: Exception) {
                    // GPX parsing failed — leave data as null
                }
            }
        }
    }

    private fun rebuildSyncEngine() {
        val data = _gpxData.value ?: return
        val engine = GpxTimeSyncEngine(
            gpxData = data,
            syncMode = _syncMode.value,
            timeOffsetMs = _timeOffsetMs.value,
            keyframes = _keyframes.value
        )
        engine.precomputeLookupTable()
        _syncEngine.value = engine
        // Refresh current point with new engine
        _currentPoint.value = engine.getPointAtVideoTime(_currentPositionMs.value)
    }

    private fun createDefaultOverlay(
        type: String,
        projectId: UUID,
        timelineClipId: UUID
    ): OverlayConfig? = when (type) {
        "static_altitude_profile" -> OverlayConfig.StaticAltitudeProfile(
            projectId = projectId,
            timelineClipId = timelineClipId
        )
        "static_map" -> OverlayConfig.StaticMap(
            projectId = projectId,
            timelineClipId = timelineClipId
        )
        "static_stats" -> OverlayConfig.StaticStats(
            projectId = projectId,
            timelineClipId = timelineClipId,
            fields = listOf(StatField.TOTAL_DISTANCE, StatField.TOTAL_TIME, StatField.TOTAL_ELEVATION_GAIN, StatField.AVG_SPEED)
        )
        "dynamic_altitude_profile" -> OverlayConfig.DynamicAltitudeProfile(
            projectId = projectId,
            timelineClipId = timelineClipId
        )
        "dynamic_map" -> OverlayConfig.DynamicMap(
            projectId = projectId,
            timelineClipId = timelineClipId
        )
        "dynamic_stat" -> OverlayConfig.DynamicStat(
            projectId = projectId,
            timelineClipId = timelineClipId
        )
        else -> null
    }
}
