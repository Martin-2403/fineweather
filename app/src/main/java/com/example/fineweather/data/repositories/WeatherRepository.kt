package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.data.models.DailyForecast
import com.example.fineweather.data.models.ForecastData

class WeatherRepository(private val openMeteoApi: OpenMeteoApiService) {
    private var cachedForecast: ForecastData? = null

    suspend fun getWeatherForecast(latitude: Double, longitude: Double): ForecastData {
        try {
            val forecast = openMeteoApi.getForecast(latitude, longitude)
            cachedForecast = forecast
            return forecast
        } catch (e: Exception) {
            throw Exception("Failed to fetch weather data", e)
        }
    }

    fun calculateForecastAverageTemperature(): Double {
        requireNotNull(cachedForecast) { "No forecast data available" }
        return if (cachedForecast?.daily?.temperature_2m_max != null && cachedForecast?.daily?.temperature_2m_min != null) {

            String.format(
                "%.1f",
                (cachedForecast!!.daily.temperature_2m_max.sum() + cachedForecast!!.daily.temperature_2m_min.sum())/ cachedForecast!!.daily.temperature_2m_max.size / 2
            ).toDouble()

        } else -273.15
    }
}
