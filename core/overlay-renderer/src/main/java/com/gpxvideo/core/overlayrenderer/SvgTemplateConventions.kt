package com.gpxvideo.core.overlayrenderer

/**
 * Naming conventions for SVG template layers.
 *
 * Templates follow a consistent naming scheme so the renderer knows how to
 * handle each element:
 *
 * ## Metric text layers (generic positional slots)
 * - `metric_N_value` → Dynamic metric value (e.g. "12.3"). Drawn natively with fill + stroke.
 * - `metric_N_label` → Dynamic metric label (e.g. "DIST"). Drawn natively with fill + stroke.
 * - `metric_N_unit`  → Dynamic metric unit (e.g. "km"). Drawn natively with fill + stroke.
 * - `title_text`     → Activity title. Dynamic, drawn natively with accent color.
 *
 * The renderer maps each slot to a [MetricType] from the project's metric config.
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
 * - `card_N`  → Background card shape for metric slot N (rect, rounded rect)
 * - `scrim`   → Semi-transparent gradient overlay
 *
 * ## SVG attributes on metric text elements
 * - `font-family`   → Maps to a bundled .ttf via TemplateFontProvider
 * - `font-size`     → Text size in SVG units (scaled to canvas)
 * - `fill`          → Fill color (e.g. "#FFFFFF")
 * - `stroke`        → Stroke/outline color (e.g. "#333333")
 * - `stroke-width`  → Stroke width in SVG units
 * - `text-anchor`   → Alignment: "start", "middle", "end"
 * - `font-weight`   → "bold" or "normal" (or numeric 700/400)
 */
object SvgTemplateConventions {

    // ── Layer name patterns ────────────────────────────────────────────

    const val TITLE_TEXT = "title_text"
    const val CARD_PREFIX = "card_"
    const val SCRIM = "scrim"

    /** Regex matching generic metric slot IDs: metric_1_value, metric_2_label, etc. */
    private val METRIC_SLOT_REGEX = Regex("""^metric_(\d+)_(value|label|unit)$""")

    /** Regex matching card IDs: card_1, card_2, etc. */
    private val CARD_SLOT_REGEX = Regex("""^card_(\d+)$""")

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
        isMetricSlot(id) || id == TITLE_TEXT

    /** Returns true if the id matches `metric_N_(value|label|unit)`. */
    fun isMetricSlot(id: String): Boolean = METRIC_SLOT_REGEX.matches(id)

    /** Extract the slot number from a metric id (e.g. "metric_2_value" → 2), or null. */
    fun metricSlotNumber(id: String): Int? = METRIC_SLOT_REGEX.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull()

    /** Extract the slot part from a metric id (e.g. "metric_2_value" → "value"), or null. */
    fun metricSlotPart(id: String): String? = METRIC_SLOT_REGEX.matchEntire(id)?.groupValues?.get(2)

    /** Returns true if the id matches `card_N`. */
    fun isCardSlot(id: String): Boolean = CARD_SLOT_REGEX.matches(id)

    /** Extract the slot number from a card id (e.g. "card_2" → 2), or null. */
    fun cardSlotNumber(id: String): Int? = CARD_SLOT_REGEX.matchEntire(id)?.groupValues?.get(1)?.toIntOrNull()

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

    // ── Slot count detection ──────────────────────────────────────────

    /** Count how many metric slots the SVG defines (by scanning for metric_N_value). */
    fun countSlots(svgString: String): Int {
        val slots = mutableSetOf<Int>()
        val regex = Regex("""id\s*=\s*"metric_(\d+)_value"""")
        for (m in regex.findAll(svgString)) {
            m.groupValues[1].toIntOrNull()?.let { slots.add(it) }
        }
        return slots.size
    }
}
