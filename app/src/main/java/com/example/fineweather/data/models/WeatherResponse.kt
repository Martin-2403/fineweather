package com.example.fineweather.data.models

data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val generationtime_ms: Double,
    val utc_offset_seconds: Int,
    val timezone: String,
    val timezone_abbreviation: String,
    val elevation: Double,
    val daily_units: DailyUnits,
    val daily: Daily
)

data class DailyUnits(
    val time: String,
    val temperature_2m_mean: String
)

data class Daily(
    val time: List<String>,
    val temperature_2m_mean: List<Double>
)