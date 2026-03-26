package com.example.fineweather.data.models

import com.example.fineweather.data.local.entities.WeatherEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FineWeatherData(
    val location: String,
    val coordinates: Pair<Double, Double>,
    val id: String,
    val country: String? = null,
) {
    val timestamp: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    var currentMonthAverage: Double? = null
    var currentAverage: Double? = null
    var forecastAverage: Double? = null
    var historicMonthlyAverage: Double? = null
    var historicTimePeriodAverage: Double? = null

    var currentTimePeriod: Int = 31
    var forecastTimePeriod: Int = 14
    var historicTimePeriod: Int = 30

    val tempDifference: Double?
        get() =
            if (currentMonthAverage == null ||
                historicMonthlyAverage == null
            ) {
                null
            } else {
                currentMonthAverage!! - historicMonthlyAverage!!
            }
}

// Helper function to build a FineWeatherData object from an entity
fun FineWeatherData.toEntity(locationId: Long): WeatherEntity =
    WeatherEntity(
        id = this.id,
        date = this.timestamp, // "yyyy-MM-dd"
        locationName = this.location,
        latitude = this.coordinates.first,
        longitude = this.coordinates.second,
        currentAverage = this.currentAverage,
        currentMonthAverage = this.currentMonthAverage,
        forecastAverage7 = null,
        forecastAverage14 = this.forecastAverage,
        forecastDate7 = null,
        forecastDate14 = this.timestamp,
        historicMonthlyAverageClassic = null,
        historicMonthlyAverageCurrent = this.historicMonthlyAverage,
        country = this.country,
    )
