package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.data.models.ForecastData
import com.example.fineweather.data.models.HistoricData
import java.util.Calendar

class WeatherRepository(private val openMeteoApi: OpenMeteoApiService) {
    private var cachedForecast: ForecastData? = null
    private var cachedHistoricData: HistoricData? = null

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

    suspend fun getWeatherHistory(latitude: Double, longitude: Double, timeSpan: Int): HistoricData {
        val startYear: Int = Calendar.getInstance().get(Calendar.YEAR) - (timeSpan + 1);
        val endYear: Int = Calendar.getInstance().get(Calendar.YEAR) - 1
        val startDate = "$startYear-01-01"
        val endDate = "$endYear-12-31"
        try {
            val historicData = openMeteoApi.getHistoricData(latitude, longitude, startDate, endDate)
            cachedHistoricData = historicData
            return historicData
        } catch (e: Exception) {
            throw Exception("Failed to fetch weather data", e)
        }
    }


}
