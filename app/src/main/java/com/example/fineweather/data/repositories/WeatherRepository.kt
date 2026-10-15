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
import com.example.fineweather.data.models.Daily
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        // Forecast and current year are small, so they carry every metric; the 30-year reference only
        // temperature, and the extras of the reference are fetched lazily by getReferenceExtras.
        private val ALL_METRICS = Metric.entries
        private val REFERENCE_METRICS = listOf(Metric.TEMPERATURE)
        private val EXTRA_METRICS = listOf(Metric.SUNSHINE, Metric.PRECIPITATION)
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

    // Serializes cache-or-fetch per key, so identical concurrent requests hit the API once while
    // different ones run in parallel.
    private val fetchLocks = KeyedMutex()

    /**
     * The 14-day daily forecast starting today in the place's time zone, with every metric. Always
     * 14 days, so a 7-day view is just its first week and switching needs no request. Cached for the
     * day; a row from before sunshine and precipitation were stored counts as a miss.
     */
    suspend fun getForecast(
        locationId: String,
        latitude: Double,
        longitude: Double,
        today: LocalDate,
    ): CacheResult<MetricSeries> =
        fetchLocks.withLock("forecast|$locationId") {
            val cached = forecastSeriesDao.get(locationId)
            if (cached != null && cached.fetchedDate == today.toString()) {
                val startDate = LocalDate.parse(cached.startDate)
                val series =
                    metricSeriesOrNull(
                        startDate,
                        daysBetween(startDate, LocalDate.parse(cached.endDate)),
                        cached.data,
                        cached.sunshineData,
                        cached.precipitationData,
                    )
                if (series != null) return@withLock CacheResult(series, fromCache = true)
            }
            val series = fetchForecast(latitude, longitude)
            forecastSeriesDao.insert(
                ForecastSeriesEntity(
                    locationId = locationId,
                    startDate = series.start.toString(),
                    endDate = series.end.toString(),
                    data = series.blob(Metric.TEMPERATURE),
                    fetchedDate = today.toString(),
                    sunshineData = series.blob(Metric.SUNSHINE),
                    precipitationData = series.blob(Metric.PRECIPITATION),
                ),
            )
            CacheResult(series, fromCache = false)
        }

    private suspend fun fetchForecast(
        latitude: Double,
        longitude: Double,
    ): MetricSeries {
        try {
            val response =
                openMeteoWeatherApi.getForecast(
                    latitude = latitude,
                    longitude = longitude,
                    daily = dailyParameter(ALL_METRICS),
                    forecastDays = FORECAST_DAYS,
                    pastDays = 0,
                    timezone = "auto",
                )
            val first = response.daily.time.firstOrNull()?.let { LocalDate.parse(it) }
                ?: throw IllegalStateException("The forecast contained no days")
            response.daily.requireTemperature()
            return MetricSeries.fromApi(first, first.plusDays(FORECAST_DAYS - 1L), response.daily, ALL_METRICS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WeatherAPI", "Forecast error: ${e.message}")
            throw if (e.isRateLimit()) RateLimitException(e) else Exception("Failed to fetch weather data", e)
        }
    }

    /**
     * Daily values of every metric from January 1 (or 30 days before yesterday, whichever is
     * earlier) to yesterday, the last complete day. Cached per location for the day it was fetched;
     * a row from before sunshine and precipitation were stored counts as a miss.
     */
    suspend fun getYearToDate(
        locationId: String,
        latitude: Double,
        longitude: Double,
        today: LocalDate,
    ): CacheResult<MetricSeries> =
        fetchLocks.withLock("year|$locationId") {
            val yesterday = today.minusDays(1)
            val start =
                minOf(LocalDate.of(yesterday.year, 1, 1), yesterday.minusDays(YEAR_SERIES_LOOKBACK_DAYS))
            val cached = yearSeriesDao.get(locationId)
            if (cached != null &&
                cached.fetchedDate == today.toString() &&
                cached.startDate == start.toString() &&
                cached.endDate == yesterday.toString()
            ) {
                val series =
                    metricSeriesOrNull(
                        start,
                        daysBetween(start, yesterday),
                        cached.data,
                        cached.sunshineData,
                        cached.precipitationData,
                    )
                if (series != null) return@withLock CacheResult(series, fromCache = true)
            }
            val series =
                fetchArchive(latitude, longitude, start, yesterday, ALL_METRICS) { daily ->
                    daily.requireTemperature()
                    MetricSeries.fromApi(start, yesterday, daily, ALL_METRICS)
                }
            yearSeriesDao.insert(
                YearSeriesEntity(
                    locationId = locationId,
                    startDate = start.toString(),
                    endDate = yesterday.toString(),
                    data = series.blob(Metric.TEMPERATURE),
                    fetchedDate = today.toString(),
                    sunshineData = series.blob(Metric.SUNSHINE),
                    precipitationData = series.blob(Metric.PRECIPITATION),
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
        fetchLocks.withLock(referenceLockKey(locationId, reference)) {
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
            val end = LocalDate.of(lastYear, 12, 31)
            val series =
                fetchArchive(latitude, longitude, start, end, REFERENCE_METRICS) { daily ->
                    DailySeries.fromApi(start, end, daily.time, daily.requireTemperature())
                }
            // REPLACE drops the extras of an older period, which is wanted: its years changed.
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

    /**
     * Sunshine and precipitation over the same years as [getReferenceClimate], from one request.
     * They are stored on the reference row, so eviction, last use and favorites work as for the
     * temperature. The row is therefore made sure first: a missing or outdated temperature reference
     * is fetched (and may fail, e.g. with [RateLimitException], before any extras are requested), so
     * the extras are always persisted and never fetched twice. Absent means no data: a metric the
     * service has no value for is left out of the result, also when it is served from the cache.
     */
    suspend fun getReferenceExtras(
        locationId: String,
        latitude: Double,
        longitude: Double,
        reference: HistoricReference,
        today: LocalDate,
    ): CacheResult<Map<Metric, ReferenceClimate>> {
        // Takes the reference lock itself, so it must run before and outside the lock below.
        getReferenceClimate(locationId, latitude, longitude, reference, today)
        return fetchLocks.withLock(referenceLockKey(locationId, reference)) {
            val (firstYear, lastYear) = referenceYears(reference, today)
            val start = LocalDate.of(firstYear, 1, 1)
            val end = LocalDate.of(lastYear, 12, 31)
            val days = daysBetween(start, end)
            val now = clock()
            val cached = referenceClimateDao.get(locationId, reference.id)
            val cachedSunshine = cached?.sunshineData
            val cachedPrecipitation = cached?.precipitationData
            if (cached != null &&
                cached.firstYear == firstYear &&
                cached.lastYear == lastYear &&
                cachedSunshine?.holdsDays(days) == true &&
                cachedPrecipitation?.holdsDays(days) == true
            ) {
                referenceClimateDao.touch(locationId, reference.id, now)
                val series =
                    mapOf(
                        Metric.SUNSHINE to DailySeries.fromBytes(start, cachedSunshine),
                        Metric.PRECIPITATION to DailySeries.fromBytes(start, cachedPrecipitation),
                    )
                return@withLock CacheResult(extrasOf(series, firstYear, lastYear), fromCache = true)
            }
            val fetched =
                fetchArchive(latitude, longitude, start, end, EXTRA_METRICS) { daily ->
                    MetricSeries.fromApi(start, end, daily, EXTRA_METRICS)
                }
            // Only 0 if the row was evicted or replaced by another period since it was ensured.
            val updated =
                referenceClimateDao.updateExtras(
                    locationId,
                    reference.id,
                    firstYear,
                    lastYear,
                    fetched.blob(Metric.SUNSHINE),
                    fetched.blob(Metric.PRECIPITATION),
                )
            if (updated > 0) referenceClimateDao.touch(locationId, reference.id, now)
            CacheResult(extrasOf(fetched.toMap(), firstYear, lastYear), fromCache = false)
        }
    }

    /** Both reference requests of a place and period share this key, so they never interleave. */
    private fun referenceLockKey(
        locationId: String,
        reference: HistoricReference,
    ) = "ref|$locationId|${reference.id}"

    /** Only the metrics with at least one value; an all-missing one is unavailable. */
    private fun extrasOf(
        series: Map<Metric, DailySeries>,
        firstYear: Int,
        lastYear: Int,
    ): Map<Metric, ReferenceClimate> =
        series.filterValues { it.hasValues() }.mapValues { ReferenceClimate(it.value, firstYear, lastYear) }

    private fun dailyParameter(metrics: Collection<Metric>) = metrics.joinToString(",") { it.apiName }

    /**
     * The series of a cached row, or null if its sunshine or precipitation was never stored or any
     * blob does not hold exactly [days] days (a corrupt row is just a cache miss).
     */
    private fun metricSeriesOrNull(
        start: LocalDate,
        days: Long,
        temperature: ByteArray,
        sunshine: ByteArray?,
        precipitation: ByteArray?,
    ): MetricSeries? {
        if (sunshine == null || precipitation == null) return null
        if (!temperature.holdsDays(days) || !sunshine.holdsDays(days) || !precipitation.holdsDays(days)) return null
        return MetricSeries(
            mapOf(
                Metric.TEMPERATURE to DailySeries.fromBytes(start, temperature),
                Metric.SUNSHINE to DailySeries.fromBytes(start, sunshine),
                Metric.PRECIPITATION to DailySeries.fromBytes(start, precipitation),
            ),
        )
    }

    private fun daysBetween(
        start: LocalDate,
        end: LocalDate,
    ): Long = ChronoUnit.DAYS.between(start, end) + 1

    private fun ByteArray.holdsDays(days: Long): Boolean = size.toLong() == days * Float.SIZE_BYTES

    private fun MetricSeries.blob(metric: Metric): ByteArray =
        checkNotNull(toBytes(metric)) { "The series has no ${metric.apiName}" }

    private fun Daily.requireTemperature(): List<Double?> =
        checkNotNull(temperature_2m_mean) { "The response contained no ${Metric.TEMPERATURE.apiName}" }

    private suspend fun <T> fetchArchive(
        latitude: Double,
        longitude: Double,
        start: LocalDate,
        end: LocalDate,
        metrics: Collection<Metric>,
        parse: (Daily) -> T,
    ): T {
        try {
            val response =
                openMeteoArchiveApi.getHistoricData(
                    latitude = latitude,
                    longitude = longitude,
                    daily = dailyParameter(metrics),
                    startDate = start.toString(),
                    endDate = end.toString(),
                )
            return parse(response.daily)
        } catch (e: CancellationException) {
            throw e
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
