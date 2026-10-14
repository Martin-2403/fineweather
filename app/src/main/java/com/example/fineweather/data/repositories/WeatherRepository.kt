package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.dao.ForecastSeriesDao
import com.example.fineweather.data.local.dao.ReferenceClimateDao
import com.example.fineweather.data.local.dao.WeatherDao
import com.example.fineweather.data.local.dao.YearSeriesDao
import com.example.fineweather.data.local.entities.ForecastSeriesEntity
import com.example.fineweather.data.local.entities.ReferenceClimateEntity
import com.example.fineweather.data.local.entities.YearSeriesEntity
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ReferenceClimate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.LocalDate

/** A loaded value and whether it came from the local cache instead of the network. */
data class CacheResult<T>(
    val value: T,
    val fromCache: Boolean,
)

class RateLimitException(
    cause: Throwable? = null,
) : Exception("The weather service's request limit was reached", cause)

class WeatherRepository(
    private val openMeteoWeatherApi: OpenMeteoWeatherApiService,
    private val openMeteoArchiveApi: OpenMeteoArchiveApiService,
    private val weatherDao: WeatherDao,
    private val referenceClimateDao: ReferenceClimateDao,
    private val yearSeriesDao: YearSeriesDao,
    private val forecastSeriesDao: ForecastSeriesDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val DAILY_TEMPERATURE = "temperature_2m_mean"
        private const val FORECAST_DAYS = 14
        private const val YEAR_SERIES_LOOKBACK_DAYS = 30L
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Number of non-favorite locations whose 30-year reference stays cached. */
        const val REFERENCE_CACHE_KEEP = 20

        fun referenceYears(
            reference: HistoricReference,
            today: LocalDate,
        ): Pair<Int, Int> =
            when (reference) {
                HistoricReference.CLASSIC -> 1961 to 1990
                HistoricReference.CURRENT -> {
                    val end = HistoricReference.latestCompleteDecadeEndYear(today)
                    (end - 29) to end
                }
            }
    }

    // Serializes cache-or-fetch so identical concurrent requests hit the API once.
    private val fetchLock = Mutex()

    /**
     * The 14-day daily forecast starting today in the place's time zone. Always 14 days, so a
     * 7-day view is just its first week and switching needs no request. Cached for the day.
     */
    suspend fun getForecast(
        locationId: String,
        latitude: Double,
        longitude: Double,
        today: LocalDate,
    ): CacheResult<DailySeries> =
        fetchLock.withLock {
            val cached = forecastSeriesDao.get(locationId)
            if (cached != null && cached.fetchedDate == today.toString()) {
                return@withLock CacheResult(
                    DailySeries.fromBytes(LocalDate.parse(cached.startDate), cached.data),
                    fromCache = true,
                )
            }
            val series = fetchForecast(latitude, longitude)
            forecastSeriesDao.insert(
                ForecastSeriesEntity(
                    locationId = locationId,
                    startDate = series.start.toString(),
                    endDate = series.end.toString(),
                    data = series.toBytes(),
                    fetchedDate = today.toString(),
                ),
            )
            CacheResult(series, fromCache = false)
        }

    private suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
    ): DailySeries {
        try {
            val response =
                openMeteoWeatherApi.getForecast(
                    latitude = latitude,
                    longitude = longitude,
                    daily = DAILY_TEMPERATURE,
                    forecastDays = FORECAST_DAYS,
                    pastDays = 0,
                    timezone = "auto",
                )
            val first = response.daily.time.firstOrNull()?.let { LocalDate.parse(it) }
                ?: throw IllegalStateException("The forecast contained no days")
            return DailySeries.fromApi(
                first,
                first.plusDays(FORECAST_DAYS - 1L),
                response.daily.time,
                response.daily.temperature_2m_mean,
            )
        } catch (e: Exception) {
            Log.e("WeatherAPI", "Forecast error: ${e.message}")
            throw if (e.isRateLimit()) RateLimitException(e) else Exception("Failed to fetch weather data", e)
        }
    }

    /**
     * Daily means from January 1 (or 30 days before yesterday, whichever is earlier) to yesterday,
     * the last complete day. Cached per location for the day it was fetched.
     */
    suspend fun getYearToDate(
        locationId: String,
        latitude: Double,
        longitude: Double,
        today: LocalDate,
    ): CacheResult<DailySeries> =
        fetchLock.withLock {
            val yesterday = today.minusDays(1)
            val start =
                minOf(LocalDate.of(yesterday.year, 1, 1), yesterday.minusDays(YEAR_SERIES_LOOKBACK_DAYS))
            val cached = yearSeriesDao.get(locationId)
            if (cached != null &&
                cached.fetchedDate == today.toString() &&
                cached.startDate == start.toString() &&
                cached.endDate == yesterday.toString()
            ) {
                return@withLock CacheResult(DailySeries.fromBytes(start, cached.data), fromCache = true)
            }
            val series = fetchSeries(latitude, longitude, start, yesterday)
            yearSeriesDao.insert(
                YearSeriesEntity(
                    locationId = locationId,
                    startDate = start.toString(),
                    endDate = yesterday.toString(),
                    data = series.toBytes(),
                    fetchedDate = today.toString(),
                ),
            )
            CacheResult(series, fromCache = false)
        }

    /**
     * Daily means of the whole reference period. Cached until the period itself changes, with the
     * least recently used non-favorite locations evicted.
     */
    suspend fun getReferenceClimate(
        locationId: String,
        latitude: Double,
        longitude: Double,
        reference: HistoricReference,
        today: LocalDate,
    ): CacheResult<ReferenceClimate> =
        fetchLock.withLock {
            val (firstYear, lastYear) = referenceYears(reference, today)
            val start = LocalDate.of(firstYear, 1, 1)
            val now = clock()
            val cached = referenceClimateDao.get(locationId, reference.id)
            if (cached != null && cached.firstYear == firstYear && cached.lastYear == lastYear) {
                referenceClimateDao.touch(locationId, reference.id, now)
                return@withLock CacheResult(
                    ReferenceClimate(DailySeries.fromBytes(start, cached.data), firstYear, lastYear),
                    fromCache = true,
                )
            }
            val series = fetchSeries(latitude, longitude, start, LocalDate.of(lastYear, 12, 31))
            referenceClimateDao.insert(
                ReferenceClimateEntity(
                    locationId = locationId,
                    referenceId = reference.id,
                    firstYear = firstYear,
                    lastYear = lastYear,
                    data = series.toBytes(),
                    lastUsed = now,
                ),
            )
            referenceClimateDao.evictLeastRecentlyUsed(REFERENCE_CACHE_KEEP)
            CacheResult(ReferenceClimate(series, firstYear, lastYear), fromCache = false)
        }

    private suspend fun fetchSeries(
        latitude: Double,
        longitude: Double,
        start: LocalDate,
        end: LocalDate,
    ): DailySeries {
        try {
            val response =
                openMeteoArchiveApi.getHistoricData(
                    latitude = latitude,
                    longitude = longitude,
                    daily = DAILY_TEMPERATURE,
                    startDate = start.toString(),
                    endDate = end.toString(),
                )
            return DailySeries.fromApi(start, end, response.daily.time, response.daily.temperature_2m_mean)
        } catch (e: HttpException) {
            Log.e("WeatherAPI", "HTTP error: ${e.code()} - ${e.message()}")
            throw if (e.isRateLimit()) RateLimitException(e) else Exception("Failed to fetch weather data: HTTP ${e.code()}", e)
        } catch (e: Exception) {
            Log.e("WeatherAPI", "Network error: ${e.message}")
            throw Exception("Failed to fetch weather data: ${e.message}", e)
        }
    }

    private fun Throwable.isRateLimit(): Boolean = this is HttpException && code() == HTTP_TOO_MANY_REQUESTS

    suspend fun cleanOutdatedWeatherDate() {
        val minDate = LocalDate.now().minusDays(7).toString()
        weatherDao.cleanOutdatedCache(minDate)
        yearSeriesDao.cleanOutdated(minDate)
        forecastSeriesDao.cleanOutdated(LocalDate.now().toString())
    }
}
