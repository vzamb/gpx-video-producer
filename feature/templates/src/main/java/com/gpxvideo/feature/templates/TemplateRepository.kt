package com.gpxvideo.feature.templates

import com.gpxvideo.core.database.dao.TemplateDao
import com.gpxvideo.core.database.entity.TemplateEntity
import com.gpxvideo.core.model.Anchor
import com.gpxvideo.core.model.AudioCodec
import com.gpxvideo.core.model.ExportFormat
import com.gpxvideo.core.model.OutputSettings
import com.gpxvideo.core.model.OverlayPosition
import com.gpxvideo.core.model.OverlayPreset
import com.gpxvideo.core.model.OverlaySize
import com.gpxvideo.core.model.OverlayStyle
import com.gpxvideo.core.model.Resolution
import com.gpxvideo.core.model.SportType
import com.gpxvideo.core.model.StylePreset
import com.gpxvideo.core.model.Template
import com.gpxvideo.core.model.TemplateTrack
import com.gpxvideo.core.model.TrackType
import com.gpxvideo.core.model.TransitionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun getAllTemplates(): Flow<List<Template>> =
        templateDao.getAll().map { entities -> entities.map { entityToTemplate(it) } }

    fun getBuiltInTemplates(): Flow<List<Template>> =
        templateDao.getBuiltIn().map { entities -> entities.map { entityToTemplate(it) } }

    fun getUserTemplates(): Flow<List<Template>> =
        templateDao.getUserTemplates().map { entities -> entities.map { entityToTemplate(it) } }

    fun getTemplatesBySport(sportType: SportType): Flow<List<Template>> =
        templateDao.getBySportType(sportType.name).map { entities ->
            entities.map { entityToTemplate(it) }
        }

    fun searchTemplatesByName(query: String): Flow<List<Template>> =
        templateDao.searchByName(query).map { entities ->
            entities.map { entityToTemplate(it) }
        }

    suspend fun getTemplateById(id: UUID): Template? =
        templateDao.getById(id)?.let { entityToTemplate(it) }

    suspend fun saveTemplate(template: Template) {
        templateDao.insert(templateToEntity(template))
    }

    suspend fun deleteTemplate(templateId: UUID) {
        templateDao.deleteById(templateId)
    }

    suspend fun initBuiltInTemplates() {
        val builtIn = BuiltInTemplates.all
        for (template in builtIn) {
            val existing = templateDao.getById(template.id)
            if (existing == null) {
                templateDao.insert(templateToEntity(template))
            }
        }
    }

    internal fun templateToEntity(template: Template): TemplateEntity {
        val trackLayoutJson = json.encodeToString(
            TemplateTrackListJson.serializer(),
            TemplateTrackListJson(template.trackLayout.map { track ->
                TemplateTrackJson(track.type.name, track.order, track.label)
            })
        )

        val overlayPresetsJson = json.encodeToString(
            OverlayPresetListJson.serializer(),
            OverlayPresetListJson(template.overlayPresets.map { preset ->
                OverlayPresetJson(
                    overlayType = preset.overlayType,
                    positionX = preset.defaultPosition.x,
                    positionY = preset.defaultPosition.y,
                    anchor = preset.defaultPosition.anchor.name,
                    sizeWidth = preset.defaultSize.width,
                    sizeHeight = preset.defaultSize.height,
                    backgroundColor = preset.defaultStyle.backgroundColor,
                    borderColor = preset.defaultStyle.borderColor,
                    borderWidth = preset.defaultStyle.borderWidth,
                    cornerRadius = preset.defaultStyle.cornerRadius,
                    opacity = preset.defaultStyle.opacity,
                    fontFamily = preset.defaultStyle.fontFamily,
                    fontSize = preset.defaultStyle.fontSize,
                    fontColor = preset.defaultStyle.fontColor,
                    shadowEnabled = preset.defaultStyle.shadowEnabled,
                    shadowColor = preset.defaultStyle.shadowColor,
                    shadowRadius = preset.defaultStyle.shadowRadius,
                    config = preset.config
                )
            })
        )

        val outputSettingsJson = json.encodeToString(
            OutputSettingsJson.serializer(),
            OutputSettingsJson(
                resolution = template.outputSettings.resolution.name,
                frameRate = template.outputSettings.frameRate,
                format = template.outputSettings.format.name,
                bitrateBps = template.outputSettings.bitrateBps,
                audioCodec = template.outputSettings.audioCodec.name
            )
        )

        val stylePresetJson = json.encodeToString(
            StylePresetJson.serializer(),
            StylePresetJson(
                primaryColor = template.stylePreset.primaryColor,
                secondaryColor = template.stylePreset.secondaryColor,
                accentColor = template.stylePreset.accentColor,
                fontFamily = template.stylePreset.fontFamily,
                backgroundOverlayColor = template.stylePreset.backgroundOverlayColor,
                transitionType = template.stylePreset.transitionType.name,
                transitionDurationMs = template.stylePreset.transitionDurationMs
            )
        )

        return TemplateEntity(
            id = template.id,
            name = template.name,
            description = template.description,
            sportType = template.sportType?.name,
            thumbnailPath = template.thumbnailPath,
            isBuiltIn = template.isBuiltIn,
            createdAt = template.createdAt,
            trackLayoutJson = trackLayoutJson,
            overlayPresetsJson = overlayPresetsJson,
            outputSettingsJson = outputSettingsJson,
            stylePresetJson = stylePresetJson
        )
    }

    internal fun entityToTemplate(entity: TemplateEntity): Template {
        val trackLayout = try {
            val parsed = json.decodeFromString(TemplateTrackListJson.serializer(), entity.trackLayoutJson)
            parsed.tracks.map { track ->
                TemplateTrack(
                    type = runCatching { TrackType.valueOf(track.type) }.getOrDefault(TrackType.VIDEO),
                    order = track.order,
                    label = track.label
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        val overlayPresets = try {
            val parsed = json.decodeFromString(OverlayPresetListJson.serializer(), entity.overlayPresetsJson)
            parsed.presets.map { preset ->
                OverlayPreset(
                    overlayType = preset.overlayType,
                    defaultPosition = OverlayPosition(
                        x = preset.positionX,
                        y = preset.positionY,
                        anchor = runCatching { Anchor.valueOf(preset.anchor) }.getOrDefault(Anchor.TOP_LEFT)
                    ),
                    defaultSize = OverlaySize(width = preset.sizeWidth, height = preset.sizeHeight),
                    defaultStyle = OverlayStyle(
                        backgroundColor = preset.backgroundColor,
                        borderColor = preset.borderColor,
                        borderWidth = preset.borderWidth,
                        cornerRadius = preset.cornerRadius,
                        opacity = preset.opacity,
                        fontFamily = preset.fontFamily,
                        fontSize = preset.fontSize,
                        fontColor = preset.fontColor,
                        shadowEnabled = preset.shadowEnabled,
                        shadowColor = preset.shadowColor,
                        shadowRadius = preset.shadowRadius
                    ),
                    config = preset.config
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        val outputSettings = try {
            val parsed = json.decodeFromString(OutputSettingsJson.serializer(), entity.outputSettingsJson)
            OutputSettings(
                resolution = runCatching { Resolution.valueOf(parsed.resolution) }.getOrDefault(Resolution.FHD_1080P),
                frameRate = parsed.frameRate,
                format = runCatching { ExportFormat.valueOf(parsed.format) }.getOrDefault(ExportFormat.MP4_H264),
                bitrateBps = parsed.bitrateBps,
                audioCodec = runCatching { AudioCodec.valueOf(parsed.audioCodec) }.getOrDefault(AudioCodec.AAC)
            )
        } catch (_: Exception) {
            OutputSettings()
        }

        val stylePreset = try {
            val parsed = json.decodeFromString(StylePresetJson.serializer(), entity.stylePresetJson)
            StylePreset(
                primaryColor = parsed.primaryColor,
                secondaryColor = parsed.secondaryColor,
                accentColor = parsed.accentColor,
                fontFamily = parsed.fontFamily,
                backgroundOverlayColor = parsed.backgroundOverlayColor,
                transitionType = runCatching { TransitionType.valueOf(parsed.transitionType) }
                    .getOrDefault(TransitionType.DISSOLVE),
                transitionDurationMs = parsed.transitionDurationMs
            )
        } catch (_: Exception) {
            StylePreset()
        }

        return Template(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            sportType = entity.sportType?.let { name ->
                runCatching { SportType.valueOf(name) }.getOrNull()
            },
            thumbnailPath = entity.thumbnailPath,
            isBuiltIn = entity.isBuiltIn,
            createdAt = entity.createdAt,
            trackLayout = trackLayout,
            overlayPresets = overlayPresets,
            outputSettings = outputSettings,
            stylePreset = stylePreset
        )
    }
}

// JSON serialization models

@Serializable
internal data class TemplateTrackJson(
    val type: String = "VIDEO",
    val order: Int = 0,
    val label: String? = null
)

@Serializable
internal data class TemplateTrackListJson(
    val tracks: List<TemplateTrackJson> = emptyList()
)

@Serializable
internal data class OverlayPresetJson(
    val overlayType: String = "",
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val anchor: String = "TOP_LEFT",
    val sizeWidth: Float = 0.2f,
    val sizeHeight: Float = 0.2f,
    val backgroundColor: Long? = null,
    val borderColor: Long? = null,
    val borderWidth: Float = 0f,
    val cornerRadius: Float = 8f,
    val opacity: Float = 1.0f,
    val fontFamily: String? = null,
    val fontSize: Float = 14f,
    val fontColor: Long = 0xFFFFFFFF,
    val shadowEnabled: Boolean = false,
    val shadowColor: Long = 0x80000000,
    val shadowRadius: Float = 4f,
    val config: Map<String, String> = emptyMap()
)

@Serializable
internal data class OverlayPresetListJson(
    val presets: List<OverlayPresetJson> = emptyList()
)

@Serializable
internal data class OutputSettingsJson(
    val resolution: String = "FHD_1080P",
    val frameRate: Int = 30,
    val format: String = "MP4_H264",
    val bitrateBps: Long = 10_000_000L,
    val audioCodec: String = "AAC"
)

@Serializable
internal data class StylePresetJson(
    val primaryColor: Long = 0xFFFF5722,
    val secondaryColor: Long = 0xFF4CAF50,
    val accentColor: Long = 0xFFFFC107,
    val fontFamily: String = "Inter",
    val backgroundOverlayColor: Long = 0x80000000,
    val transitionType: String = "DISSOLVE",
    val transitionDurationMs: Long = 500
)
