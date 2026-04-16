package com.gpxvideo.feature.project

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.dao.TimelineClipDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.GpxFileEntity
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.ProjectEntity
import com.gpxvideo.core.model.ClipSyncPoint
import com.gpxvideo.core.model.ChartType
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.MetricType
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.StoryMode
import com.gpxvideo.core.overlayrenderer.OverlayTemplateRenderer
import com.gpxvideo.core.overlayrenderer.SvgTemplateConventions
import com.gpxvideo.feature.gpx.GpxImportManager
import com.gpxvideo.feature.gpx.StravaStreamConverter
import com.gpxvideo.feature.preview.PreviewClip
import com.gpxvideo.feature.preview.PreviewDisplayTransform
import com.gpxvideo.feature.preview.PreviewEngine
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.lib.gpxparser.GpxStats
import com.gpxvideo.lib.mediautils.MediaProber
import com.gpxvideo.lib.mediautils.ThumbnailGenerator
import com.gpxvideo.lib.strava.StravaActivity
import com.gpxvideo.lib.strava.StravaApi
import com.gpxvideo.lib.strava.StravaAuth
import com.gpxvideo.lib.strava.StravaTokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ProjectEditorUiState(
    val project: ProjectEntity? = null,
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val gpxData: GpxData? = null,
    val gpxStats: GpxStats? = null,
    val gpxFiles: List<GpxFileEntity> = emptyList(),
    val isImportingGpx: Boolean = false,
    val storyMode: String = "STATIC",
    val storyTemplate: String = "CINEMATIC",
    val selectedAspectRatio: SocialAspectRatio = SocialAspectRatio.PORTRAIT_9_16,
    val accentColor: Int = 0xFF448AFF.toInt(),
    val activityTitle: String = "",
    val clipSyncPoints: Map<UUID, ClipSyncPoint> = emptyMap(),
    val autoSyncedClipIds: Set<UUID> = emptySet(),
    val chartType: ChartType? = ChartType.ELEVATION,
    val showRouteMap: Boolean = true,
    val metricConfig: List<MetricType> = MetricType.fallbackMetrics,
    val templateSlotCount: Int = 4
)

