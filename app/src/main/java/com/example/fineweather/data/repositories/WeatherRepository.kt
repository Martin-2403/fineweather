package com.example.fineweather.data.repositories

import com.example.fineweather.data.models.WeatherResponse
import android.util.Log
import com.example.fineweather.api.OpenMeteoApiService
import retrofit2.HttpException
import java.util.Calendar
import java.util.Locale

class WeatherRepository(private val openMeteoApi: OpenMeteoApiService) {
    private var cachedForecast: WeatherResponse? = null
    private var cachedCurrentData: WeatherResponse? = null
    private var cachedHistoricData: WeatherResponse? = null

    suspend fun getWeatherCurrent(
        latitude: Double,
        longitude: Double,
        pastDays: Int
    ): WeatherResponse {
        try {
            val weatherData = openMeteoApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                daily = "temperature_2m_mean",
                forecastDays = 0,
                pastDays = pastDays,
                timezone = "auto"
            )
            cachedCurrentData = weatherData
            return weatherData
        } catch (e: Exception) {

            throw Exception("Failed to fetch weather data", e)
        }
    }

    suspend fun getWeatherForecast(
        latitude: Double,
        longitude: Double,
        forecastDays: Int
    ): WeatherResponse {
        try {
            val weatherData = openMeteoApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                daily = "temperature_2m_mean",
                forecastDays = forecastDays,
                pastDays = 0,
                timezone = "auto"
            )
            cachedForecast = weatherData
            return weatherData
        } catch (e: Exception) {

            throw Exception("Failed to fetch weather data", e)
        }
    }

    fun calculateAverageForecastTemperature(): Double {
        requireNotNull(cachedForecast) { "No forecast data available" }
        return (if (cachedForecast?.daily?.temperature_2m_mean != null) {
            String.format(
                "%.2f",
                (cachedForecast!!.daily.temperature_2m_mean.sum() / cachedForecast!!.daily.temperature_2m_mean.size)
            ).toDouble()

        } else -273.15)
    }

    fun calculateAverageCurrentTemperature(): Double {
        requireNotNull(cachedCurrentData) { "No current data available" }
        return (if (cachedCurrentData?.daily?.temperature_2m_mean != null) {
            String.format(
                "%.2f",
                (cachedCurrentData!!.daily.temperature_2m_mean.sum() / cachedCurrentData!!.daily.temperature_2m_mean.size)
            ).toDouble()

        } else -273.15)
    }

    fun calculateAverageCurrentMonthlyTemperature(): Pair<String, String>? {
        requireNotNull(cachedCurrentData) { "No current data available" }
        val timeList = cachedCurrentData?.daily?.time
        val tempList = cachedCurrentData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No historic weather data available"
        }
        val dailyData = timeList.zip(tempList)
        val dailyDataCurrentMonth = dailyData.filter { pair ->  pair.first.contains(("-${getCurrentMonthString()}-" ))}
        return calculateMonthlyAverage(dailyDataCurrentMonth)
    }

    suspend fun getWeatherHistory(
        latitude: Double,
        longitude: Double,
        timeSpan: Int
    ): WeatherResponse {
        val startYear: Int = Calendar.getInstance().get(Calendar.YEAR) - (timeSpan + 1);
        val month: String = getCurrentMonthString()
        val endYear: Int = Calendar.getInstance().get(Calendar.YEAR) - 1
        val startDate = "$startYear-$month-01"
        val endDate = "$endYear-$month-31"

        try {
            val historicData = openMeteoApi.getHistoricData(latitude, longitude, startDate, endDate)
            cachedHistoricData = historicData
            return historicData
        } catch (e: HttpException) {
            Log.e("WeatherAPI", "HTTP error: ${e.code()} - ${e.message()}")
            throw Exception("Failed to fetch weather data: HTTP ${e.code()}", e)
        } catch (e: Exception) {
            Log.e("WeatherAPI", "Network error: ${e.message}")
            throw Exception("Failed to fetch weather data: ${e.message}", e)
        }
    }

    private fun getCurrentMonthString(): String {
        val month: Int = Calendar.getInstance().get(Calendar.MONTH)
        return if (month >= 10) "$month" else "0${month}"
    }

    private fun getCurrentMonthName(): String {
        val calendar = Calendar.getInstance()
        return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            ?: "Unknown"
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

    fun calculateAverageHistoricMonthlyTemperature(): Pair<String, String>? {
        val timeList = cachedHistoricData?.daily?.time
        val tempList = cachedHistoricData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No historic weather data available"
        }

        // Combine the data safely
        val dailyData = timeList.zip(tempList)

        return calculateMonthlyAverage(dailyData)
    }

    fun calculateMonthlyAverage(dailyData: List<Pair<String, Double>>): Pair<String, String>?{
        val month = getCurrentMonthString()
        val groupedByMonth = dailyData.groupBy { (dateStr, _) ->
            dateStr.substring(0, 7)
        }

        // Calculate average per month
        val avaragesByMonth: Map<String, Double> = groupedByMonth.mapValues { (_, values) ->
            val temps = values.map { it.second }
            temps.average()
        }
        val monthlyData =
            avaragesByMonth.entries.filter { it.key.contains("-${month}", ignoreCase = true) }
        if (monthlyData.isEmpty()) {
            return null
        }
        val monthlyAverage = String.format("%.2f", monthlyData.map { it.value }.average())
        return Pair(getCurrentMonthName(), monthlyAverage)
    }


}
