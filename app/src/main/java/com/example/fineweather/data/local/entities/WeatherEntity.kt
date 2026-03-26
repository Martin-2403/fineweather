package com.example.fineweather.data.local.entities

import androidx.room.Entity
import com.example.fineweather.data.models.FineWeatherData

@Entity(
    tableName = "weather_history",
    primaryKeys = ["id"],
)
data class WeatherEntity(
    val id: String,
    val date: String, // "yyyy-MM-dd"
    val locationName: String,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val currentAverage: Double?,
    val currentMonthAverage: Double?,
    val forecastAverage7: Double?,
    val forecastAverage14: Double?,
    val forecastDate7: String?,
    val forecastDate14: String?,
    val historicMonthlyAverageClassic: Double?,
    val historicMonthlyAverageCurrent: Double?,
)

// In WeatherEntity.kt
fun WeatherEntity.toUiModel(): FineWeatherData {
    val data =
        FineWeatherData(
            this.locationName,
            Pair(this.latitude, this.longitude),
            this.id,
            this.country,
        )
    data.currentAverage = this.currentAverage
    data.currentMonthAverage = this.currentMonthAverage
    data.forecastAverage = this.forecastAverage14 ?: this.forecastAverage7
    data.historicMonthlyAverage =
        this.historicMonthlyAverageCurrent ?: this.historicMonthlyAverageClassic
    return data
}
