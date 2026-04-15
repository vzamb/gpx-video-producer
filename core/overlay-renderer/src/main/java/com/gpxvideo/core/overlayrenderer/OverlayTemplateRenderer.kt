package com.gpxvideo.core.overlayrenderer

import android.content.Context
import android.graphics.Bitmap
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.MetricType

/**
 * Loaded template wrapper.
 */
data class UnifiedTemplate(
    val id: String,
    val displayName: String,
    val loaded: LoadedSvgTemplate
)

/**
 * Template metadata for discovery/listing.
 */
data class UnifiedTemplateInfo(
    val id: String,
    val displayName: String,
    val description: String
)

/**
 * Loads and renders SVG overlay templates.
 *
 * Discovers templates by scanning assets/templates/ for SVG subdirectories
 * with meta.json + SVG files per aspect ratio.
 *
 * Usage (both preview and export):
 * ```
 * val renderer = OverlayTemplateRenderer(context)
 * val template = renderer.load("cinematic", width, height) ?: return
 * val bitmap = renderer.render(template, width, height, frameData, gpxData)
 * ```
 */
class OverlayTemplateRenderer(private val context: Context) {

    private val fontProvider = TemplateFontProvider(context)
    private val svgLoader = SvgTemplateLoader(context, fontProvider)
    private val svgRenderer = SvgOverlayRenderer(fontProvider)

    /** Discover all available SVG templates. */
    fun discoverTemplates(): List<UnifiedTemplateInfo> {
        return svgLoader.discoverTemplates().map {
            UnifiedTemplateInfo(it.id, it.displayName, it.description)
        }.sortedBy { it.id }
    }

    suspend fun load(templateId: String, width: Int, height: Int): UnifiedTemplate? {
        val svgTemplate = svgLoader.load(templateId, width, height) ?: return null
        return UnifiedTemplate(
            id = templateId,
            displayName = svgTemplate.meta.displayName,
            loaded = svgTemplate
        )
    }

    fun loadSync(templateId: String, width: Int, height: Int): UnifiedTemplate? {
        val svgTemplate = svgLoader.loadSync(templateId, width, height) ?: return null
        return UnifiedTemplate(
            id = templateId,
            displayName = svgTemplate.meta.displayName,
            loaded = svgTemplate
        )
    }

    /** Render a loaded template to a Bitmap. */
    fun render(
        template: UnifiedTemplate,
        width: Int,
        height: Int,
        frameData: OverlayFrameData,
        gpxData: GpxData?,
        activityTitle: String = "",
        showElevationChart: Boolean = true,
        showRouteMap: Boolean = true,
        metricConfig: List<MetricType> = MetricType.fallbackMetrics
    ): Bitmap {
        return svgRenderer.render(
            svg = template.loaded.svg,
            svgString = template.loaded.rawSvgString,
            width = width,
            height = height,
            frameData = frameData,
            gpxData = gpxData,
            activityTitle = activityTitle,
            showElevationChart = showElevationChart,
            showRouteMap = showRouteMap,
            metricConfig = metricConfig
        )
    }

    fun release() {
        svgRenderer.release()
    }
}
