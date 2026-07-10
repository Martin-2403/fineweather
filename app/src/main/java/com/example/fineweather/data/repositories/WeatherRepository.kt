package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.doa.WeatherDao
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.WeatherResponse
import com.example.fineweather.utils.getCurrentMonthName
import com.example.fineweather.utils.getCurrentMonthString
import retrofit2.HttpException
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.sqrt

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

    data class CurrentMonthStats(
        val month: String,
        val average: Double?,
        val dayCount: Int,
    )

    fun calculateCurrentMonthStats(): CurrentMonthStats {
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

        if (dailyDataCurrentMonth.isEmpty()) {
            return CurrentMonthStats(currentMonth, null, 0)
        }

        return CurrentMonthStats(
            currentMonth,
            dailyDataCurrentMonth.map { it.second }.average(),
            dailyDataCurrentMonth.size,
        )
    }

    suspend fun getWeatherHistory(
        latitude: Double,
        longitude: Double,
        reference: HistoricReference,
    ): WeatherResponse {
        try {
            val range = buildHistoricDateRange(reference)
            val historicData =
                openMeteoArchiveApi.getHistoricData(
                    latitude = latitude,
                    longitude = longitude,
                    startDate = range.start,
                    endDate = range.end,
                )
            val sanitized = filterDailyToRange(historicData, range)
            cachedHistoricData = sanitized
            return sanitized
        } catch (e: HttpException) {
            Log.e("WeatherAPI", "HTTP error: ${e.code()} - ${e.message()}")
            throw Exception("Failed to fetch weather data: HTTP ${e.code()}", e)
        } catch (e: Exception) {
            Log.e("WeatherAPI", "Network error: ${e.message}")
            throw Exception("Failed to fetch weather data: ${e.message}", e)
        }
    }

    fun calculateAverageHistoricMonthlyTemperature(): Pair<String, Double>? {
        return calculateHistoricMonthlyStats()?.let { Pair(it.month, it.average) }
    }

    data class HistoricMonthlyStats(
        val month: String,
        val average: Double,
        val stdDev: Double,
    )

    fun calculateHistoricMonthlyStats(): HistoricMonthlyStats? {
        val timeList = cachedHistoricData?.daily?.time
        val tempList = cachedHistoricData?.daily?.temperature_2m_mean

        require(!timeList.isNullOrEmpty() && !tempList.isNullOrEmpty()) {
            "No historic weather data available"
        }

        val dailyData = timeList.zip(tempList)
        return calculateMonthlyStats(dailyData)
    }

    fun calculateMonthlyAverage(dailyData: List<Pair<String, Double>>): Pair<String, Double>? {
        return calculateMonthlyStats(dailyData)?.let { Pair(it.month, it.average) }
    }

    fun calculateMonthlyStats(dailyData: List<Pair<String, Double>>): HistoricMonthlyStats? {
        val month = getCurrentMonthString()
        val groupedByMonth =
            dailyData.groupBy { (dateStr, _) ->
                dateStr.substring(0, 7)
            }

        val averagesByMonth =
            groupedByMonth.mapValues { (_, values) ->
                val temps = values.map { it.second }
                temps.average()
            }
        val monthlyMeans =
            averagesByMonth.entries
                .filter { it.key.contains("-$month", ignoreCase = true) }
                .map { it.value }
        if (monthlyMeans.isEmpty()) {
            return null
        }
        val monthlyAverage = monthlyMeans.average()
        val stdDev =
            if (monthlyMeans.size < 2) {
                0.0
            } else {
                val variance =
                    monthlyMeans
                        .map { value -> (value - monthlyAverage) * (value - monthlyAverage) }
                        .sum() / (monthlyMeans.size - 1)
                sqrt(variance)
            }
        return HistoricMonthlyStats(
            month = getCurrentMonthName(),
            average = monthlyAverage,
            stdDev = stdDev,
        )
    }

    fun getDateRange(daysAgo: Int): Pair<String, String> {
        val currentDate = LocalDate.now()
        val pastDate = currentDate.minusDays(daysAgo.toLong())

        return Pair(currentDate.toString(), pastDate.toString())
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

    data class DateRange(
        val start: String,
        val end: String,
    )

    internal fun buildHistoricDateRange(
        timeSpan: Int,
        now: LocalDate = LocalDate.now(),
    ): DateRange {
        require(timeSpan > 0) { "timeSpan must be positive" }
        val startYear = now.year - timeSpan
        val endYear = now.year - 1
        val month = now.monthValue
        val start = LocalDate.of(startYear, month, 1)
        val end = LocalDate.of(endYear, month, YearMonth.of(endYear, month).lengthOfMonth())
        return DateRange(start = start.toString(), end = end.toString())
    }

    internal fun buildHistoricDateRange(
        reference: HistoricReference,
        now: LocalDate = LocalDate.now(),
    ): DateRange {
        val month = now.monthValue
        val (startYear, endYear) =
            when (reference) {
                HistoricReference.CLASSIC -> 1961 to 1990
                HistoricReference.CURRENT -> {
                    val end = HistoricReference.latestCompleteDecadeEndYear(now)
                    val start = end - 29
                    start to end
                }
            }
        val start = LocalDate.of(startYear, month, 1)
        val end = LocalDate.of(endYear, month, YearMonth.of(endYear, month).lengthOfMonth())
        return DateRange(start = start.toString(), end = end.toString())
    }

    private fun filterDailyToRange(
        response: WeatherResponse,
        range: DateRange,
    ): WeatherResponse {
        val startDate = LocalDate.parse(range.start)
        val endDate = LocalDate.parse(range.end)
        val filteredPairs =
            response.daily.time.zip(response.daily.temperature_2m_mean).filter { (dateStr, _) ->
                val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@filter false
                !date.isBefore(startDate) && !date.isAfter(endDate)
            }
        if (filteredPairs.isEmpty()) {
            return response
        }
        val (times, temps) = filteredPairs.unzip()
        return response.copy(
            daily =
                response.daily.copy(
                    time = times,
                    temperature_2m_mean = temps,
                ),
        )
    }
}
