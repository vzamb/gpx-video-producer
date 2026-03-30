package com.gpxvideo.feature.project

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpxvideo.core.database.dao.MediaItemDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.entity.MediaItemEntity
import com.gpxvideo.core.database.entity.ProjectEntity
import com.gpxvideo.lib.mediautils.MediaProber
import com.gpxvideo.lib.mediautils.ThumbnailGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class ProjectEditorUiState(
    val project: ProjectEntity? = null,
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false
)

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectDao: ProjectDao,
    private val mediaItemDao: MediaItemDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val projectId: UUID = UUID.fromString(
        checkNotNull(savedStateHandle.get<String>("projectId"))
    )

    private val _project = MutableStateFlow<ProjectEntity?>(null)
    private val _isImporting = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            _project.value = projectDao.getById(projectId)
        }
    }

    val uiState: StateFlow<ProjectEditorUiState> = combine(
        _project,
        mediaItemDao.getByProjectId(projectId),
        _isImporting
    ) { project, mediaItems, importing ->
        ProjectEditorUiState(
            project = project,
            mediaItems = mediaItems,
            isLoading = project == null,
            isImporting = importing
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
            durationMs = info.durationMs,
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
}
