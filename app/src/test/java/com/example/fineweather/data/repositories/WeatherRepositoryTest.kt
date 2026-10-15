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
import com.example.fineweather.data.models.DailyUnits
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.WeatherResponse
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.LocalDate

class WeatherRepositoryTest {

    private val weatherApi = mockk<OpenMeteoWeatherApiService>(relaxed = true)
    private val archiveApi = mockk<OpenMeteoArchiveApiService>(relaxed = true)
    private val weatherDao = mockk<WeatherDao>(relaxed = true)
    private val referenceDao = mockk<ReferenceClimateDao>(relaxed = true)
    private val yearSeriesDao = mockk<YearSeriesDao>(relaxed = true)
    private val forecastDao = mockk<ForecastSeriesDao>(relaxed = true)
    private val repository =
        WeatherRepository(
            weatherApi,
            archiveApi,
            weatherDao,
            referenceDao,
            yearSeriesDao,
            forecastDao,
            clock = { NOW_MILLIS },
        )

    private val today = LocalDate.of(2026, 10, 14)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        coEvery { referenceDao.get(any(), any()) } returns null
        coEvery { yearSeriesDao.get(any()) } returns null
        coEvery { forecastDao.get(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ---- forecast ----

    private fun forecastResponse(
        start: LocalDate,
        days: Int = 14,
        sunshineOf: (LocalDate) -> Double? = { 7200.0 },
        rainOf: (LocalDate) -> Double? = { 1.5 },
        valueOf: (LocalDate) -> Double? = { 10.0 },
    ): WeatherResponse {
        val dates = (0 until days).map { start.plusDays(it.toLong()) }
        return sampleResponse(dates.map { it.toString() }, dates.map(valueOf), dates.map(sunshineOf), dates.map(rainOf))
    }

    @Test
    fun getForecast_requestsFourteenDaysAndCachesThem() = runTest {
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 14), valueOf = { it.dayOfMonth.toDouble() }))
        val saved = slot<ForecastSeriesEntity>()
        coEvery { forecastDao.insert(capture(saved)) } returns Unit

