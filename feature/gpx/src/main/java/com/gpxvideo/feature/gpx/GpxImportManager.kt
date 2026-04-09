package com.gpxvideo.feature.gpx

import android.content.Context
import android.net.Uri
import com.gpxvideo.core.database.dao.GpxFileDao
import com.gpxvideo.core.database.entity.GpxFileEntity
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxFile
import com.gpxvideo.lib.gpxparser.GpxParser
import com.gpxvideo.lib.gpxparser.TcxParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class GpxImportManager @Inject constructor(
    private val gpxFileDao: GpxFileDao,
    @ApplicationContext private val context: Context
) {

    suspend fun importGpxFile(projectId: UUID, uri: Uri, name: String): Result<GpxFile> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "gpx/$projectId")
                dir.mkdirs()

                val destFile = File(dir, "${UUID.randomUUID()}_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Cannot open input file"))

                val gpxData = parseFile(destFile)

                val gpxFileId = UUID.randomUUID()
                val entity = GpxFileEntity(
                    id = gpxFileId,
                    projectId = projectId,
                    name = name,
                    filePath = destFile.absolutePath
                )
                gpxFileDao.insert(entity)

                Result.success(
                    GpxFile(
                        id = gpxFileId,
                        projectId = projectId,
                        name = name,
                        filePath = destFile.absolutePath,
                        parsedData = gpxData
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun parseGpxFile(gpxFileEntity: GpxFileEntity): GpxData? {
        return withContext(Dispatchers.IO) {
            try {
                parseFile(File(gpxFileEntity.filePath))
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun importFromGpxData(projectId: UUID, name: String, gpxData: GpxData): Result<GpxFile> {
        return withContext(Dispatchers.IO) {
            try {
                val gpxFileId = UUID.randomUUID()
                // Write a minimal GPX file for persistence
                val dir = File(context.filesDir, "gpx/$projectId")
                dir.mkdirs()
                val destFile = File(dir, "${gpxFileId}_$name.gpx")
                destFile.writeText(buildMinimalGpx(gpxData))

                val entity = GpxFileEntity(
                    id = gpxFileId,
                    projectId = projectId,
                    name = name,
                    filePath = destFile.absolutePath
                )
                gpxFileDao.insert(entity)

                Result.success(
                    GpxFile(
                        id = gpxFileId,
                        projectId = projectId,
                        name = name,
                        filePath = destFile.absolutePath,
                        parsedData = gpxData
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildMinimalGpx(gpxData: GpxData): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="GpxVideoProducer-Strava">""")
        for (track in gpxData.tracks) {
            sb.appendLine("  <trk>")
            track.name?.let { sb.appendLine("    <name>$it</name>") }
            for (segment in track.segments) {
                sb.appendLine("    <trkseg>")
                for (p in segment.points) {
                    sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""")
                    p.elevation?.let { sb.append("<ele>$it</ele>") }
                    p.time?.let { sb.append("<time>$it</time>") }
                    sb.appendLine("</trkpt>")
                }
                sb.appendLine("    </trkseg>")
            }
            sb.appendLine("  </trk>")
        }
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    suspend fun deleteGpxFile(gpxFileId: UUID) {
        withContext(Dispatchers.IO) {
            gpxFileDao.getById(gpxFileId)?.let { entity ->
                File(entity.filePath).delete()
                gpxFileDao.deleteById(gpxFileId)
            }
        }
    }

    private suspend fun parseFile(file: File): GpxData? {
        return when (file.extension.lowercase()) {
            "tcx" -> file.inputStream().use { TcxParser.parse(it) }
            "gpx" -> file.inputStream().use { GpxParser.parse(it) }
            else -> {
                val header = file.bufferedReader().use {
                    val buf = CharArray(500)
                    val read = it.read(buf)
                    if (read > 0) String(buf, 0, read) else ""
                }
                file.inputStream().use { stream ->
                    if (header.contains("<TrainingCenterDatabase")) {
                        TcxParser.parse(stream)
                    } else {
                        GpxParser.parse(stream)
                    }
                }
            }
        }
    }
}
