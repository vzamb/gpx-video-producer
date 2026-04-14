package com.gpxvideo.core.overlayrenderer

/**
 * Naming conventions for SVG template layers.
 *
 * Templates follow a consistent naming scheme so the renderer knows how to
 * handle each element:
 *
 * ## Text layers
 * - `stat_*`   → Dynamic stat values (distance, elevation, pace, hr, time, grade, speed).
 *                Hidden before SVG render; drawn natively with fill + stroke for outlined text.
 * - `label_*`  → Static labels ("DIST", "TIME", etc.). Exported as vector paths from Figma;
 *                rendered by AndroidSVG as-is. No font file needed.
 * - `title_text` → Activity title. Dynamic, drawn natively with accent color.
 *
 * ## Chart / map layers
 * - `elevation_chart` → Group (`<g>`) containing chart style sub-elements:
 *     - `background` → Rect with fill/stroke for chart bg
 *     - `line`       → Stroke style for the visited elevation line
 *     - `area`       → Fill style for the gradient area below the line
 *     - `full_path`  → Stroke style for the unvisited (dimmed) line
 *     - `dot`        → Circle for the current position indicator
 *     - `glow`       → Circle for the outer glow around the dot
 *
 * - `route_map` → Group (`<g>`) with same sub-elements as above (for route rendering).
 *
 * ## Visual layers
 * - `card_*`  → Background card shapes (rects, rounded rects)
 * - `scrim`   → Semi-transparent gradient overlay
 *
 * ## SVG attributes on `stat_*` text elements
 * - `font-family`   → Maps to a bundled .ttf via TemplateFontProvider
 * - `font-size`     → Text size in SVG units (scaled to canvas)
 * - `fill`          → Fill color (e.g. "#FFFFFF")
 * - `stroke`        → Stroke/outline color (e.g. "#333333")
 * - `stroke-width`  → Stroke width in SVG units
 * - `text-anchor`   → Alignment: "start", "middle", "end"
 * - `font-weight`   → "bold" or "normal" (or numeric 700/400)
 */
object SvgTemplateConventions {

    // ── Layer name prefixes ────────────────────────────────────────────

    const val STAT_PREFIX = "stat_"
    const val LABEL_PREFIX = "label_"
    const val TITLE_TEXT = "title_text"
    const val CARD_PREFIX = "card_"
    const val SCRIM = "scrim"

    // ── Chart / map layer ids ──────────────────────────────────────────

    const val ELEVATION_CHART = "elevation_chart"
    const val ROUTE_MAP = "route_map"

    // ── Chart sub-group names ──────────────────────────────────────────

    const val SUB_BACKGROUND = "background"
    const val SUB_LINE = "line"
    const val SUB_ROUTE = "route"
    const val SUB_AREA = "area"
    const val SUB_FULL_PATH = "full_path"
    const val SUB_FULL_ROUTE = "full_route"
    const val SUB_DOT = "dot"
    const val SUB_GLOW = "glow"

    val CHART_SUB_GROUPS = setOf(
        SUB_BACKGROUND, SUB_LINE, SUB_ROUTE, SUB_AREA,
        SUB_FULL_PATH, SUB_FULL_ROUTE, SUB_DOT, SUB_GLOW
    )

    // ── Text layer detection ───────────────────────────────────────────

    /** Returns true if this layer should be rendered natively (not by SVG). */
    fun isDynamicTextLayer(id: String): Boolean =
        id.startsWith(STAT_PREFIX) || id == TITLE_TEXT

    /** Returns true if this layer is a chart or map placeholder. */
    fun isChartOrMapLayer(id: String): Boolean =
        id == ELEVATION_CHART || id == ROUTE_MAP

    /** Returns true if this layer is a chart sub-group whose style should be extracted. */
    fun isChartSubGroup(id: String): Boolean {
        val normalized = stripFigmaSuffix(id).lowercase()
        return normalized in CHART_SUB_GROUPS
    }

    /** Normalizes a Figma-deduplicated ID (e.g. "glow_2" → "glow"). */
    fun stripFigmaSuffix(id: String): String =
        id.replace(Regex("""_\d+$"""), "")

    // ── Stat name → OverlayFrameData mapping ──────────────────────────

    val STAT_KEYS = setOf(
        "stat_distance", "stat_distance_unit",
        "stat_elevation", "stat_elevation_unit",
        "stat_pace", "stat_pace_unit",
        "stat_hr", "stat_hr_unit",
        "stat_time",
        "stat_grade", "stat_grade_unit",
        "stat_speed", "stat_speed_unit"
    )
}
