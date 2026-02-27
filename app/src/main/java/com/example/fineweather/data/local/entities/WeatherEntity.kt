package com.example.fineweather.data.local.entities

import androidx.room.Entity
import com.example.fineweather.data.models.FineWeatherData

@Entity(
    tableName = "weather_history",
    primaryKeys = ["id", "date"],
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
    val forecastAverage: Double?,
    val historicMonthlyAverage: Double?,
)

// In WeatherEntity.kt
fun WeatherEntity.toUiModel(): FineWeatherData {
    val data = FineWeatherData(this.locationName, Pair(this.latitude, this.longitude), this.id)
    data.currentAverage = this.currentAverage
    data.currentMonthAverage = this.currentMonthAverage
    data.forecastAverage = this.forecastAverage
    data.historicMonthlyAverage = this.historicMonthlyAverage
    return data
}
