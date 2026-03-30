package com.gpxvideo.feature.templates

import com.gpxvideo.core.database.dao.OverlayDao
import com.gpxvideo.core.database.dao.ProjectDao
import com.gpxvideo.core.database.dao.TimelineTrackDao
import com.gpxvideo.core.database.entity.TimelineTrackEntity
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.OverlayPreset
import com.gpxvideo.core.model.Template
import com.gpxvideo.feature.overlays.OverlayRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateApplicator @Inject constructor(
    private val overlayRepository: OverlayRepository,
    private val timelineTrackDao: TimelineTrackDao,
    private val templateRepository: TemplateRepository,
    private val projectDao: ProjectDao
) {
    suspend fun applyTemplate(
        template: Template,
        projectId: UUID,
        timelineClipId: UUID
    ) {
        // Create timeline tracks from template layout
        for (track in template.trackLayout) {
            timelineTrackDao.insert(
                TimelineTrackEntity(
                    projectId = projectId,
                    type = track.type.name,
                    order = track.order
                )
            )
        }

        // Create overlays from template presets
        for (preset in template.overlayPresets) {
            val overlay = presetToOverlayConfig(preset, projectId, timelineClipId)
            if (overlay != null) {
                overlayRepository.addOverlay(overlay)
            }
        }
    }

    suspend fun saveProjectAsTemplate(
        projectId: UUID,
        templateName: String,
        description: String?
    ): Template {
        val project = projectDao.getById(projectId)

        val overlays = mutableListOf<OverlayConfig>()
        overlayRepository.getOverlaysForProject(projectId).collect { list ->
            overlays.addAll(list)
            return@collect
        }

        val overlayPresets = overlays.map { overlay ->
            OverlayPreset(
                overlayType = overlayTypeFromConfig(overlay),
                defaultPosition = overlay.position,
                defaultSize = overlay.size,
                defaultStyle = overlay.style,
                config = extractConfigMap(overlay)
            )
        }

        val template = Template(
            name = templateName,
            description = description,
            sportType = project?.sportType?.let { name ->
                runCatching { com.gpxvideo.core.model.SportType.valueOf(name) }.getOrNull()
            },
            isBuiltIn = false,
            overlayPresets = overlayPresets
        )

        templateRepository.saveTemplate(template)
        return template
    }

    private fun presetToOverlayConfig(
        preset: OverlayPreset,
        projectId: UUID,
        timelineClipId: UUID
    ): OverlayConfig? {
        val field = preset.config["field"]
        return when (preset.overlayType) {
            "dynamic_map" -> OverlayConfig.DynamicMap(
                projectId = projectId,
                timelineClipId = timelineClipId,
                position = preset.defaultPosition,
                size = preset.defaultSize,
                style = preset.defaultStyle,
                mapStyle = preset.config["mapStyle"]?.let {
                    runCatching { com.gpxvideo.core.model.MapStyle.valueOf(it) }.getOrNull()
                } ?: com.gpxvideo.core.model.MapStyle.MINIMAL,
                showTrail = preset.config["showTrail"]?.toBooleanStrictOrNull() ?: true,
                followPosition = preset.config["followPosition"]?.toBooleanStrictOrNull() ?: true
            )
            "dynamic_altitude_profile" -> OverlayConfig.DynamicAltitudeProfile(
                projectId = projectId,
                timelineClipId = timelineClipId,
                position = preset.defaultPosition,
                size = preset.defaultSize,
                style = preset.defaultStyle
            )
            "dynamic_stat" -> OverlayConfig.DynamicStat(
                projectId = projectId,
                timelineClipId = timelineClipId,
                position = preset.defaultPosition,
                size = preset.defaultSize,
                style = preset.defaultStyle,
                field = field?.let {
                    runCatching { com.gpxvideo.core.model.DynamicField.valueOf(it) }.getOrNull()
                } ?: com.gpxvideo.core.model.DynamicField.CURRENT_SPEED
            )
            "static_map" -> OverlayConfig.StaticMap(
                projectId = projectId,
                timelineClipId = timelineClipId,
                position = preset.defaultPosition,
                size = preset.defaultSize,
                style = preset.defaultStyle,
                mapStyle = preset.config["mapStyle"]?.let {
                    runCatching { com.gpxvideo.core.model.MapStyle.valueOf(it) }.getOrNull()
                } ?: com.gpxvideo.core.model.MapStyle.MINIMAL,
                showStartEnd = preset.config["showStartEnd"]?.toBooleanStrictOrNull() ?: true
            )
            "static_stats" -> {
                val fields = preset.config["fields"]?.split(",")?.mapNotNull { name ->
                    runCatching { com.gpxvideo.core.model.StatField.valueOf(name.trim()) }.getOrNull()
                } ?: emptyList()
                val layout = preset.config["layout"]?.let {
                    runCatching { com.gpxvideo.core.model.StatsLayout.valueOf(it) }.getOrNull()
                } ?: com.gpxvideo.core.model.StatsLayout.GRID_2X2
                OverlayConfig.StaticStats(
                    projectId = projectId,
                    timelineClipId = timelineClipId,
                    position = preset.defaultPosition,
                    size = preset.defaultSize,
                    style = preset.defaultStyle,
                    fields = fields,
                    layout = layout
                )
            }
            "static_altitude_profile" -> OverlayConfig.StaticAltitudeProfile(
                projectId = projectId,
                timelineClipId = timelineClipId,
                position = preset.defaultPosition,
                size = preset.defaultSize,
                style = preset.defaultStyle
            )
            else -> null
        }
    }

    private fun overlayTypeFromConfig(config: OverlayConfig): String = when (config) {
        is OverlayConfig.StaticAltitudeProfile -> "static_altitude_profile"
        is OverlayConfig.StaticMap -> "static_map"
        is OverlayConfig.StaticStats -> "static_stats"
        is OverlayConfig.DynamicAltitudeProfile -> "dynamic_altitude_profile"
        is OverlayConfig.DynamicMap -> "dynamic_map"
        is OverlayConfig.DynamicStat -> "dynamic_stat"
    }

    private fun extractConfigMap(config: OverlayConfig): Map<String, String> = when (config) {
        is OverlayConfig.DynamicMap -> mapOf(
            "mapStyle" to config.mapStyle.name,
            "showTrail" to config.showTrail.toString(),
            "followPosition" to config.followPosition.toString()
        )
        is OverlayConfig.DynamicStat -> mapOf(
            "field" to config.field.name,
            "format" to config.format
        )
        is OverlayConfig.StaticMap -> mapOf(
            "mapStyle" to config.mapStyle.name,
            "showStartEnd" to config.showStartEnd.toString()
        )
        is OverlayConfig.StaticStats -> mapOf(
            "fields" to config.fields.joinToString(",") { it.name },
            "layout" to config.layout.name
        )
        is OverlayConfig.DynamicAltitudeProfile -> emptyMap()
        is OverlayConfig.StaticAltitudeProfile -> emptyMap()
    }
}
