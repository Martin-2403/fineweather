package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.dao.ReferenceClimateDao
import com.example.fineweather.data.local.dao.WeatherDao
import com.example.fineweather.data.local.dao.YearSeriesDao
import com.example.fineweather.data.local.entities.ReferenceClimateEntity
import com.example.fineweather.data.local.entities.YearSeriesEntity
import com.example.fineweather.data.models.Daily
import com.example.fineweather.data.models.DailyUnits
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.WeatherResponse
import com.example.fineweather.domain.DailySeries
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val repository =
        WeatherRepository(weatherApi, archiveApi, weatherDao, referenceDao, yearSeriesDao, clock = { NOW_MILLIS })

    private val today = LocalDate.of(2026, 10, 14)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        coEvery { referenceDao.get(any(), any()) } returns null
        coEvery { yearSeriesDao.get(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ---- forecast ----

    @Test
    fun calculateAverageForecastTemperature_usesCachedForecast() = runTest {
        stubForecast(sampleResponse(listOf("2026-03-01", "2026-03-02"), listOf(10.0, 14.0)))

        repository.getWeatherForecast(1.0, 2.0, 2)

        assertEquals(12.0, repository.calculateAverageForecastTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageForecastTemperature_ignoresMissingDays() = runTest {
        stubForecast(sampleResponse(listOf("2026-03-01", "2026-03-02", "2026-03-03"), listOf(10.0, null, 14.0)))

        repository.getWeatherForecast(1.0, 2.0, 3)

        assertEquals(12.0, repository.calculateAverageForecastTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageForecastTemperature_returnsFallbackWhenNoCache() {
        assertEquals(-273.15, repository.calculateAverageForecastTemperature(), 0.0001)
    }

    @Test
    fun getWeatherForecast_throwsWhenApiFails() = runTest {
        stubForecastFailure(RuntimeException("boom"))

        val thrown = runCatching { repository.getWeatherForecast(1.0, 2.0, 2) }.exceptionOrNull()

        assertTrue(thrown is Exception)
        assertFalse(thrown is RateLimitException)
    }

    @Test
    fun getWeatherForecast_mapsHttp429ToRateLimit() = runTest {
        stubForecastFailure(httpError(429))

        val thrown = runCatching { repository.getWeatherForecast(1.0, 2.0, 2) }.exceptionOrNull()

        assertTrue(thrown is RateLimitException)
    }

    // ---- year to date ----

    @Test
    fun getYearToDate_fetchesJanuaryFirstToYesterdayAndCaches() = runTest {
        stubArchive { date -> date.dayOfMonth.toDouble() }
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
        stubArchive { 1.0 }

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, LocalDate.of(2026, 1, 10))

        coVerify(exactly = 1) {
            archiveApi.getHistoricData(1.0, 2.0, "2025-12-10", "2026-01-09", any(), any())
        }
        assertEquals(LocalDate.of(2025, 12, 10), result.value.start)
    }

    @Test
    fun getYearToDate_usesCacheFetchedToday() = runTest {
        val series = DailySeries.fromApi(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 10, 13),
            listOf("2026-10-13"),
            listOf(7.5),
        )
        coEvery { yearSeriesDao.get("loc-1") } returns
            YearSeriesEntity("loc-1", "2026-01-01", "2026-10-13", series.toBytes(), "2026-10-14")

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertTrue(result.fromCache)
        assertEquals(7.5, result.value.valueOrNull(LocalDate.of(2026, 10, 13))!!, 1e-6)
        coVerify(exactly = 0) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_refetchesWhenCacheIsFromAnEarlierDay() = runTest {
        stubArchive { 1.0 }
        coEvery { yearSeriesDao.get("loc-1") } returns
            YearSeriesEntity("loc-1", "2026-01-01", "2026-10-12", ByteArray(0), "2026-10-13")

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today)

        assertFalse(result.fromCache)
        coVerify(exactly = 1) { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun getYearToDate_treatsNullValuesAsMissingDays() = runTest {
        stubArchive { date -> if (date.dayOfMonth == 5) null else 10.0 }

        val result = repository.getYearToDate("loc-1", 1.0, 2.0, today).value

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
        stubArchive { date -> date.monthValue.toDouble() }
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
        stubArchive { 1.0 }

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
        stubArchive { 1.0 }
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
    fun daoDelegations_invokeRoom() = runTest {
        repository.cleanOutdatedWeatherDate()
        repository.getCachedWeatherById("loc-1")
        repository.insertWeather(mockk(relaxed = true))

        coVerify(exactly = 1) { weatherDao.cleanOutdatedCache(any()) }
        coVerify(exactly = 1) { yearSeriesDao.cleanOutdated(any()) }
        coVerify(exactly = 1) { weatherDao.getWeatherById("loc-1") }
        coVerify(exactly = 1) { weatherDao.insertWeather(any()) }
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

    /** Answers any archive request with one value per requested day, computed by [valueOf]. */
    private fun stubArchive(valueOf: (LocalDate) -> Double?) {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } coAnswers {
            val start = LocalDate.parse(thirdArg<String>())
            val end = LocalDate.parse(arg<String>(3))
            val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
            sampleResponse(dates.map { it.toString() }, dates.map(valueOf))
        }
    }

    private fun httpError(code: Int) = HttpException(Response.error<WeatherResponse>(code, "error".toResponseBody()))

    private fun sampleResponse(
        dates: List<String>,
        temps: List<Double?>,
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
            daily = Daily(time = dates, temperature_2m_mean = temps),
        )

    private companion object {
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
