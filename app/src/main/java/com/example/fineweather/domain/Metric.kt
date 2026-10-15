package com.example.fineweather.domain

/** A daily weather quantity. Values are held in the domain unit; dividing the API value by [apiDivisor] gives it. */
enum class Metric(
    val label: String,
    val unit: String,
    val apiName: String,
    val apiDivisor: Double = 1.0,
) {
    TEMPERATURE("Temp", "°C", "temperature_2m_mean"),

    /** The API reports seconds per day. */
    SUNSHINE("Sun", "h/day", "sunshine_duration", 3600.0),
    PRECIPITATION("Rain", "mm", "precipitation_sum"),
}