sealed interface FrameExportState {
    data object Idle : FrameExportState
    data object Exporting : FrameExportState
    data class Complete(val uri: android.net.Uri) : FrameExportState
    data class Error(val message: String) : FrameExportState
}

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectDao: ProjectDao,
    private val mediaItemDao: MediaItemDao,
    private val gpxFileDao: GpxFileDao,
    private val gpxImportManager: GpxImportManager,
    private val trackDao: TimelineTrackDao,
    private val clipDao: TimelineClipDao,
    val previewEngine: PreviewEngine,
    val stravaApi: StravaApi,
    val stravaAuth: StravaAuth,
    val stravaTokenStore: StravaTokenStore,
    private val stravaStreamConverter: StravaStreamConverter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val projectId: UUID = UUID.fromString(
        checkNotNull(savedStateHandle.get<String>("projectId"))
    )

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    private val _isImporting = MutableStateFlow(false)
    private val _gpxData = MutableStateFlow<GpxData?>(null)
    private val _gpxStats = MutableStateFlow<GpxStats?>(null)
    private val _isImportingGpx = MutableStateFlow(false)
    private val _storyMode = MutableStateFlow("STATIC")
    private val _storyTemplate = MutableStateFlow("CINEMATIC")
    private val _selectedAspectRatio = MutableStateFlow(SocialAspectRatio.PORTRAIT_9_16)
    private val _accentColor = MutableStateFlow(0xFF448AFF.toInt())
    private val _activityTitle = MutableStateFlow("")
    private val _clipSyncPoints = MutableStateFlow<Map<UUID, ClipSyncPoint>>(emptyMap())
    /** Clip IDs that were auto-synced because their video creation date matched the GPX timespan. */
    private val _autoSyncedClipIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val _chartType = MutableStateFlow<ChartType?>(ChartType.ELEVATION)
    private val _showRouteMap = MutableStateFlow(true)
    private val _metricConfig = MutableStateFlow(MetricType.fallbackMetrics)
    private val _templateSlotCount = MutableStateFlow(4)

    init {
        previewEngine.initialize()
        viewModelScope.launch {
            projectDao.observeById(projectId).collect { project ->
                _project.value = project
                project?.let {
                    // Map legacy DB values to new enum names
                    _storyMode.value = when (it.storyMode) {
                        "HYPER_LAPSE" -> StoryMode.FAST_FORWARD.name
                        "DOCUMENTARY" -> StoryMode.LIVE_SYNC.name
                        else -> it.storyMode
                    }
                    _storyTemplate.value = it.storyTemplate
                    val savedRatio = SocialAspectRatio.entries.find { r ->
                        r.width == it.resolutionWidth && r.height == it.resolutionHeight
                    } ?: SocialAspectRatio.PORTRAIT_9_16
                    _selectedAspectRatio.value = savedRatio
                    _activityTitle.value = it.activityTitle
                    _accentColor.value = it.accentColor
                    _chartType.value = ChartType.fromName(it.chartType)
                    _showRouteMap.value = it.showRouteMap
                    _metricConfig.value = resolveMetricConfig(it)
                }
            }
        }
        // Watch GPX files reactively so data appears as soon as a file is imported
        viewModelScope.launch {
            gpxFileDao.getByProjectId(projectId).collect { existingFiles ->
                if (existingFiles.isNotEmpty() && _gpxData.value == null) {
                    val gpxData = gpxImportManager.parseGpxFile(existingFiles.first())
                    if (gpxData != null) {
                        _gpxData.value = gpxData
                        _gpxStats.value = GpxStatistics.computeFullStats(gpxData)
                        // Auto-detect sync points if mode is LIVE_SYNC
                        if (_storyMode.value == StoryMode.LIVE_SYNC.name) {
                            autoDetectSyncPoints()
                        }
                    }
                } else if (existingFiles.isEmpty()) {
                    _gpxData.value = null
                    _gpxStats.value = null
                }
            }
        }
        // Load preview clips from timeline (respects trims, reorder, effects).
        // Falls back to raw media items if no timeline clips exist yet.
        viewModelScope.launch {
            mediaItemDao.getByProjectId(projectId).collect { items ->
                loadTimelineAwarePreviewClips(items)
            }
        }
        // Load persisted clip sync points
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = trackDao.getByProjectId(projectId).first()
            val videoTrack = tracks.find { it.type == "VIDEO" } ?: return@launch
            val clipEntities = clipDao.getByTrackId(videoTrack.id).first()
            val synced = clipEntities.filter { it.isSynced && it.mediaItemId != null }.associate { clip ->
                clip.mediaItemId!! to ClipSyncPoint(
                    clipId = clip.mediaItemId!!,
                    gpxPointIndex = clip.gpxPointIndex,
                    gpxDistanceMeters = clip.gpxDistanceMeters,
                    isSynced = true
                )
            }
            val autoIds = clipEntities
                .filter { it.isAutoSynced && it.mediaItemId != null }
                .map { it.mediaItemId!! }
                .toSet()
            if (synced.isNotEmpty()) {
                _clipSyncPoints.value = synced
            }
            if (autoIds.isNotEmpty()) {
                _autoSyncedClipIds.value = autoIds
            }
        }
        // Compute template slot count when template or aspect ratio changes
        viewModelScope.launch(Dispatchers.IO) {
            combine(_storyTemplate, _selectedAspectRatio) { tmpl, ratio -> tmpl to ratio }
                .collect { (templateId, ratio) ->
                    val renderer = OverlayTemplateRenderer(context)
                    val loaded = renderer.load(templateId, ratio.width, ratio.height)
                    val slots = loaded?.let {
                        SvgTemplateConventions.countSlots(it.loaded.rawSvgString)
                    } ?: 4
                    _templateSlotCount.value = slots

                    // Auto-trim metric config when switching to a template with fewer slots
                    val currentConfig = _metricConfig.value
                    if (currentConfig.size > slots) {
                        setMetricConfig(currentConfig.take(slots))
                    }
                }
        }
    }

    // ── Preview playback ─────────────────────────────────────────────────
    val currentPositionMs = previewEngine.currentPositionMs
    val isPlaying = previewEngine.isPlaying
    val videoDuration = previewEngine.duration

    fun play() = previewEngine.play()
    fun pause() = previewEngine.pause()
    fun togglePlayback() { if (previewEngine.isPlaying.value) pause() else play() }
    fun seekTo(positionMs: Long) = previewEngine.seekTo(positionMs)

    private val _frameExportState = MutableStateFlow<FrameExportState>(FrameExportState.Idle)
    val frameExportState: StateFlow<FrameExportState> = _frameExportState

    /**
     * Export the current frame (video + overlay) as a PNG image to the gallery.
     * Renders the overlay at full output resolution.
     */
    fun exportCurrentFrame(frameData: com.gpxvideo.core.overlayrenderer.OverlayFrameData) {
        viewModelScope.launch {
            _frameExportState.value = FrameExportState.Exporting
            try {
                val aspectRatio = _selectedAspectRatio.value
                val outputW = aspectRatio.width
                val outputH = aspectRatio.height

                // Get the clean video frame
                val videoFrame = previewEngine.captureCleanFrame()

                // Render overlay at full output resolution
                val renderer = com.gpxvideo.core.overlayrenderer.OverlayTemplateRenderer(context)
                val template = renderer.load(_storyTemplate.value, outputW, outputH)
                val overlayBitmap = template?.let {
                    renderer.render(
                        template = it,
                        width = outputW,
                        height = outputH,
                        frameData = frameData,
                        gpxData = _gpxData.value,
                        activityTitle = _activityTitle.value,
                        chartType = _chartType.value,
                        showRouteMap = _showRouteMap.value,
                        metricConfig = _metricConfig.value
                    )
                }

                // Composite: video frame + overlay
                val composite = android.graphics.Bitmap.createBitmap(
                    outputW, outputH, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(composite)
                canvas.drawColor(android.graphics.Color.BLACK)

                if (videoFrame != null) {
                    val src = android.graphics.Rect(0, 0, videoFrame.width, videoFrame.height)
                    val dst = android.graphics.Rect(0, 0, outputW, outputH)
                    canvas.drawBitmap(videoFrame, src, dst, null)
                }

                if (overlayBitmap != null) {
                    val src = android.graphics.Rect(0, 0, overlayBitmap.width, overlayBitmap.height)
                    val dst = android.graphics.Rect(0, 0, outputW, outputH)
                    canvas.drawBitmap(overlayBitmap, src, dst, null)
                }

                // Save to gallery
                val savedUri = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    saveImageToGallery(composite)
                }
                _frameExportState.value = if (savedUri != null) {
                    FrameExportState.Complete(savedUri)
                } else {
                    FrameExportState.Error("Failed to save image")
                }
            } catch (e: Exception) {
                _frameExportState.value = FrameExportState.Error(e.message ?: "Export failed")
            }
        }
    }

    fun resetFrameExportState() {
        _frameExportState.value = FrameExportState.Idle
    }

    private fun saveImageToGallery(bitmap: android.graphics.Bitmap): android.net.Uri? {
        val resolver = context.contentResolver
        val filename = "GPX_Story_${System.currentTimeMillis()}.png"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_PICTURES
            )
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return null
        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
        return uri
    }

    /** Load preview clips respecting timeline trims, order, and effects. */
    private suspend fun loadTimelineAwarePreviewClips(mediaItems: List<MediaItemEntity>) {
        val mediaMap = mediaItems.associateBy { it.id }
        val visualItems = mediaItems.filter { it.type == "VIDEO" || it.type == "IMAGE" }
        if (visualItems.isEmpty()) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                previewEngine.setMediaSources(emptyList())
            }
            return
        }

        // Try timeline clips first
        val tracks = trackDao.getByProjectId(projectId).first()
        val visualTracks = tracks.filter { it.type == "VIDEO" || it.type == "IMAGE" }
        val allClipEntities = mutableListOf<com.gpxvideo.core.database.entity.TimelineClipEntity>()
        for (track in visualTracks) {
            allClipEntities += clipDao.getByTrackId(track.id).first()
        }
        if (allClipEntities.isNotEmpty()) {
            val clips = allClipEntities.sortedBy { it.startTimeMs }.mapNotNull { clipEntity ->
                val media = mediaMap[clipEntity.mediaItemId] ?: return@mapNotNull null
                val path = media.localCopyPath.ifBlank { media.sourcePath }
                val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
                val isImage = media.type == "IMAGE"
                val sourceAR = if (media.height > 0) {
                    val r = media.rotation % 360
                    if (r == 90 || r == 270) media.height.toFloat() / media.width.toFloat()
                    else media.width.toFloat() / media.height.toFloat()
                } else 0f
                val effectiveDurationMs = clipEntity.endTimeMs - clipEntity.startTimeMs
                PreviewClip(
                    uri = uri,
                    startMs = if (isImage) 0L else clipEntity.trimStartMs,
                    endMs = if (isImage) effectiveDurationMs else clipEntity.trimStartMs + effectiveDurationMs,
                    speed = clipEntity.speed,
                    volume = clipEntity.volume,
                    displayTransform = PreviewDisplayTransform(
                        brightness = clipEntity.brightness,
                        contrast = clipEntity.contrast,
                        saturation = clipEntity.saturation,
                        sourceVideoAspectRatio = sourceAR
                    ),
                    isImage = isImage
                )
            }
            if (clips.isNotEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    previewEngine.setMediaSources(clips)
                }
                return
            }
        }

        // Fallback: raw media items (no timeline yet)
        loadPreviewClips(visualItems)
    }

    private suspend fun loadPreviewClips(items: List<MediaItemEntity>) {
        val visualItems = items.filter { it.type == "VIDEO" || it.type == "IMAGE" }
        if (visualItems.isEmpty()) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                previewEngine.setMediaSources(emptyList())
            }
            return
        }
        val clips = visualItems.map { media ->
            val path = media.localCopyPath.ifBlank { media.sourcePath }
            val uri = if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))
            val isImage = media.type == "IMAGE"
            val durationMs = media.durationMs ?: if (isImage) 3000L else 5000L
            val sourceAR = if (media.height > 0) {
                val r = media.rotation % 360
                if (r == 90 || r == 270) media.height.toFloat() / media.width.toFloat()
                else media.width.toFloat() / media.height.toFloat()
            } else 0f
            PreviewClip(
                uri = uri,
                startMs = 0L,
                endMs = durationMs,
                speed = 1f,
                displayTransform = PreviewDisplayTransform(sourceVideoAspectRatio = sourceAR),
                isImage = isImage
            )
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            previewEngine.setMediaSources(clips)
        }
    }

    val uiState: StateFlow<ProjectEditorUiState> = combine(
        _project,
        mediaItemDao.getByProjectId(projectId),
        _isImporting,
        gpxFileDao.getByProjectId(projectId),
        _gpxData,
        _gpxStats,
        _isImportingGpx,
        _storyMode,
        _storyTemplate,
        combine(
            combine(_selectedAspectRatio, _accentColor, _activityTitle, _clipSyncPoints, _autoSyncedClipIds) { ar, ac, at, cs, autoIds -> arrayOf(ar, ac, at, cs, autoIds) },
            _chartType,
            _showRouteMap,
            _metricConfig,
            _templateSlotCount
        ) { extra, ct, srm, mc, tsc -> arrayOf(*extra, ct, srm, mc, tsc) }
    ) { values ->
        val project = values[0] as ProjectEntity?
        val mediaItems = @Suppress("UNCHECKED_CAST") (values[1] as List<MediaItemEntity>)
        val importing = values[2] as Boolean
        val gpxFiles = @Suppress("UNCHECKED_CAST") (values[3] as List<GpxFileEntity>)
        val gpxData = values[4] as GpxData?
        val gpxStats = values[5] as GpxStats?
        val importingGpx = values[6] as Boolean
        val storyMode = values[7] as String
        val storyTemplate = values[8] as String
        @Suppress("UNCHECKED_CAST")
        val extra = values[9] as Array<*>
        val aspectRatio = extra[0] as SocialAspectRatio
        val accentColor = extra[1] as Int
        val activityTitle = extra[2] as String
        @Suppress("UNCHECKED_CAST")
        val clipSyncPoints = extra[3] as Map<UUID, ClipSyncPoint>
        @Suppress("UNCHECKED_CAST")
        val autoSyncedClipIds = extra[4] as Set<UUID>
        val chartType = extra[5] as ChartType?
        val showRouteMap = extra[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val metricConfig = extra[7] as List<MetricType>
        val templateSlotCount = extra[8] as Int
        ProjectEditorUiState(
            project = project,
            mediaItems = mediaItems,
            isLoading = project == null,
            isImporting = importing,
            gpxData = gpxData,
            gpxStats = gpxStats,
            gpxFiles = gpxFiles,
            isImportingGpx = importingGpx,
            storyMode = storyMode,
            storyTemplate = storyTemplate,
            selectedAspectRatio = aspectRatio,
            accentColor = accentColor,
            activityTitle = activityTitle,
            clipSyncPoints = clipSyncPoints,
            autoSyncedClipIds = autoSyncedClipIds,
            chartType = chartType,
            showRouteMap = showRouteMap,
            metricConfig = metricConfig,
            templateSlotCount = templateSlotCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectEditorUiState()
    )

    fun importMedia(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                val mediaDir = File(context.filesDir, "media/$projectId")
                mediaDir.mkdirs()
                val thumbnailDir = File(context.cacheDir, "thumbnails")
                thumbnailDir.mkdirs()

                for (uri in uris) {
                    try {
                        importSingleMedia(uri, mediaDir, thumbnailDir)
                    } catch (_: Exception) {
                        // Skip failed imports
                    }
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    private suspend fun importSingleMedia(
        uri: Uri,
        mediaDir: File,
        thumbnailDir: File
    ) {
        val mimeType = context.contentResolver.getType(uri)
        val isVideo = mimeType?.startsWith("video/") == true
        val mediaType = if (isVideo) "VIDEO" else "IMAGE"

        val mediaItemId = UUID.randomUUID()
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?: if (isVideo) "mp4" else "jpg"
        val destFile = File(mediaDir, "$mediaItemId.$extension")

        // Copy file to app storage
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return

        // Probe media metadata
        val localUri = Uri.fromFile(destFile)
        val info = MediaProber.probeMedia(context, localUri)

        // Generate and save thumbnail
        val bitmap = if (isVideo) {
            ThumbnailGenerator.generateVideoThumbnail(context, localUri)
        } else {
            ThumbnailGenerator.generateImageThumbnail(context, localUri)
        }
        bitmap?.let {
            val thumbnailFile = File(thumbnailDir, "$mediaItemId.jpg")
            ThumbnailGenerator.saveThumbnail(it, thumbnailFile)
        }

        // Insert into Room
        val entity = MediaItemEntity(
            id = mediaItemId,
            projectId = projectId,
            type = mediaType,
            sourcePath = uri.toString(),
            localCopyPath = destFile.absolutePath,
            durationMs = info.durationMs?.takeIf { it > 0L },
            width = info.width,
            height = info.height,
            rotation = info.rotation,
            codec = info.codec,
            hasAudio = info.hasAudio,
            audioCodec = info.audioCodec,
            fileSize = info.fileSize,
            videoCreatedAt = info.videoCreatedAt
        )
        mediaItemDao.insert(entity)
    }

    fun importGpxFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImportingGpx.value = true
            try {
                val existingFiles = gpxFileDao.getByProjectId(projectId).first()
                val name = getFileName(uri) ?: "track.gpx"
                val result = gpxImportManager.importGpxFile(projectId, uri, name)
                result.onSuccess { gpxFile ->
                    existingFiles.forEach { existing ->
                        gpxImportManager.deleteGpxFile(existing.id)
                    }
                    gpxFile.parsedData?.let { data ->
                        _gpxData.value = data
                        _gpxStats.value = GpxStatistics.computeFullStats(data)
                    }
                }
            } finally {
                _isImportingGpx.value = false
            }
        }
    }

    fun importFromStrava(activity: StravaActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImportingGpx.value = true
            try {
                val streamsResult = stravaApi.getActivityStreams(activity.id)
                streamsResult.onSuccess { streams ->
                    val gpxData = stravaStreamConverter.convert(activity, streams)
                    val existingFiles = gpxFileDao.getByProjectId(projectId).first()
                    val result = gpxImportManager.importFromGpxData(
                        projectId, activity.name, gpxData
                    )
                    result.onSuccess {
                        existingFiles.forEach { existing ->
                            gpxImportManager.deleteGpxFile(existing.id)
                        }
                        _gpxData.value = gpxData
                        _gpxStats.value = GpxStatistics.computeFullStats(gpxData)
                    }
                }
            } finally {
                _isImportingGpx.value = false
            }
        }
    }

    fun renameGpxFile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = gpxFileDao.getByProjectId(projectId).first().firstOrNull() ?: return@launch
            gpxFileDao.update(current.copy(name = trimmed))
        }
    }

    fun updateCanvasResolution(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentProject = _project.value ?: projectDao.getById(projectId) ?: return@launch
            val updated = currentProject.copy(
                resolutionWidth = width,
                resolutionHeight = height
            )
            projectDao.update(updated)
            _project.value = updated
        }
    }

    fun deleteGpxFile(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            gpxImportManager.deleteGpxFile(id)
            val remaining = gpxFileDao.getByProjectId(projectId).first()
            if (remaining.isEmpty()) {
                _gpxData.value = null
                _gpxStats.value = null
            } else {
                val gpxData = gpxImportManager.parseGpxFile(remaining.first())
                _gpxData.value = gpxData
                _gpxStats.value = gpxData?.let { GpxStatistics.computeFullStats(it) }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    fun deleteMedia(mediaItemId: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete local media file
            val mediaDir = File(context.filesDir, "media/$projectId")
            mediaDir.listFiles()
                ?.filter { it.nameWithoutExtension == mediaItemId.toString() }
                ?.forEach { it.delete() }

            // Delete thumbnail
            File(context.cacheDir, "thumbnails/$mediaItemId.jpg").delete()

            // Delete from Room
            mediaItemDao.deleteById(mediaItemId)
        }
    }

    fun setStoryMode(mode: String) {
        _storyMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateStoryMode(projectId, mode)
        }
        if (mode == StoryMode.LIVE_SYNC.name) {
            autoDetectSyncPoints()
        }
    }

    fun setStoryTemplate(template: String) {
        _storyTemplate.value = template
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateStoryTemplate(projectId, template)
        }
    }

    fun setAspectRatio(ratio: SocialAspectRatio) {
        _selectedAspectRatio.value = ratio
        viewModelScope.launch(Dispatchers.IO) {
            val p = _project.value ?: return@launch
            projectDao.update(p.copy(
                resolutionWidth = ratio.width,
                resolutionHeight = ratio.height
            ))
        }
    }

    fun setAccentColor(color: Int) {
        _accentColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateAccentColor(projectId, color)
        }
    }

    fun setActivityTitle(title: String) {
        _activityTitle.value = title
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateActivityTitle(projectId, title)
        }
    }

    fun setChartType(chartType: ChartType?) {
        _chartType.value = chartType
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateChartType(projectId, chartType?.name)
        }
    }

    fun setShowRouteMap(show: Boolean) {
        _showRouteMap.value = show
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateShowRouteMap(projectId, show)
        }
    }

    fun setMetricConfig(config: List<MetricType>) {
        _metricConfig.value = config
        val json = "[${config.joinToString(",") { "\"${it.name}\"" }}]"
        viewModelScope.launch(Dispatchers.IO) {
            projectDao.updateMetricConfig(projectId, json)
        }
    }

    /** Resolve metric config from project entity: use DB override if set, else sport defaults. */
    private fun resolveMetricConfig(project: ProjectEntity): List<MetricType> {
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

    fun setClipSyncPoint(clipId: UUID, syncPoint: ClipSyncPoint) {
        _clipSyncPoints.value = _clipSyncPoints.value + (clipId to syncPoint)
        // Manual sync clears auto-synced flag
        _autoSyncedClipIds.value = _autoSyncedClipIds.value - clipId
        viewModelScope.launch(Dispatchers.IO) {
            clipDao.updateSyncPoint(
                mediaItemId = clipId,
                gpxPointIndex = syncPoint.gpxPointIndex,
                gpxDistanceMeters = syncPoint.gpxDistanceMeters,
                isSynced = syncPoint.isSynced,
                isAutoSynced = false
            )
        }
    }

    fun removeClipSyncPoint(clipId: UUID) {
        _clipSyncPoints.value = _clipSyncPoints.value - clipId
        _autoSyncedClipIds.value = _autoSyncedClipIds.value - clipId
        viewModelScope.launch(Dispatchers.IO) {
            clipDao.updateSyncPoint(clipId, -1, 0.0, isSynced = false, isAutoSynced = false)
        }
    }

    fun revertToAutoSync(clipId: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            autoDetectSyncPoints()
        }
    }

    /**
     * Auto-detect clips whose videoCreatedAt falls within the GPX track timespan.
     * For each matching clip, compute the closest GPX point and set a sync point.
     */
    fun autoDetectSyncPoints() {
        viewModelScope.launch(Dispatchers.IO) {
            val gpxData = _gpxData.value ?: return@launch
            val points = gpxData.tracks.flatMap { it.segments }.flatMap { it.points }
            if (points.isEmpty()) return@launch

            val gpxStart = points.firstOrNull()?.time ?: return@launch
            val gpxEnd = points.lastOrNull()?.time ?: return@launch

            val mediaItems = mediaItemDao.getByProjectId(projectId).first()
            val tracks = trackDao.getByProjectId(projectId).first()
            val videoTrack = tracks.find { it.type == "VIDEO" } ?: return@launch
            val clipEntities = clipDao.getByTrackId(videoTrack.id).first()

            val newSyncPoints = mutableMapOf<UUID, ClipSyncPoint>()
            val autoIds = mutableSetOf<UUID>()

            // Precompute cumulative distances
            val cumulDist = DoubleArray(points.size)
            for (i in 1 until points.size) {
                cumulDist[i] = cumulDist[i - 1] + com.gpxvideo.lib.gpxparser.GpxStatistics.computeDistance(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude
                )
            }

            for (clipEntity in clipEntities) {
                val mediaItemId = clipEntity.mediaItemId ?: continue
                if (_clipSyncPoints.value[mediaItemId]?.isSynced == true) continue

                val mediaItem = mediaItems.find { it.id == mediaItemId } ?: continue
                // If videoCreatedAt is null (media imported before migration), try to re-probe
                var videoDate = mediaItem.videoCreatedAt
                if (videoDate == null && mediaItem.type == "VIDEO") {
                    val file = java.io.File(mediaItem.localCopyPath)
                    if (file.exists()) {
                        try {
                            val uri = android.net.Uri.fromFile(file)
                            val info = MediaProber.probeMedia(context, uri)
                            videoDate = info.videoCreatedAt
                            if (videoDate != null) {
                                mediaItemDao.update(mediaItem.copy(videoCreatedAt = videoDate))
                            }
                        } catch (_: Exception) { }
                    }
                }
                if (videoDate == null) continue

                // Check if video date falls within GPX timespan (with 1 hour tolerance)
                val tolerance = java.time.Duration.ofHours(1)
                if (videoDate.isBefore(gpxStart.minus(tolerance)) || videoDate.isAfter(gpxEnd.plus(tolerance))) continue

                // Find closest GPX point by timestamp
                var bestIdx = 0
                var bestDiff = Long.MAX_VALUE
                for (i in points.indices) {
                    val ptTime = points[i].time ?: continue
                    val diff = kotlin.math.abs(java.time.Duration.between(ptTime, videoDate).toMillis())
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestIdx = i
                    }
                }

                val syncPoint = ClipSyncPoint(
                    clipId = mediaItemId,
                    gpxPointIndex = bestIdx,
                    gpxDistanceMeters = cumulDist[bestIdx],
                    isSynced = true
                )
                newSyncPoints[mediaItemId] = syncPoint
                autoIds.add(mediaItemId)

                // Persist to DB
                clipDao.updateSyncPoint(mediaItemId, bestIdx, cumulDist[bestIdx], isSynced = true, isAutoSynced = true)
            }

            if (newSyncPoints.isNotEmpty()) {
                _clipSyncPoints.value = _clipSyncPoints.value + newSyncPoints
                _autoSyncedClipIds.value = _autoSyncedClipIds.value + autoIds
            }
        }
    }

    /** Force reload timeline-aware preview clips (call from Style screen). */
    fun reloadTimelinePreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mediaItemDao.getByProjectId(projectId).first()
            loadTimelineAwarePreviewClips(items)
        }
    }

    fun reorderMedia(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mediaItemDao.getByProjectId(projectId).first()
            if (fromIndex !in items.indices || toIndex !in items.indices) return@launch
            val mutable = items.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            // Re-insert with updated createdAt to maintain order
            val now = java.time.Instant.now()
            mutable.forEachIndexed { idx, item ->
                mediaItemDao.update(item.copy(createdAt = now.plusMillis(idx.toLong())))
            }
        }
    }

    /** Check if any imported video has Exif creation timestamps (enables Documentary mode). */
    fun hasVideoTimestamps(): Boolean {
        // For now, always allow both modes; Exif extraction will be enhanced later
        return true
    }
}