        val result = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) { weatherApi.getForecast(1.0, 2.0, any(), 14, 0, any()) }
        assertEquals(LocalDate.of(2026, 10, 14), result.value.start)
        assertEquals(LocalDate.of(2026, 10, 27), result.value.end)
        assertEquals(20.0, result.value.temp().valueOrNull(LocalDate.of(2026, 10, 20))!!, 1e-6)
        assertEquals("loc-1", saved.captured.locationId)
        assertEquals("2026-10-14", saved.captured.startDate)
        assertEquals("2026-10-14", saved.captured.fetchedDate)
    }

    @Test
    fun getForecast_startsAtTheFirstDayTheApiReturns() = runTest {
        // the place's local date can already be tomorrow
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 15)))

        val result = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertEquals(LocalDate.of(2026, 10, 15), result.value.start)
    }

    @Test
    fun getForecast_usesTheCacheFromToday() = runTest {
        val start = LocalDate.of(2026, 10, 14)
        coEvery { forecastDao.get("loc-1") } returns
            ForecastSeriesEntity(
                "loc-1",
                "2026-10-14",
                "2026-10-27",
                blob(start, 14, 6.5),
                "2026-10-14",
                sunshineData = blob(start, 14, 3.0),
                precipitationData = blob(start, 14, 0.5),
            )

        val result = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertTrue(result.fromCache)
        assertEquals(6.5, result.value.temp().valueOrNull(start)!!, 1e-6)
        assertEquals(3.0, result.value[Metric.SUNSHINE]!!.valueOrNull(start)!!, 1e-6)
        assertEquals(0.5, result.value[Metric.PRECIPITATION]!!.valueOrNull(start)!!, 1e-6)
        assertEquals(start, result.value.start)
        coVerify(exactly = 0) { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getForecast_refetchesWhenTheCacheIsFromAnEarlierDay() = runTest {
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 14)))
        coEvery { forecastDao.get("loc-1") } returns
            ForecastSeriesEntity("loc-1", "2026-10-13", "2026-10-26", ByteArray(0), "2026-10-13")

        val result = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getForecast_treatsNullValuesAsMissingDays() = runTest {
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 14), valueOf = { if (it.dayOfMonth == 16) null else 9.0 }))

        val series = repository.getForecast("loc-1", 1.0, 2.0, today).value.temp()

        assertNull(series.valueOrNull(LocalDate.of(2026, 10, 16)))
        assertEquals(9.0, series.valueOrNull(LocalDate.of(2026, 10, 17))!!, 1e-6)
    }

    @Test
    fun getForecast_failsWhenTheResponseHasNoDays() = runTest {
        stubForecast(sampleResponse(emptyList(), emptyList()))

        val thrown = runCatching { repository.getForecast("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        assertTrue(thrown is Exception)
        coVerify(exactly = 0) { forecastDao.insert(any()) }
    }

    @Test
    fun getForecast_wrapsGenericErrors() = runTest {
        stubForecastFailure(RuntimeException("boom"))

        val thrown = runCatching { repository.getForecast("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        assertTrue(thrown is Exception)
        assertFalse(thrown is RateLimitException)
    }

    @Test
    fun getForecast_mapsHttp429ToRateLimit() = runTest {
        stubForecastFailure(httpError(429))

        val thrown = runCatching { repository.getForecast("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
    }

    // ---- year to date ----

    @Test
    fun getYearToDate_fetchesJanuaryFirstToYesterdayAndCaches() = runTest {
        stubArchive(valueOf = { date -> date.dayOfMonth.toDouble() })
        val saved = slot<YearSeriesEntity>()
        coEvery { yearSeriesDao.insert(capture(saved)) } returns Unit

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "2026-01-01", "2026-10-13", any(), any())
        }
        assertEquals(LocalDate.of(2026, 1, 1), result.value.start)
        assertEquals(LocalDate.of(2026, 10, 13), result.value.end)
        assertEquals("loc-1", saved.captured.locationId)
        assertEquals("2026-01-01", saved.captured.startDate)
        assertEquals("2026-10-13", saved.captured.endDate)
        assertEquals("2026-10-14", saved.captured.fetchedDate)
    }

    @Test
    fun getYearToDate_looksBackIntoPreviousYearEarlyInJanuary() = runTest {
        stubArchive(valueOf = { 1.0 })

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, LocalDate.of(2026, 1, 10))

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "2025-12-10", "2026-01-09", any(), any())
        }
        assertEquals(LocalDate.of(2025, 12, 10), result.value.start)
    }

    @Test
    fun getYearToDate_usesCacheFetchedToday() = runTest {
        val start = LocalDate.of(2026, 1, 1)
        coEvery { yearSeriesDao.get("loc-1") } returns
            YearSeriesEntity(
                "loc-1",
                "2026-01-01",
                "2026-10-13",
                blob(start, 286, 7.5),
                "2026-10-14",
                sunshineData = blob(start, 286, 4.0),
                precipitationData = blob(start, 286, 2.0),
            )

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertTrue(result.fromCache)
        assertEquals(7.5, result.value.temp().valueOrNull(LocalDate.of(2026, 10, 13))!!, 1e-6)
        assertEquals(4.0, result.value[Metric.SUNSHINE]!!.valueOrNull(LocalDate.of(2026, 10, 13))!!, 1e-6)
        assertEquals(2.0, result.value[Metric.PRECIPITATION]!!.valueOrNull(start)!!, 1e-6)
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_refetchesWhenCacheIsFromAnEarlierDay() = runTest {
        stubArchive(valueOf = { 1.0 })
        coEvery { yearSeriesDao.get("loc-1") } returns
            YearSeriesEntity("loc-1", "2026-01-01", "2026-10-12", ByteArray(0), "2026-10-13")

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_treatsNullValuesAsMissingDays() = runTest {
        stubArchive(valueOf = { date -> if (date.dayOfMonth == 5) null else 10.0 })

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today).value.temp()

        assertNull(result.valueOrNull(LocalDate.of(2026, 10, 5)))
        assertEquals(10.0, result.valueOrNull(LocalDate.of(2026, 10, 6))!!, 1e-6)
    }

    @Test
    fun getYearToDate_mapsHttp429ToRateLimit() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(429)

        val thrown = runCatching { repository.getYearToDate("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
    }

    @Test
    fun getYearToDate_wrapsOtherHttpAndGenericErrors() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(500)
        val http = runCatching { repository.getYearToDate("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws RuntimeException("boom")
        val generic = runCatching { repository.getYearToDate("loc-1", 1.0, 2.0, today) }.exceptionOrNull()

        assertTrue(http!!.message!!.contains("HTTP 500"))
        assertTrue(generic!!.message!!.contains("boom"))
    }

    // ---- reference climate ----

    @Test
    fun getReferenceClimate_fetchesWholeCalendarYearsOfClassicReference() = runTest {
        stubArchive(valueOf = { date -> date.monthValue.toDouble() })
        val saved = slot<ReferenceClimateEntity>()
        coEvery { referenceDao.insert(capture(saved)) } returns Unit

        val result = repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1961-01-01", "1990-12-31", any(), any())
        }
        assertEquals(10.0, result.value.monthMean(10)!!, 1e-6)
        assertEquals(1961, saved.captured.firstYear)
        assertEquals(1990, saved.captured.lastYear)
        assertEquals("1961_1990", saved.captured.referenceId)
        assertEquals(NOW_MILLIS, saved.captured.lastUsed)
        coVerify(exactly = 1) { referenceDao.evictLeastRecentlyUsed(WeatherRepository.REFERENCE_CACHE_KEEP) }
    }

    @Test
    fun getReferenceClimate_currentReferenceEndsAtLastCompleteDecade() = runTest {
        stubArchive(valueOf = { 1.0 })

        repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1991-01-01", "2020-12-31", any(), any())
        }
    }

    @Test
    fun getReferenceClimate_usesCacheAndMarksItUsed() = runTest {
        val series = DailySeries.fromApi(
            LocalDate.of(1961, 1, 1),
            LocalDate.of(1990, 12, 31),
            listOf("1961-01-01"),
            listOf(3.0),
        )
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns
            ReferenceClimateEntity("loc-1", "1961_1990", 1961, 1990, series.toBytes(), 1L)

        val result = repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertTrue(result.fromCache)
        assertEquals(3.0, result.value.series.valueOrNull(LocalDate.of(1961, 1, 1))!!, 1e-6)
        coVerify(exactly = 1) { referenceDao.touch("loc-1", "1961_1990", NOW_MILLIS) }
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceClimate_refetchesWhenCurrentReferenceMovesToNextDecade() = runTest {
        stubArchive(valueOf = { 1.0 })
        coEvery { referenceDao.get("loc-1", "current") } returns
            ReferenceClimateEntity("loc-1", "current", 1991, 2020, ByteArray(0), 1L)

        val result =
            repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CURRENT, LocalDate.of(2031, 3, 1))

        assertFalse(result.fromCache)
        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "2001-01-01", "2030-12-31", any(), any())
        }
    }

    @Test
    fun getReferenceClimate_mapsHttp429ToRateLimit() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(429)

        val thrown =
            runCatching {
                repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)
            }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
        coVerify(exactly = 0) { referenceDao.insert(any()) }
    }

    // ---- weather cache delegation ----

    @Test
    fun cleanOutdatedWeatherDate_cleansEveryCache() = runTest {
        repository.cleanOutdatedWeatherDate()

        coVerify(exactly = 1) { weatherDao.cleanOutdatedCache(any()) }
        coVerify(exactly = 1) { yearSeriesDao.cleanOutdated(any()) }
        coVerify(exactly = 1) { forecastDao.cleanOutdated(LocalDate.now().toString()) }
    }

    // ---- concurrency ----

    /** Lets the DAOs remember what was inserted, like the database would. */
    private fun useStatefulCaches() {
        val forecasts = mutableMapOf<String, ForecastSeriesEntity>()
        coEvery { forecastDao.get(any()) } coAnswers { forecasts[firstArg()] }
        coEvery { forecastDao.insert(any()) } coAnswers {
            forecasts[firstArg<ForecastSeriesEntity>().locationId] = firstArg()
        }
        val years = mutableMapOf<String, YearSeriesEntity>()
        coEvery { yearSeriesDao.get(any()) } coAnswers { years[firstArg()] }
        coEvery { yearSeriesDao.insert(any()) } coAnswers {
            years[firstArg<YearSeriesEntity>().locationId] = firstArg()
        }
    }

    @Test
    fun getForecast_concurrentIdenticalCallsRequestItOnce() = runTest {
        useStatefulCaches()
        val gate = CompletableDeferred<WeatherResponse>()
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        val first = async { repository.getForecast("loc-1", 1.0, 2.0, today) }
        val second = async { repository.getForecast("loc-1", 1.0, 2.0, today) }
        advanceUntilIdle()
        gate.complete(forecastResponse(today))

        assertFalse(first.await().fromCache)
        // the second caller waited, then found what the first had stored
        assertTrue(second.await().fromCache)
        coVerify(exactly = 1) { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_concurrentIdenticalCallsRequestItOnce() = runTest {
        useStatefulCaches()
        val gate = CompletableDeferred<Unit>()
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            archiveResponse(LocalDate.parse(thirdArg<String>()), LocalDate.parse(arg<String>(3)), arg<String>(4))
        }

        val calls = List(3) { async { repository.getYearToDate("loc-1", 1.0, 2.0, today) } }
        advanceUntilIdle()
        gate.complete(Unit)

        assertEquals(listOf(false, true, true), calls.map { it.await().fromCache })
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun differentRequestsRunInParallel() = runTest {
        useStatefulCaches()
        val gate = CompletableDeferred<Unit>()
        var started = 0
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) } coAnswers {
            started++
            gate.await()
            forecastResponse(today)
        }
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            started++
            gate.await()
            archiveResponse(LocalDate.parse(thirdArg<String>()), LocalDate.parse(arg<String>(3)), arg<String>(4))
        }

        val calls =
            listOf(
                async { repository.getForecast("loc-1", 1.0, 2.0, today) },
                async { repository.getForecast("loc-2", 3.0, 4.0, today) },
                async { repository.getYearToDate("loc-1", 1.0, 2.0, today) },
                async { repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today) },
                async { repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today) },
            )
        advanceUntilIdle()

        // every one of them is on its way before any can finish
        assertEquals(5, started)
        gate.complete(Unit)
        calls.forEach { assertFalse(it.await().fromCache) }
    }

    @Test
    fun cancellationIsNotTurnedIntoAnotherException() = runTest {
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getForecast("loc-1", 1.0, 2.0, today) }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getYearToDate("loc-1", 1.0, 2.0, today) }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today) }
        }
    }

    @Test
    fun aCancelledRequestLetsTheWaitingOneThrough() = runTest {
        useStatefulCaches()
        val gate = CompletableDeferred<WeatherResponse>()
        var calls = 0
        coEvery { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) } coAnswers {
            if (calls++ == 0) gate.await() else forecastResponse(today)
        }

        val first = async { repository.getForecast("loc-1", 1.0, 2.0, today) }
        val second = async { repository.getForecast("loc-1", 1.0, 2.0, today) }
        advanceUntilIdle()
        first.cancel()
        advanceUntilIdle()

        assertFalse(second.await().fromCache)
        assertEquals(2, calls)
    }

    // ---- metrics ----

    private val allVariables = "temperature_2m_mean,sunshine_duration,precipitation_sum"
    private val extraVariables = "sunshine_duration,precipitation_sum"

    @Test
    fun getForecast_requestsAllThreeVariablesAndConvertsSunshineToHours() = runTest {
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 14), sunshineOf = { 5400.0 }, rainOf = { 2.5 }))
        val saved = slot<ForecastSeriesEntity>()
        coEvery { forecastDao.insert(capture(saved)) } returns Unit

        val series = repository.getForecast("loc-1", 1.0, 2.0, today).value

        coVerify(exactly = 1) { weatherApi.getForecast(1.0, 2.0, allVariables, 14, 0, any()) }
        val day = LocalDate.of(2026, 10, 20)
        assertEquals(10.0, series[Metric.TEMPERATURE]!!.valueOrNull(day)!!, 1e-6)
        assertEquals(1.5, series[Metric.SUNSHINE]!!.valueOrNull(day)!!, 1e-6)
        assertEquals(2.5, series[Metric.PRECIPITATION]!!.valueOrNull(day)!!, 1e-6)
        // the blobs hold hours and mm, never raw seconds
        val sunshine = DailySeries.fromBytes(series.start, saved.captured.sunshineData!!)
        assertEquals(1.5, sunshine.valueOrNull(day)!!, 1e-6)
        val rain = DailySeries.fromBytes(series.start, saved.captured.precipitationData!!)
        assertEquals(2.5, rain.valueOrNull(day)!!, 1e-6)
    }

    @Test
    fun getForecast_aCachedRowWithoutExtrasIsRefetchedOnceThenServedFromCache() = runTest {
        useStatefulCaches()
        val start = LocalDate.of(2026, 10, 14)
        forecastDao.insert(ForecastSeriesEntity("loc-1", "2026-10-14", "2026-10-27", blob(start, 14, 6.5), "2026-10-14"))
        stubForecast(forecastResponse(start))

        val first = repository.getForecast("loc-1", 1.0, 2.0, today)
        val second = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(2.0, second.value[Metric.SUNSHINE]!!.valueOrNull(start)!!, 1e-6)
        coVerify(exactly = 1) { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getForecast_missingExtraVariablesAreStoredAsMissingDays() = runTest {
        useStatefulCaches()
        stubForecast(forecastResponse(LocalDate.of(2026, 10, 14), sunshineOf = { null }))

        val first = repository.getForecast("loc-1", 1.0, 2.0, today)
        val second = repository.getForecast("loc-1", 1.0, 2.0, today)

        // absent means no data
        assertNull(first.value[Metric.SUNSHINE])
        // not asked again just because there is nothing to show
        assertTrue(second.fromCache)
        assertNull(second.value[Metric.SUNSHINE])
        assertEquals(setOf(Metric.TEMPERATURE, Metric.PRECIPITATION), second.value.metrics)
        coVerify(exactly = 1) { weatherApi.getForecast(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_requestsAllThreeVariablesAndStoresThem() = runTest {
        stubArchive(sunshineOf = { 3600.0 * (it.dayOfMonth % 5) }, rainOf = { 0.25 }, valueOf = { 4.0 })
        val saved = slot<YearSeriesEntity>()
        coEvery { yearSeriesDao.insert(capture(saved)) } returns Unit

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "2026-01-01", "2026-10-13", allVariables, any())
        }
        val day = LocalDate.of(2026, 10, 13)
        assertEquals(4.0, result.value[Metric.TEMPERATURE]!!.valueOrNull(day)!!, 1e-6)
        assertEquals(3.0, result.value[Metric.SUNSHINE]!!.valueOrNull(day)!!, 1e-6)
        assertEquals(0.25, result.value[Metric.PRECIPITATION]!!.valueOrNull(day)!!, 1e-6)
        val stored = DailySeries.fromBytes(LocalDate.of(2026, 1, 1), saved.captured.sunshineData!!)
        assertEquals(3.0, stored.valueOrNull(day)!!, 1e-6)
        assertEquals(result.value[Metric.PRECIPITATION]!!.size, saved.captured.precipitationData!!.size / 4)
    }

    @Test
    fun getYearToDate_aCachedRowWithoutExtrasIsRefetchedOnceThenServedFromCache() = runTest {
        useStatefulCaches()
        val start = LocalDate.of(2026, 1, 1)
        yearSeriesDao.insert(YearSeriesEntity("loc-1", "2026-01-01", "2026-10-13", blob(start, 286, 7.5), "2026-10-14"))
        stubArchive(valueOf = { 5.0 })

        val first = repository.getYearToDate("loc-1", 1.0, 2.0, today)
        val second = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(5.0, second.value[Metric.TEMPERATURE]!!.valueOrNull(LocalDate.of(2026, 3, 1))!!, 1e-6)
        assertEquals(2.0, second.value[Metric.SUNSHINE]!!.valueOrNull(LocalDate.of(2026, 3, 1))!!, 1e-6)
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceClimate_requestsOnlyTemperature() = runTest {
        stubArchive(valueOf = { 1.0 })
        val saved = slot<ReferenceClimateEntity>()
        coEvery { referenceDao.insert(capture(saved)) } returns Unit

        repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1961-01-01", "1990-12-31", "temperature_2m_mean", any())
        }
        assertNull(saved.captured.sunshineData)
        assertNull(saved.captured.precipitationData)
    }

    @Test
    fun getForecast_aBlobOfTheWrongLengthIsACacheMissNotACrash() = runTest {
        useStatefulCaches()
        val start = LocalDate.of(2026, 10, 14)
        forecastDao.insert(
            ForecastSeriesEntity(
                "loc-1", "2026-10-14", "2026-10-27", blob(start, 14, 6.5), "2026-10-14",
                sunshineData = blob(start, 3, 3.0),
                precipitationData = blob(start, 14, 0.5),
            ),
        )
        stubForecast(forecastResponse(start))

        val result = repository.getForecast("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        assertEquals(14, result.value[Metric.SUNSHINE]!!.size)
        assertTrue(repository.getForecast("loc-1", 1.0, 2.0, today).fromCache)
    }

    @Test
    fun getYearToDate_aBlobOfTheWrongLengthIsACacheMissNotACrash() = runTest {
        useStatefulCaches()
        val start = LocalDate.of(2026, 1, 1)
        yearSeriesDao.insert(
            YearSeriesEntity(
                "loc-1", "2026-01-01", "2026-10-13", blob(start, 286, 7.5), "2026-10-14",
                sunshineData = blob(start, 286, 4.0),
                precipitationData = byteArrayOf(1, 2, 3),
            ),
        )
        stubArchive(valueOf = { 5.0 })

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        assertEquals(286, result.value[Metric.PRECIPITATION]!!.size)
    }

    @Test
    fun getYearToDate_entriesBeyondTheRequestedRangeAreIgnored() = runTest {
        // the service answers three more days than asked for; the range decides the series
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            val start = LocalDate.parse(thirdArg<String>())
            val end = LocalDate.parse(arg<String>(3)).plusDays(3)
            archiveResponse(start, end, arg<String>(4))
        }

        val series = repository.getYearToDate("loc-1", 1.0, 2.0, today).value

        assertEquals(LocalDate.of(2026, 10, 13), series.end)
        assertEquals(286, series.size)
        assertNull(series[Metric.TEMPERATURE]!!.valueOrNull(LocalDate.of(2026, 10, 14)))
    }

    @Test
    fun getForecast_aVariableListShorterThanTheDaysLeavesTheLastDaysMissing() = runTest {
        val dates = (0 until 14).map { today.plusDays(it.toLong()).toString() }
        stubForecast(sampleResponse(dates, dates.map { 10.0 }, dates.take(10).map { 3600.0 }, dates.map { 1.0 }))

        val series = repository.getForecast("loc-1", 1.0, 2.0, today).value

        assertEquals(1.0, series[Metric.SUNSHINE]!!.valueOrNull(today.plusDays(9))!!, 1e-6)
        assertNull(series[Metric.SUNSHINE]!!.valueOrNull(today.plusDays(10)))
        assertEquals(14, series.size)
    }

    // ---- reference extras ----

    private val classicRow =
        ReferenceClimateEntity("loc-1", "1961_1990", 1961, 1990, blob(LocalDate.of(1961, 1, 1), 10957, 3.0), 1L)

    private fun extras(
        reference: HistoricReference = HistoricReference.CLASSIC,
    ) = runBlocking { repository.getReferenceExtras("loc-1", 1.0, 2.0, reference, today) }

    @Test
    fun getReferenceExtras_oneRequestForBothVariablesOverTheReferenceRange() = runTest {
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns classicRow
        stubArchive(sunshineOf = { 7200.0 }, rainOf = { 1.5 }, valueOf = { error("not requested") })
        val sunshine = slot<ByteArray>()
        val rain = slot<ByteArray>()
        coEvery {
            referenceDao.updateExtras("loc-1", "1961_1990", 1961, 1990, capture(sunshine), capture(rain))
        } returns 1

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1961-01-01", "1990-12-31", extraVariables, any())
        }
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
        assertEquals(setOf(Metric.SUNSHINE, Metric.PRECIPITATION), result.value.keys)
        val sun = result.value.getValue(Metric.SUNSHINE)
        assertEquals(1961, sun.firstYear)
        assertEquals(1990, sun.lastYear)
        // seconds became hours
        assertEquals(2.0, sun.series.valueOrNull(LocalDate.of(1975, 6, 1))!!, 1e-6)
        assertEquals(1.5, result.value.getValue(Metric.PRECIPITATION).monthMean(6)!!, 1e-6)
        val stored = DailySeries.fromBytes(LocalDate.of(1961, 1, 1), sunshine.captured)
        assertEquals(2.0, stored.valueOrNull(LocalDate.of(1990, 12, 31))!!, 1e-6)
        assertEquals(1.5, DailySeries.fromBytes(LocalDate.of(1961, 1, 1), rain.captured).valueOrNull(LocalDate.of(1961, 1, 1))!!, 1e-6)
        // once for the reference row that was ensured, once for the extras on it
        coVerify(exactly = 2) { referenceDao.touch("loc-1", "1961_1990", NOW_MILLIS) }
        coVerify(exactly = 0) { referenceDao.insert(any()) }
    }

    @Test
    fun getReferenceExtras_usesTheCurrentPeriodsYears() = runTest {
        stubArchive(valueOf = { 1.0 })

        repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1991-01-01", "2020-12-31", extraVariables, any())
        }
    }

    @Test
    fun getReferenceExtras_isACacheHitWhenBothBlobsExist() = runTest {
        val start = LocalDate.of(1961, 1, 1)
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns
            classicRow.copy(sunshineData = blob(start, 10957, 4.0), precipitationData = blob(start, 10957, 0.5))

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertTrue(result.fromCache)
        assertEquals(4.0, result.value.getValue(Metric.SUNSHINE).series.valueOrNull(start)!!, 1e-6)
        assertEquals(0.5, result.value.getValue(Metric.PRECIPITATION).monthMean(1)!!, 1e-6)
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { referenceDao.touch("loc-1", "1961_1990", NOW_MILLIS) }
    }

    @Test
    fun getReferenceExtras_fetchesWhenOnlyOneBlobExists() = runTest {
        val start = LocalDate.of(1961, 1, 1)
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns classicRow.copy(sunshineData = blob(start, 10957, 4.0))
        stubArchive(valueOf = { 1.0 })
        coEvery { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) } returns 1

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) { referenceDao.updateExtras("loc-1", "1961_1990", 1961, 1990, any(), any()) }
    }

    @Test
    fun getReferenceExtras_returnsTheDataEvenIfTheRowIsGoneAgainWhenTheyAreStored() = runTest {
        // defensive: the row is not found afterwards (evicted meanwhile); the relaxed mock's UPDATE hits 0 rows
        stubArchive(valueOf = { 1.0 })

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        assertEquals(setOf(Metric.SUNSHINE, Metric.PRECIPITATION), result.value.keys)
        coVerify(exactly = 1) { referenceDao.updateExtras("loc-1", "1961_1990", 1961, 1990, any(), any()) }
        // the one insert is the temperature reference that was ensured first
        coVerify(exactly = 1) { referenceDao.insert(any()) }
        coVerify(exactly = 0) { referenceDao.touch(any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_aRowOfAnotherPeriodIsRefetchedFirstAndNeverTouchedByTheUpdate() = runTest {
        // defensive: the stale row keeps being returned, as if another call had put it back
        coEvery { referenceDao.get("loc-1", "current") } returns
            ReferenceClimateEntity("loc-1", "current", 1981, 2010, ByteArray(0), 1L)
        stubArchive(valueOf = { 1.0 })

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)

        // the reference for 1991..2020 was fetched; the UPDATE names the period asked for
        assertFalse(result.fromCache)
        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "1991-01-01", "2020-12-31", "temperature_2m_mean", any())
        }
        coVerify(exactly = 1) { referenceDao.updateExtras("loc-1", "current", 1991, 2020, any(), any()) }
        coVerify(exactly = 0) { referenceDao.touch(any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_aRowOfAnotherPeriodWithBothBlobsIsACacheMiss() = runTest {
        val old = LocalDate.of(1981, 1, 1)
        val events = mutableListOf<String>()
        val rows = useStatefulReferenceDao(events)
        rows["loc-1|current"] =
            ReferenceClimateEntity(
                "loc-1", "current", 1981, 2010, blob(old, 10958, 3.0), 1L,
                sunshineData = blob(old, 10958, 9.0),
                precipitationData = blob(old, 10958, 9.0),
            )
        stubArchiveWithEvents(events)

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)

        assertFalse(result.fromCache)
        assertEquals(
            listOf("fetch temperature_2m_mean", "insert", "fetch $extraVariables", "update"),
            events,
        )
        // fresh values of 1991..2020, not the 9.0 of the old period
        assertEquals(1991, result.value.getValue(Metric.SUNSHINE).firstYear)
        assertEquals(2.0, result.value.getValue(Metric.SUNSHINE).monthMean(6)!!, 1e-6)
        val row = rows.getValue("loc-1|current")
        assertEquals(1991, row.firstYear)
        assertEquals(2020, row.lastYear)
        assertEquals(2.0, DailySeries.fromBytes(LocalDate.of(1991, 1, 1), row.sunshineData!!).valueOrNull(LocalDate.of(2000, 6, 1))!!, 1e-6)
    }

    @Test
    fun getReferenceExtras_aMissingReferenceRowIsFetchedFirstAndBothArePersisted() = runTest {
        val events = mutableListOf<String>()
        val rows = useStatefulReferenceDao(events)
        stubArchiveWithEvents(events)

        val first = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)
        val second = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(
            listOf("fetch temperature_2m_mean", "insert", "fetch $extraVariables", "update"),
            events,
        )
        val row = rows.getValue("loc-1|1961_1990")
        assertTrue(row.sunshineData != null && row.precipitationData != null)
        assertEquals(first.value.keys, second.value.keys)
        assertEquals(2.0, second.value.getValue(Metric.SUNSHINE).monthMean(6)!!, 1e-6)
        coVerify(exactly = 2) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_anExistingReferenceRowIsNotFetchedAgain() = runTest {
        val events = mutableListOf<String>()
        val rows = useStatefulReferenceDao(events)
        rows["loc-1|1961_1990"] = classicRow
        stubArchiveWithEvents(events)

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        assertEquals(listOf("fetch $extraVariables", "update"), events)
        coVerify(exactly = 0) { referenceDao.insert(any()) }
        assertArrayEquals(classicRow.data, rows.getValue("loc-1|1961_1990").data)
    }

    @Test
    fun getReferenceExtras_aRateLimitedReferenceFetchStopsBeforeAnyExtrasRequest() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(429)

        val thrown = runCatching { extras() }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), "temperature_2m_mean", any()) }
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), extraVariables, any()) }
        coVerify(exactly = 0) { referenceDao.insert(any()) }
        coVerify(exactly = 0) { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_aBlobOfTheWrongLengthIsACacheMiss() = runTest {
        val start = LocalDate.of(1961, 1, 1)
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns
            classicRow.copy(sunshineData = blob(start, 5, 4.0), precipitationData = blob(start, 10957, 0.5))
        stubArchive(valueOf = { 1.0 })
        coEvery { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) } returns 1

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertFalse(result.fromCache)
        assertEquals(2.0, result.value.getValue(Metric.SUNSHINE).series.valueOrNull(start)!!, 1e-6)
    }

    @Test
    fun getReferenceExtras_allNullVariableIsStoredAsMissingDaysAndUnavailable() = runTest {
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns classicRow
        stubArchive(sunshineOf = { null }, rainOf = { 2.0 }, valueOf = { 1.0 })
        val sunshine = slot<ByteArray>()
        coEvery {
            referenceDao.updateExtras(any(), any(), any(), any(), capture(sunshine), any())
        } returns 1

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertEquals(setOf(Metric.PRECIPITATION), result.value.keys)
        val stored = DailySeries.fromBytes(LocalDate.of(1961, 1, 1), sunshine.captured)
        assertEquals(10957, stored.size)
        assertFalse(stored.hasValues())
    }

    @Test
    fun getReferenceExtras_aStoredAllMissingBlobIsACacheHitAndStaysUnavailable() = runTest {
        val start = LocalDate.of(1961, 1, 1)
        val empty = DailySeries.fromApi(start, LocalDate.of(1990, 12, 31), emptyList(), emptyList()).toBytes()
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns
            classicRow.copy(sunshineData = empty, precipitationData = blob(start, 10957, 1.0))

        val result = repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)

        assertTrue(result.fromCache)
        assertEquals(setOf(Metric.PRECIPITATION), result.value.keys)
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_mapsHttp429ToRateLimit() = runTest {
        coEvery { referenceDao.get("loc-1", "1961_1990") } returns classicRow
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(429)

        val thrown =
            runCatching {
                repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)
            }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
        coVerify(exactly = 0) { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getReferenceExtras_wrapsOtherErrorsAndNeverSwallowsCancellation() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws httpError(500)
        val http = runCatching { extras() }.exceptionOrNull()
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws CancellationException("cancelled")

        assertTrue(http!!.message!!.contains("HTTP 500"))
        assertThrows(CancellationException::class.java) { extras() }
    }

    @Test
    fun getReferenceExtras_sharesTheReferenceLockSoNothingInterleaves() = runTest {
        val events = mutableListOf<String>()
        val rows = useStatefulReferenceDao(events)
        val gate = CompletableDeferred<Unit>()
        stubArchiveWithEvents(events, gateFor = "temperature_2m_mean", gate = gate)

        val reference = async { repository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today) }
        advanceUntilIdle()
        val extrasCall = async { repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today) }
        advanceUntilIdle()
        // the reference holds the lock, so the extras have not even started their request
        assertEquals(listOf("fetch temperature_2m_mean"), events)
        gate.complete(Unit)

        reference.await()
        assertFalse(extrasCall.await().fromCache)
        // the reference was fetched once; the extras found it and only added theirs
        assertEquals(listOf("fetch temperature_2m_mean", "insert", "fetch $extraVariables", "update"), events)
        val row = rows.getValue("loc-1|1961_1990")
        assertTrue(row.sunshineData != null && row.precipitationData != null)
    }

    @Test
    fun getReferenceExtras_otherPlacesAreNotBlocked() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            started++
            gate.await()
            archiveResponse(LocalDate.parse(thirdArg<String>()), LocalDate.parse(arg<String>(3)), arg<String>(4))
        }

        val calls =
            listOf(
                async { repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today) },
                async { repository.getReferenceExtras("loc-2", 3.0, 4.0, HistoricReference.CLASSIC, today) },
                async { repository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today) },
            )
        advanceUntilIdle()

        assertEquals(3, started)
        gate.complete(Unit)
        calls.forEach { assertFalse(it.await().fromCache) }
    }

    // ---- helpers ----

    private fun stubForecast(response: WeatherResponse) {
        coEvery {
            weatherApi.getForecast(any(), any(), any(), any(), any(), any())
        } returns response
    }

    private fun stubForecastFailure(error: Throwable) {
        coEvery {
            weatherApi.getForecast(any(), any(), any(), any(), any(), any())
        } throws error
    }

    /**
     * Answers any archive request with one value per requested day, for exactly the variables its
     * `daily` parameter names: temperature by [valueOf], seconds of sunshine, mm of rain.
     */
    private fun stubArchive(
        sunshineOf: (LocalDate) -> Double? = { 7200.0 },
        rainOf: (LocalDate) -> Double? = { 1.5 },
        valueOf: (LocalDate) -> Double?,
    ) {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            archiveResponse(
                LocalDate.parse(thirdArg<String>()),
                LocalDate.parse(arg<String>(3)),
                arg<String>(4),
                sunshineOf,
                rainOf,
                valueOf,
            )
        }
    }

    private fun archiveResponse(
        start: LocalDate,
        end: LocalDate,
        variables: String,
        sunshineOf: (LocalDate) -> Double? = { 7200.0 },
        rainOf: (LocalDate) -> Double? = { 1.5 },
        valueOf: (LocalDate) -> Double? = { 1.0 },
    ): WeatherResponse {
        val requested = variables.split(",")
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        return sampleResponse(
            dates.map { it.toString() },
            if (Metric.TEMPERATURE.apiName in requested) dates.map(valueOf) else null,
            if (Metric.SUNSHINE.apiName in requested) dates.map(sunshineOf) else null,
            if (Metric.PRECIPITATION.apiName in requested) dates.map(rainOf) else null,
        )
    }

    private fun MetricSeries.temp(): DailySeries = this[Metric.TEMPERATURE]!!

    /** Lets the reference DAO remember rows like the database would, and note insert/update in [events]. */
    private fun useStatefulReferenceDao(events: MutableList<String>): MutableMap<String, ReferenceClimateEntity> {
        val rows = mutableMapOf<String, ReferenceClimateEntity>()
        coEvery { referenceDao.get(any(), any()) } coAnswers { rows["${firstArg<String>()}|${secondArg<String>()}"] }
        coEvery { referenceDao.insert(any()) } coAnswers {
            events += "insert"
            val row = firstArg<ReferenceClimateEntity>()
            rows["${row.locationId}|${row.referenceId}"] = row
        }
        coEvery { referenceDao.updateExtras(any(), any(), any(), any(), any(), any()) } coAnswers {
            events += "update"
            val key = "${firstArg<String>()}|${secondArg<String>()}"
            val row = rows[key]
            if (row == null || row.firstYear != arg<Int>(2) || row.lastYear != arg<Int>(3)) {
                0
            } else {
                rows[key] = row.copy(sunshineData = arg(4), precipitationData = arg(5))
                1
            }
        }
        return rows
    }

    /** Archive answers (2 h of sun, 1.5 mm, 1 degree) that note each request's variables in [events]. */
    private fun stubArchiveWithEvents(
        events: MutableList<String>,
        gateFor: String? = null,
        gate: CompletableDeferred<Unit>? = null,
    ) {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            val variables = arg<String>(4)
            events += "fetch $variables"
            if (variables == gateFor) gate!!.await()
            archiveResponse(LocalDate.parse(thirdArg<String>()), LocalDate.parse(arg<String>(3)), variables)
        }
    }

    /** A float32 blob of [days] days that all have [value]. */
    private fun blob(
        start: LocalDate,
        days: Int,
        value: Double,
    ): ByteArray {
        val dates = (0 until days).map { start.plusDays(it.toLong()).toString() }
        return DailySeries.fromApi(start, start.plusDays(days - 1L), dates, dates.map { value }).toBytes()
    }

    private fun httpError(code: Int) = HttpException(Response.error<WeatherResponse>(code, "error".toResponseBody()))

    private fun sampleResponse(
        dates: List<String>,
        temps: List<Double?>?,
        sunshine: List<Double?>? = null,
        rain: List<Double?>? = null,
    ): WeatherResponse =
        WeatherResponse(
            latitude = 0.0,
            longitude = 0.0,
            generationtime_ms = 0.0,
            utc_offset_seconds = 0,
            timezone = "UTC",
            timezone_abbreviation = "UTC",
            elevation = 0.0,
            daily_units = DailyUnits(time = "iso8601", temperature_2m_mean = "C"),
            daily = Daily(time = dates, temperature_2m_mean = temps, sunshine_duration = sunshine, precipitation_sum = rain),
        )

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
