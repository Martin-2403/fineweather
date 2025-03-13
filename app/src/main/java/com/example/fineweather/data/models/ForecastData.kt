package com.example.fineweather.data.models

data class ForecastData(
    val daily: DailyForecast
)

data class DailyForecast(
    val time: List<String>,
    val temperature2mMax: List<Double>,
    val temperature2mMin: List<Double>
)
