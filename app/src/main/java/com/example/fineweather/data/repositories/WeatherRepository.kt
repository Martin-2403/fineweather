package com.example.fineweather.data.repositories

import WeatherResponse
import com.example.fineweather.api.OpenMeteoApiService
import java.util.Calendar

class WeatherRepository(private val openMeteoApi: OpenMeteoApiService) {
    private var cachedForecast: WeatherResponse? = null
    private var cachedHistoricData: WeatherResponse? = null

    suspend fun getWeatherForecast(latitude: Double, longitude: Double): WeatherResponse {
        try {
            val forecast = openMeteoApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                daily = "temperature_2m_mean",
                forecastDays = 7,
                pastDays = 7,
                timezone = "auto"
            )
            cachedForecast = forecast
            return forecast
        } catch (e: Exception) {

            throw Exception("Failed to fetch weather data", e)
        }
    }

    fun calculateForecastAverageTemperature(): Double {
        requireNotNull(cachedForecast) { "No forecast data available" }
        return (if (cachedForecast?.daily?.temperature_2m_mean != null) {
            String.format(
                "%.1f",
                (cachedForecast!!.daily.temperature_2m_mean.sum() / cachedForecast!!.daily.temperature_2m_mean.size)
            ).toDouble()

        } else -273.15) as Double
    }

    suspend fun getWeatherHistory(
        latitude: Double,
        longitude: Double,
        timeSpan: Int
    ): WeatherResponse {
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

    fun calculateMonthlyAverageTemperature(): Map<String, Double> {
        val timeList = cachedHistoricData?.daily?.time
        val tempList = cachedHistoricData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No historic weather data available"
        }

        // Combine the data safely
        val dailyData = timeList.zip(tempList)

        // Group by "YYYY-MM"
        val groupedByMonth = dailyData.groupBy { (dateStr, _) ->
            dateStr.substring(0, 7)
        }

        // Calculate average per month
        return groupedByMonth.mapValues { (_, values) ->
            val temps = values.map { it.second }
            temps.average()
        }
    }


}
