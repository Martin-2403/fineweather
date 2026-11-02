package com.example.fineweather.data.models

import com.example.fineweather.domain.Metric

data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val generationtime_ms: Double,
    val utc_offset_seconds: Int,
    val timezone: String,
    val timezone_abbreviation: String,
    val elevation: Double,
    val daily_units: DailyUnits,
    val daily: Daily,
)

/** Gson leaves keys missing from the response null, so everything but [time] is nullable. */
data class DailyUnits(
    val time: String,
    val temperature_2m_mean: String? = null,
    val sunshine_duration: String? = null,
    val precipitation_sum: String? = null,
)

data class Daily(
    val time: List<String>,
    val temperature_2m_mean: List<Double?>? = null,
    val sunshine_duration: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
) {
    /** The raw API values of [metric], or null if the response did not contain it. */
    fun values(metric: Metric): List<Double?>? =
        when (metric) {
            Metric.TEMPERATURE -> temperature_2m_mean
            Metric.SUNSHINE -> sunshine_duration
            Metric.PRECIPITATION -> precipitation_sum
        }
}
