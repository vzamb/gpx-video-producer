package com.gpxvideo.feature.export

import android.content.Context
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.model.AudioCodec
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.MetricType
import com.gpxvideo.core.model.OutputSettings
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.SyncMode
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.Transition
import com.gpxvideo.core.model.TransitionType
import com.gpxvideo.feature.overlays.GpxTimeSyncEngine
import com.gpxvideo.feature.overlays.OverlayRepository
import com.gpxvideo.lib.ffmpeg.FfmpegResult
import com.gpxvideo.lib.gpxparser.GpxParser
import com.gpxvideo.lib.gpxparser.GpxStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class ExportUiState(
    val settings: OutputSettings = OutputSettings(),
    val exportState: ExportState = ExportState.Idle,
    val estimatedSizeMb: Float = 0f
)

sealed class ExportState {
    data object Idle : ExportState()
    data class Exporting(
        val phase: ExportPhase,
        val progress: Float,
        val startTimeMs: Long = System.currentTimeMillis()
    ) : ExportState()
    data class Complete(val outputPath: String, val fileSizeBytes: Long) : ExportState()
    data class Error(val message: String) : ExportState()
}

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportPipeline: ExportPipeline,
    private val projectDao: ProjectDao,
    private val mediaItemDao: MediaItemDao,
    private val timelineTrackDao: TimelineTrackDao,
    private val timelineClipDao: TimelineClipDao,
    private val overlayRepository: OverlayRepository,
    private val gpxFileDao: GpxFileDao,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val projectId: String = savedStateHandle.get<String>("projectId") ?: ""

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val uuid = UUID.fromString(projectId)
                // Load project to restore saved aspect ratio
                val project = projectDao.getById(uuid)
                if (project != null) {
                    val savedRatio = SocialAspectRatio.entries.find {
                        it.width == project.resolutionWidth && it.height == project.resolutionHeight
                    } ?: SocialAspectRatio.LANDSCAPE_16_9
                    _uiState.update { it.copy(settings = it.settings.copy(aspectRatio = savedRatio)) }
                }
                val items = mediaItemDao.getByProjectId(uuid).first()
                val totalDuration = items.sumOf { it.durationMs ?: 0L }
                if (totalDuration > 0) {
                    estimatedDurationSec = totalDuration / 1000f
                }
            } catch (_: Exception) {}
            updateEstimatedSize()
        }
    }

    private var estimatedDurationSec = 60f

    fun updateFormat(format: ExportFormat) {
        _uiState.update { it.copy(settings = it.settings.copy(format = format)) }
        updateEstimatedSize()
    }

    fun updateResolution(resolution: Resolution) {
        _uiState.update { it.copy(settings = it.settings.copy(resolution = resolution)) }
        updateEstimatedSize()
    }

    fun updateAspectRatio(aspectRatio: SocialAspectRatio) {
        _uiState.update { it.copy(settings = it.settings.copy(aspectRatio = aspectRatio)) }
        updateEstimatedSize()
    }

    fun updateFrameRate(fps: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(frameRate = fps)) }
        updateEstimatedSize()
    }

    fun updateBitrate(bitrate: Long) {
        _uiState.update { it.copy(settings = it.settings.copy(bitrateBps = bitrate)) }
        updateEstimatedSize()
    }

    private fun updateEstimatedSize() {
        val settings = _uiState.value.settings
        val sizeMb = (settings.bitrateBps * estimatedDurationSec) / (8f * 1024 * 1024)
        _uiState.update { it.copy(estimatedSizeMb = sizeMb) }
    }

    fun startExport() {
        if (_uiState.value.exportState is ExportState.Exporting) return

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(exportState = ExportState.Exporting(ExportPhase.PREPARING, 0f))
                }

                val config = buildExportConfig() ?: run {
                    _uiState.update {
                        it.copy(exportState = ExportState.Error("Failed to build export configuration"))
                    }
                    return@launch
                }

                val result = exportPipeline.export(
                    config = config,
                    onPhaseChanged = { phase ->
                        _uiState.update { state ->
                            val current = state.exportState
                            if (current is ExportState.Exporting) {
                                state.copy(exportState = current.copy(phase = phase))
                            } else state
                        }
                    },
                    onProgress = { progress ->
                        _uiState.update { state ->
                            val current = state.exportState
                            if (current is ExportState.Exporting) {
                                state.copy(exportState = current.copy(progress = progress))
                            } else state
                        }
                    }
                )

                when (result) {
                    is FfmpegResult.Success -> {
                        val file = File(result.outputPath)
                        _uiState.update {
                            it.copy(
                                exportState = ExportState.Complete(
                                    outputPath = result.outputPath,
                                    fileSizeBytes = if (file.exists()) file.length() else 0L
                                )
                            )
                        }
                    }
                    is FfmpegResult.Error -> {
                        _uiState.update {
                            it.copy(exportState = ExportState.Error(result.message))
                        }
                    }
                    is FfmpegResult.Cancelled -> {
                        _uiState.update { it.copy(exportState = ExportState.Idle) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(exportState = ExportState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun cancelExport() {
        exportPipeline.cancel()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun resetState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun saveToGallery(outputPath: String) {
        viewModelScope.launch {
            try {
                val file = java.io.File(outputPath)
                if (!file.exists()) return@launch

                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, file.name)
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently fail for stub exports
            }
        }
    }

    private suspend fun buildExportConfig(): ExportConfig? {
        val uuid = try {
            UUID.fromString(projectId)
        } catch (e: Exception) {
            return null
        }

        val project = projectDao.getById(uuid) ?: return null
        val tracks = timelineTrackDao.getByProjectId(uuid).first()
        val mediaItems = mediaItemDao.getByProjectId(uuid).first()
        val overlays = overlayRepository.getOverlaysForProject(uuid).first()
        val gpxFiles = gpxFileDao.getByProjectId(uuid).first()

        val settings = _uiState.value.settings

        // Build clips from video and image tracks (timeline-based)
        val visualTracks = tracks.filter { it.type == TrackType.VIDEO.name || it.type == TrackType.IMAGE.name }
        val clips = mutableListOf<ExportClip>()

        for (track in visualTracks) {
            val trackClips = timelineClipDao.getByTrackId(track.id).first()
            for (clip in trackClips) {
                val mediaItem = clip.mediaItemId?.let { mid ->
                    mediaItems.find { it.id == mid }
                } ?: continue
                val filePath = mediaItem.localCopyPath.ifBlank { null } ?: mediaItem.sourcePath.ifBlank { null } ?: continue
                val isImage = mediaItem.type == "IMAGE"

                val transition = clip.entryTransitionType?.let { type ->
                    try {
                        Transition(
                            type = TransitionType.valueOf(type),
                            durationMs = clip.entryTransitionDurationMs ?: 500L
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                clips.add(
                    ExportClip(
                        filePath = filePath,
                        startTimeMs = clip.startTimeMs,
                        endTimeMs = clip.endTimeMs,
                        trimStartMs = clip.trimStartMs,
                        trimEndMs = clip.trimEndMs,
                        speed = clip.speed,
                        volume = clip.volume,
                        transition = transition,
                        clipId = clip.id,
                        gpxPointIndex = if (clip.gpxPointIndex >= 0) clip.gpxPointIndex else null,
                        gpxDistanceMeters = if (clip.isSynced) clip.gpxDistanceMeters else null,
                        isSynced = clip.isSynced,
                        isImage = isImage,
                        brightness = clip.brightness,
                        contrast = clip.contrast,
                        saturation = clip.saturation
                    )
                )
            }
        }
        clips.sortBy { it.startTimeMs }

        // Fallback: if no timeline clips exist, build directly from media items
        // (Story Creator wizard doesn't create timeline tracks/clips)
        if (clips.isEmpty()) {
            val visualItems = mediaItems.filter { it.type == "VIDEO" || it.type == "IMAGE" }
            var currentTimeMs = 0L
            for (media in visualItems) {
                val filePath = media.localCopyPath.ifBlank { media.sourcePath }
                if (filePath.isBlank()) continue
                val isImage = media.type == "IMAGE"
                val durationMs = media.durationMs ?: if (isImage) 3000L else 5000L
                clips.add(
                    ExportClip(
                        filePath = filePath,
                        startTimeMs = currentTimeMs,
                        endTimeMs = currentTimeMs + durationMs,
                        trimStartMs = 0L,
                        trimEndMs = durationMs,
                        speed = 1f,
                        volume = 1f,
                        transition = null,
                        isImage = isImage
                    )
                )
                currentTimeMs += durationMs
            }
        }

        val trackOrderById = tracks.associate { it.id to it.order }
        val exportOverlays = overlays.mapNotNull { overlay ->
            val clip = timelineClipDao.getById(overlay.timelineClipId) ?: return@mapNotNull null
            if (clip.endTimeMs <= clip.startTimeMs) return@mapNotNull null
            val order = trackOrderById[clip.trackId] ?: Int.MAX_VALUE
            Triple(
                order,
                clip.startTimeMs,
                ExportOverlay(
                    overlayConfig = overlay,
                    startTimeMs = clip.startTimeMs,
                    endTimeMs = clip.endTimeMs
                )
            )
        }.sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }

        val gpxData: GpxData? = gpxFiles.firstOrNull()?.let { gpxFile ->
            runCatching {
                File(gpxFile.filePath).inputStream().use { GpxParser.parse(it) }
            }.getOrNull()
        }
        val gpxStats = gpxData?.let { GpxStatistics.computeFullStats(it) }

        val syncEngine = gpxData?.let {
            val mode = when (project.storyMode) {
                "FAST_FORWARD" -> SyncMode.CLIP_PROGRESS
                "LIVE_SYNC" -> SyncMode.CLIP_PROGRESS // fallback until sync points are persisted
                else -> SyncMode.GPX_TIMESTAMP
            }
            GpxTimeSyncEngine(it, mode).apply {
                precomputeLookupTable()
            }
        }

        // Output path
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        moviesDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = settings.format.extension
        val outputPath = File(moviesDir, "export_${timestamp}.$extension").absolutePath

        return ExportConfig(
            projectId = uuid,
            clips = clips,
            overlays = exportOverlays,
            outputSettings = settings,
            outputPath = outputPath,
            gpxData = gpxData,
            gpxStats = gpxStats,
            syncEngine = syncEngine,
            projectWidth = settings.aspectRatio.width,
            projectHeight = settings.aspectRatio.height,
            storyTemplate = project.storyTemplate,
            activityTitle = project.activityTitle,
            storyMode = project.storyMode,
            accentColor = project.accentColor,
            chartType = com.gpxvideo.core.model.ChartType.fromName(project.chartType),
            showRouteMap = project.showRouteMap,
            metricConfig = resolveMetricConfig(project)
        )
    }

    /** Resolve metric config: use project override if set, otherwise sport defaults. */
    private fun resolveMetricConfig(project: com.gpxvideo.core.database.entity.ProjectEntity): List<MetricType> {
        project.metricConfig?.let { json ->
            try {
                val names = json.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                val types = names.mapNotNull { name ->
                    try { MetricType.valueOf(name) } catch (_: Exception) { null }
                }
                if (types.isNotEmpty()) return types
            } catch (_: Exception) { }
        }
        val sportType = try { SportType.valueOf(project.sportType) } catch (_: Exception) { null }
        return sportType?.let { MetricType.forSport(it) } ?: MetricType.fallbackMetrics
    }
}
