package com.gpxvideo.feature.overlays

import com.gpxvideo.core.database.dao.OverlayDao
import com.gpxvideo.core.database.entity.OverlayEntity
import com.gpxvideo.core.model.Anchor
import com.gpxvideo.core.model.MapStyle
import com.gpxvideo.core.model.OverlayConfig
import com.gpxvideo.core.model.OverlayPosition
import com.gpxvideo.core.model.OverlaySize
import com.gpxvideo.core.model.OverlayStyle
import com.gpxvideo.core.model.StatField
import com.gpxvideo.core.model.StatsLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepository @Inject constructor(
    private val overlayDao: OverlayDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getOverlaysForProject(projectId: UUID): Flow<List<OverlayConfig>> {
        return overlayDao.getByProjectId(projectId).map { entities ->
            entities.mapNotNull { entityToConfig(it) }
        }
    }

    suspend fun addOverlay(overlay: OverlayConfig) {
        overlayDao.insert(configToEntity(overlay))
    }

    suspend fun updateOverlay(overlay: OverlayConfig) {
        overlayDao.update(configToEntity(overlay))
    }

    suspend fun deleteOverlay(overlayId: UUID) {
        overlayDao.deleteById(overlayId)
    }

    private fun configToEntity(config: OverlayConfig): OverlayEntity {
        val (overlayType, configJson) = when (config) {
            is OverlayConfig.StaticAltitudeProfile -> "static_altitude_profile" to json.encodeToString(
                AltitudeProfileJson.serializer(),
                AltitudeProfileJson(config.lineColor, config.fillColor, config.showGrid, config.showLabels)
            )
            is OverlayConfig.StaticMap -> "static_map" to json.encodeToString(
                StaticMapJson.serializer(),
                StaticMapJson(config.routeColor, config.routeWidth, config.showStartEnd, config.mapStyle.name)
            )
            is OverlayConfig.StaticStats -> "static_stats" to json.encodeToString(
                StaticStatsJson.serializer(),
                StaticStatsJson(config.fields.map { it.name }, config.layout.name)
            )
            is OverlayConfig.DynamicAltitudeProfile -> "dynamic_altitude_profile" to json.encodeToString(
                DynamicAltitudeProfileJson.serializer(),
                DynamicAltitudeProfileJson(config.lineColor, config.markerColor, config.trailColor, config.syncMode.name)
            )
            is OverlayConfig.DynamicMap -> "dynamic_map" to json.encodeToString(
                DynamicMapJson.serializer(),
                DynamicMapJson(config.mapStyle.name, config.routeColor, config.showTrail, config.followPosition, config.syncMode.name)
            )
            is OverlayConfig.DynamicStat -> "dynamic_stat" to json.encodeToString(
                DynamicStatJson.serializer(),
                DynamicStatJson(config.field.name, config.syncMode.name, config.format)
            )
        }

        val styleJson = json.encodeToString(
            StyleJson.serializer(),
            StyleJson(
                backgroundColor = config.style.backgroundColor,
                borderColor = config.style.borderColor,
                borderWidth = config.style.borderWidth,
                cornerRadius = config.style.cornerRadius,
                opacity = config.style.opacity,
                fontFamily = config.style.fontFamily,
                fontSize = config.style.fontSize,
                fontColor = config.style.fontColor,
                shadowEnabled = config.style.shadowEnabled,
                shadowColor = config.style.shadowColor,
                shadowRadius = config.style.shadowRadius
            )
        )

        return OverlayEntity(
            id = config.id,
            projectId = config.projectId,
            timelineClipId = config.timelineClipId,
            name = config.name,
            overlayType = overlayType,
            positionX = config.position.x,
            positionY = config.position.y,
            anchor = config.position.anchor.name,
            sizeWidth = config.size.width,
            sizeHeight = config.size.height,
            styleJson = styleJson,
            configJson = configJson
        )
    }

    private fun entityToConfig(entity: OverlayEntity): OverlayConfig? {
        val position = OverlayPosition(
            x = entity.positionX,
            y = entity.positionY,
            anchor = runCatching { Anchor.valueOf(entity.anchor) }.getOrDefault(Anchor.TOP_LEFT)
        )
        val size = OverlaySize(width = entity.sizeWidth, height = entity.sizeHeight)
        val style = try {
            val s = json.decodeFromString(StyleJson.serializer(), entity.styleJson)
            OverlayStyle(
                backgroundColor = s.backgroundColor,
                borderColor = s.borderColor,
                borderWidth = s.borderWidth,
                cornerRadius = s.cornerRadius,
                opacity = s.opacity,
                fontFamily = s.fontFamily,
                fontSize = s.fontSize,
                fontColor = s.fontColor,
                shadowEnabled = s.shadowEnabled,
                shadowColor = s.shadowColor,
                shadowRadius = s.shadowRadius
            )
        } catch (_: Exception) {
            OverlayStyle()
        }

        return try {
            when (entity.overlayType) {
                "static_altitude_profile" -> {
                    val c = json.decodeFromString(AltitudeProfileJson.serializer(), entity.configJson)
                    OverlayConfig.StaticAltitudeProfile(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        lineColor = c.lineColor, fillColor = c.fillColor, showGrid = c.showGrid, showLabels = c.showLabels
                    )
                }
                "static_map" -> {
                    val c = json.decodeFromString(StaticMapJson.serializer(), entity.configJson)
                    OverlayConfig.StaticMap(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        routeColor = c.routeColor, routeWidth = c.routeWidth, showStartEnd = c.showStartEnd,
                        mapStyle = runCatching { MapStyle.valueOf(c.mapStyle) }.getOrDefault(MapStyle.MINIMAL)
                    )
                }
                "static_stats" -> {
                    val c = json.decodeFromString(StaticStatsJson.serializer(), entity.configJson)
                    OverlayConfig.StaticStats(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        fields = c.fields.mapNotNull { name -> runCatching { StatField.valueOf(name) }.getOrNull() },
                        layout = runCatching { StatsLayout.valueOf(c.layout) }.getOrDefault(StatsLayout.GRID_2X2)
                    )
                }
                "dynamic_altitude_profile" -> {
                    val c = json.decodeFromString(DynamicAltitudeProfileJson.serializer(), entity.configJson)
                    OverlayConfig.DynamicAltitudeProfile(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        lineColor = c.lineColor, markerColor = c.markerColor, trailColor = c.trailColor,
                        syncMode = runCatching { com.gpxvideo.core.model.SyncMode.valueOf(c.syncMode) }
                            .getOrDefault(com.gpxvideo.core.model.SyncMode.GPX_TIMESTAMP)
                    )
                }
                "dynamic_map" -> {
                    val c = json.decodeFromString(DynamicMapJson.serializer(), entity.configJson)
                    OverlayConfig.DynamicMap(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        mapStyle = runCatching { MapStyle.valueOf(c.mapStyle) }.getOrDefault(MapStyle.MINIMAL),
                        routeColor = c.routeColor, showTrail = c.showTrail, followPosition = c.followPosition,
                        syncMode = runCatching { com.gpxvideo.core.model.SyncMode.valueOf(c.syncMode) }
                            .getOrDefault(com.gpxvideo.core.model.SyncMode.GPX_TIMESTAMP)
                    )
                }
                "dynamic_stat" -> {
                    val c = json.decodeFromString(DynamicStatJson.serializer(), entity.configJson)
                    OverlayConfig.DynamicStat(
                        id = entity.id, projectId = entity.projectId, name = entity.name,
                        timelineClipId = entity.timelineClipId, position = position, size = size, style = style,
                        field = runCatching { com.gpxvideo.core.model.DynamicField.valueOf(c.field) }
                            .getOrDefault(com.gpxvideo.core.model.DynamicField.CURRENT_SPEED),
                        syncMode = runCatching { com.gpxvideo.core.model.SyncMode.valueOf(c.syncMode) }
                            .getOrDefault(com.gpxvideo.core.model.SyncMode.GPX_TIMESTAMP),
                        format = c.format
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
internal data class StyleJson(
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
    val shadowRadius: Float = 4f
)

@Serializable
internal data class AltitudeProfileJson(
    val lineColor: Long = 0xFF4CAF50,
    val fillColor: Long = 0x804CAF50,
    val showGrid: Boolean = true,
    val showLabels: Boolean = true
)

@Serializable
internal data class StaticMapJson(
    val routeColor: Long = 0xFFFF5722,
    val routeWidth: Float = 3f,
    val showStartEnd: Boolean = true,
    val mapStyle: String = "MINIMAL"
)

@Serializable
internal data class StaticStatsJson(
    val fields: List<String> = emptyList(),
    val layout: String = "GRID_2X2"
)

@Serializable
internal data class DynamicAltitudeProfileJson(
    val lineColor: Long = 0xFF4CAF50,
    val markerColor: Long = 0xFFFF5722,
    val trailColor: Long = 0x804CAF50,
    val syncMode: String = "GPX_TIMESTAMP"
)

@Serializable
internal data class DynamicMapJson(
    val mapStyle: String = "MINIMAL",
    val routeColor: Long = 0xFFFF5722,
    val showTrail: Boolean = true,
    val followPosition: Boolean = true,
    val syncMode: String = "GPX_TIMESTAMP"
)

@Serializable
internal data class DynamicStatJson(
    val field: String = "CURRENT_SPEED",
    val syncMode: String = "GPX_TIMESTAMP",
    val format: String = ""
)
