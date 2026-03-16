package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.doa.WeatherDao
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.models.WeatherResponse
import com.example.fineweather.utils.getCurrentMonthName
import com.example.fineweather.utils.getCurrentMonthString
import retrofit2.HttpException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class WeatherRepository(
    private val openMeteoWeatherApi: OpenMeteoWeatherApiService,
    private val openMeteoArchiveApi: OpenMeteoArchiveApiService,
    private val weatherDao: WeatherDao,
) {
    private companion object {
        const val DAILY_TEMPERATURE = "temperature_2m_mean"
        const val FALLBACK_TEMPERATURE = -273.15
    }

    private var cachedForecast: WeatherResponse? = null
    private var cachedCurrentData: WeatherResponse? = null
    private var cachedHistoricData: WeatherResponse? = null

    suspend fun getWeatherCurrent(
        latitude: Double,
        longitude: Double,
        timeSpan: Int,
    ): WeatherResponse {
        try {
            val (endDate, startDate) = getDateRange(timeSpan)
            val weatherData =
                openMeteoArchiveApi.getHistoricData(
                    latitude = latitude,
                    longitude = longitude,
                    daily = DAILY_TEMPERATURE,
                    startDate = startDate,
                    endDate = endDate,
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
        forecastDays: Int,
    ): WeatherResponse {
        try {
            val weatherData =
                openMeteoWeatherApi.getForecast(
                    latitude = latitude,
                    longitude = longitude,
                    daily = DAILY_TEMPERATURE,
                    forecastDays = forecastDays,
                    pastDays = 0,
                    timezone = "auto",
                )
            cachedForecast = weatherData
            return weatherData
        } catch (e: Exception) {
            throw Exception("Failed to fetch weather data", e)
        }
    }

    fun calculateAverageForecastTemperature(): Double {
        return averageTemperature(cachedForecast?.daily?.temperature_2m_mean, "forecast")
    }

    fun calculateAverageCurrentTemperature(): Double {
        return averageTemperature(cachedCurrentData?.daily?.temperature_2m_mean, "current")
    }

    fun calculateAverageCurrentMonthlyTemperature(): Pair<String, Double> {
        requireNotNull(cachedCurrentData) { "No current data available" }
        val timeList = cachedCurrentData?.daily?.time
        val tempList = cachedCurrentData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No weather data available"
        }
        val dailyData = timeList.zip(tempList)
        val currentMonth = getCurrentMonthString()
        val dailyDataCurrentMonth =
            dailyData.filter { pair -> pair.first.contains(("-$currentMonth-")) }

        return Pair(currentMonth, dailyDataCurrentMonth.map { it.second }.average())
    }

    suspend fun getWeatherHistory(
        latitude: Double,
        longitude: Double,
        timeSpan: Int,
    ): WeatherResponse {
        val startYear: Int = Calendar.getInstance().get(Calendar.YEAR) - (timeSpan + 1)
        val month: String = getCurrentMonthString()
        val endYear: Int = Calendar.getInstance().get(Calendar.YEAR) - 1
        val startDate = "$startYear-$month-01"
        val endDate = "$endYear-$month-31"
//        val startDate = "1970-01-01"
//        val endDate = "1999-12-31"

        try {
            val historicData =
                openMeteoArchiveApi.getHistoricData(latitude, longitude, startDate, endDate)
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
    fun calculateAverageHistoricMonthlyTemperature(): Pair<String, Double>? {
        val timeList = cachedHistoricData?.daily?.time
        val tempList = cachedHistoricData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No historic weather data available"
        }

        // Combine the data safely
        val dailyData = timeList.zip(tempList)

        return calculateMonthlyAverage(dailyData)
    }

    fun calculateMonthlyAverage(dailyData: List<Pair<String, Double>>): Pair<String, Double>? {
        val month = getCurrentMonthString()
        val groupedByMonth =
            dailyData.groupBy { (dateStr, _) ->
                dateStr.substring(0, 7)
            }

        // Calculate average per month
        val averagesByMonth: Map<String, Double> =
            groupedByMonth.mapValues { (_, values) ->
                val temps = values.map { it.second }
                temps.average()
            }
        val monthlyData =
            averagesByMonth.entries.filter { it.key.contains("-$month", ignoreCase = true) }
        if (monthlyData.isEmpty()) {
            return null
        }
        val monthlyAverage = monthlyData.map { it.value }.average()
        return Pair(getCurrentMonthName(), monthlyAverage)
    }

    fun getDateRange(daysAgo: Int): Pair<String, String> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val currentDate = LocalDate.now()
        val pastDate = currentDate.minusDays(daysAgo.toLong())

        return Pair(currentDate.format(formatter), pastDate.format(formatter))
    }

    suspend fun cleanOutdatedWeatherDate() {
        val minDate = LocalDate.now().minusDays(7).toString()
        weatherDao.cleanOutdatedCache(minDate)
    }

    suspend fun getCachedWeatherById(
        locId: String,
    ) = weatherDao.getWeatherById(locId)

    suspend fun insertWeather(entity: WeatherEntity) {
        weatherDao.insertWeather(entity)
    }

    private fun averageTemperature(
        temperatures: List<Double>?,
        label: String,
    ): Double {
        if (temperatures.isNullOrEmpty()) {
            Log.e("WeatherRepository", "No $label data available")
            return FALLBACK_TEMPERATURE
        }
        return temperatures.average()
    }
}
