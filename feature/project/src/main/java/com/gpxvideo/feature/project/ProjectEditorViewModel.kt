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
import com.gpxvideo.core.database.entity.GpxFileEntity
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.ProjectEntity
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.SocialAspectRatio
import com.gpxvideo.feature.gpx.GpxImportManager
import com.gpxvideo.lib.gpxparser.GpxStatistics
import com.gpxvideo.lib.gpxparser.GpxStats
import com.gpxvideo.lib.mediautils.MediaProber
import com.gpxvideo.lib.mediautils.ThumbnailGenerator
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
    val storyMode: String = "HYPER_LAPSE",
    val storyTemplate: String = "CINEMATIC",
    val selectedAspectRatio: SocialAspectRatio = SocialAspectRatio.PORTRAIT_9_16,
    val accentColor: Int = 0xFF448AFF.toInt(),
    val activityTitle: String = ""
)

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectDao: ProjectDao,
    private val mediaItemDao: MediaItemDao,
    private val gpxFileDao: GpxFileDao,
    private val gpxImportManager: GpxImportManager,
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
    private val _storyMode = MutableStateFlow("HYPER_LAPSE")
    private val _storyTemplate = MutableStateFlow("CINEMATIC")
    private val _selectedAspectRatio = MutableStateFlow(SocialAspectRatio.PORTRAIT_9_16)
    private val _accentColor = MutableStateFlow(0xFF448AFF.toInt())
    private val _activityTitle = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val project = projectDao.getById(projectId)
            _project.value = project
            project?.let {
                _storyMode.value = it.storyMode
                _storyTemplate.value = it.storyTemplate
                // Restore aspect ratio from saved resolution
                val savedRatio = SocialAspectRatio.entries.find { r ->
                    r.width == it.resolutionWidth && r.height == it.resolutionHeight
                } ?: SocialAspectRatio.PORTRAIT_9_16
                _selectedAspectRatio.value = savedRatio
            }
        }
        viewModelScope.launch {
            val existingFiles = gpxFileDao.getByProjectId(projectId).first()
            if (existingFiles.isNotEmpty()) {
                val gpxData = gpxImportManager.parseGpxFile(existingFiles.first())
                if (gpxData != null) {
                    _gpxData.value = gpxData
                    _gpxStats.value = GpxStatistics.computeFullStats(gpxData)
                }
            }
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
        _selectedAspectRatio,
        _accentColor,
        _activityTitle
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
        val aspectRatio = values[9] as SocialAspectRatio
        val accentColor = values[10] as Int
        val activityTitle = values[11] as String
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
            activityTitle = activityTitle
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
            fileSize = info.fileSize
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
    }

    fun setActivityTitle(title: String) {
        _activityTitle.value = title
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
