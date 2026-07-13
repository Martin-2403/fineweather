package com.example.fineweather.data.models

enum class TemperatureComparisonMode(
    val id: String,
    val label: String,
    val description: String,
) {
    ABSOLUTE_DELTA(
        id = "absolute_delta",
        label = "Absolute delta",
        description = "Uses the raw temperature difference in degrees Celsius.",
    ),
    NORMALIZED_ANOMALY(
        id = "normalized_anomaly",
        label = "Normalized anomaly",
        description = "Uses standard deviation to show how unusual the month is for that place.",
    );

    companion object {
        fun fromId(id: String?): TemperatureComparisonMode =
            entries.firstOrNull { it.id == id } ?: ABSOLUTE_DELTA
    }
}
