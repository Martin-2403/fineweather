package com.example.fineweather.data.models

data class ForecastData(
    val daily: DailyForecast
)

data class DailyForecast(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>
)
