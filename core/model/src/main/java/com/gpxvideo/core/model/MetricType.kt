package com.gpxvideo.core.model

/**
 * Available telemetry metrics that can be assigned to template slots.
 *
 * Each metric defines its display properties (label text, unit text) and a
 * [key] that the renderer uses to extract the value from frame data.
 *
 * Templates use generic positional slots (`metric_1_value`, `metric_1_label`,
 * `metric_1_unit`). The renderer maps each slot to the corresponding MetricType
 * based on the project's metric configuration.
 */
enum class MetricType(
    val displayName: String,
    val labelText: String,
    val unitText: String,
    val key: String
) {
    DISTANCE("Distance", "DIST", "km", "distance"),
    ELEVATION("Elevation", "ELEV", "m", "elevation"),
    PACE("Pace", "PACE", "min/km", "pace"),
    HR("Heart Rate", "HR", "bpm", "hr"),
    TIME("Time", "TIME", "", "time"),
    SPEED("Speed", "SPEED", "km/h", "speed"),
    GRADE("Grade", "GRADE", "%", "grade");

    companion object {
        /** Default metric ordering per sport type. */
        val defaultMetrics: Map<SportType, List<MetricType>> = mapOf(
            SportType.RUNNING to listOf(DISTANCE, PACE, HR, TIME),
            SportType.CYCLING to listOf(DISTANCE, SPEED, ELEVATION, HR),
            SportType.WALKING to listOf(DISTANCE, TIME, ELEVATION, HR),
        )

        /** Fallback metric list for sport types without an explicit mapping. */
        val fallbackMetrics: List<MetricType> = listOf(DISTANCE, PACE, HR, TIME)

        /** Resolve the metric config for a sport, using defaults or fallback. */
        fun forSport(sportType: SportType): List<MetricType> =
            defaultMetrics[sportType] ?: fallbackMetrics
    }
}
