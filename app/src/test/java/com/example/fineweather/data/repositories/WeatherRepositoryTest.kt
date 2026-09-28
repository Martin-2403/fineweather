package com.example.fineweather.data.repositories

import android.util.Log
import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.dao.WeatherDao
import com.example.fineweather.data.models.Daily
import com.example.fineweather.data.models.DailyUnits
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.WeatherResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.LocalDate

class WeatherRepositoryTest {

    private val weatherApi = mockk<OpenMeteoWeatherApiService>(relaxed = true)
    private val archiveApi = mockk<OpenMeteoArchiveApiService>(relaxed = true)
    private val weatherDao = mockk<WeatherDao>(relaxed = true)
    private val repository = WeatherRepository(weatherApi, archiveApi, weatherDao)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun buildHistoricDateRange_usesExpectedYearsAndMonth() {
        val range = repository.buildHistoricDateRange(
            timeSpan = 30,
            now = LocalDate.of(2026, 3, 21),
        )

        assertEquals("1996-03-01", range.start)
        assertEquals("2025-03-31", range.end)
    }

    @Test
    fun buildHistoricDateRange_handlesLeapYearFebruary() {
        val range = repository.buildHistoricDateRange(
            timeSpan = 1,
            now = LocalDate.of(2025, 2, 15),
        )

        assertEquals("2024-02-01", range.start)
        assertEquals("2024-02-29", range.end)
    }

    @Test
    fun buildHistoricDateRange_requiresPositiveTimeSpan() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.buildHistoricDateRange(timeSpan = 0, now = LocalDate.of(2026, 3, 1))
        }
    }

    @Test
    fun buildHistoricDateRange_usesClassicReferenceYears() {
        val range =
            repository.buildHistoricDateRange(
                reference = HistoricReference.CLASSIC,
                now = LocalDate.of(2026, 3, 10),
            )

        assertEquals("1961-03-01", range.start)
        assertEquals("1990-03-31", range.end)
    }

    @Test
    fun buildHistoricDateRange_usesCurrentReferenceYears() {
        val range =
            repository.buildHistoricDateRange(
                reference = HistoricReference.CURRENT,
                now = LocalDate.of(2026, 3, 10),
            )

        assertEquals("1991-03-01", range.start)
        assertEquals("2020-03-31", range.end)
    }

    @Test
    fun calculateAverageForecastTemperature_usesCachedForecast() = runTest {
        val response = sampleResponse(
            listOf("2026-03-01", "2026-03-02"),
            listOf(10.0, 14.0),
        )
        coEvery {
            weatherApi.getForecast(
                latitude = any(),
                longitude = any(),
                daily = any(),
                forecastDays = any(),
                pastDays = any(),
                timezone = any(),
            )
        } returns response

        repository.getWeatherForecast(1.0, 2.0, 2)

        assertEquals(12.0, repository.calculateAverageForecastTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageCurrentTemperature_usesCachedCurrent() = runTest {
        val response = sampleResponse(
            listOf("2026-03-01", "2026-03-02"),
            listOf(7.0, 9.0),
        )
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherCurrent(1.0, 2.0, 2)

        assertEquals(8.0, repository.calculateAverageCurrentTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageCurrentTemperature_returnsFallbackWhenEmpty() = runTest {
        val response = sampleResponse(emptyList(), emptyList())
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherCurrent(1.0, 2.0, 2)

        assertEquals(-273.15, repository.calculateAverageCurrentTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageForecastTemperature_returnsFallbackWhenNoCache() {
        assertEquals(-273.15, repository.calculateAverageForecastTemperature(), 0.0001)
    }

    @Test
    fun calculateAverageCurrentTemperature_returnsFallbackWhenNoCache() {
        assertEquals(-273.15, repository.calculateAverageCurrentTemperature(), 0.0001)
    }

    @Test
    fun calculateCurrentMonthStats_returnsAverageAndCount() = runTest {
        val now = LocalDate.now()
        val dates =
            listOf(
                now.withDayOfMonth(1).toString(),
                now.withDayOfMonth(2).toString(),
                now.withDayOfMonth(3).toString(),
            )
        val response = sampleResponse(dates, listOf(10.0, 12.0, 14.0))
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherCurrent(1.0, 2.0, 3)

        val stats = repository.calculateCurrentMonthStats()
        assertEquals(now.format(java.time.format.DateTimeFormatter.ofPattern("MM")), stats.month)
        assertEquals(12.0, stats.average ?: Double.NaN, 0.0001)
        assertEquals(3, stats.dayCount)
    }

    @Test
    fun calculateCurrentMonthStats_returnsNullWhenMonthMissing() = runTest {
        val now = LocalDate.now()
        val otherMonth = now.minusMonths(1).withDayOfMonth(1)
        val response = sampleResponse(
            listOf(otherMonth.toString()),
            listOf(10.0),
        )
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherCurrent(1.0, 2.0, 1)

        val stats = repository.calculateCurrentMonthStats()
        assertNull(stats.average)
        assertEquals(0, stats.dayCount)
    }

    @Test
    fun calculateCurrentMonthStats_throwsWhenNoCachedData() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.calculateCurrentMonthStats()
        }
    }

    @Test
    fun calculateCurrentMonthStats_throwsWhenListsEmpty() = runTest {
        val response = sampleResponse(emptyList(), emptyList())
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherCurrent(1.0, 2.0, 2)

        assertThrows(IllegalArgumentException::class.java) {
            repository.calculateCurrentMonthStats()
        }
    }

    @Test
    fun calculateAverageHistoricMonthlyTemperature_returnsAverage() = runTest {
        val now = LocalDate.now()
        val dates =
            listOf(
                LocalDate.of(1961, now.monthValue, 1).toString(),
                LocalDate.of(1990, now.monthValue, 2).toString(),
            )
        val response = sampleResponse(dates, listOf(5.0, 9.0))
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherHistory(1.0, 2.0, HistoricReference.CLASSIC)

        val result = repository.calculateAverageHistoricMonthlyTemperature()
        assertEquals((5.0 + 9.0) / 2.0, result?.second ?: Double.NaN, 0.0001)
    }

    @Test
    fun calculateAverageHistoricMonthlyTemperature_returnsNullWhenNoCurrentMonth() = runTest {
        val now = LocalDate.now()
        val otherMonth = now.plusMonths(1)
        val response =
            sampleResponse(
                listOf(LocalDate.of(1961, otherMonth.monthValue, 1).toString()),
                listOf(7.0),
            )
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        repository.getWeatherHistory(1.0, 2.0, HistoricReference.CLASSIC)

        val result = repository.calculateAverageHistoricMonthlyTemperature()
        assertNull(result)
    }

    @Test
    fun calculateAverageHistoricMonthlyTemperature_throwsWhenNoData() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.calculateAverageHistoricMonthlyTemperature()
        }
    }

    @Test
    fun getDateRange_returnsCurrentAndPastDates() {
        val now = LocalDate.now()
        val range = repository.getDateRange(7)

        assertEquals(now.toString(), range.first)
        assertEquals(now.minusDays(7).toString(), range.second)
    }

    @Test
    fun getWeatherCurrent_throwsWhenApiFails() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws RuntimeException("boom")

        val thrown = runCatching { repository.getWeatherCurrent(1.0, 2.0, 2) }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }

    @Test
    fun getWeatherForecast_throwsWhenApiFails() = runTest {
        coEvery {
            weatherApi.getForecast(
                latitude = any(),
                longitude = any(),
                daily = any(),
                forecastDays = any(),
                pastDays = any(),
                timezone = any(),
            )
        } throws RuntimeException("boom")

        val thrown = runCatching { repository.getWeatherForecast(1.0, 2.0, 2) }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }

    @Test
    fun getWeatherHistory_throwsForHttpException() = runTest {
        val response = Response.error<WeatherResponse>(500, "error".toResponseBody())
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws HttpException(response)

        val thrown = runCatching { repository.getWeatherHistory(1.0, 2.0, HistoricReference.CLASSIC) }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }

    @Test
    fun getWeatherHistory_throwsForGenericException() = runTest {
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } throws RuntimeException("boom")

        val thrown = runCatching { repository.getWeatherHistory(1.0, 2.0, HistoricReference.CLASSIC) }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }

    @Test
    fun getWeatherHistory_returnsOriginalWhenDatesInvalid() = runTest {
        val response = sampleResponse(listOf("invalid-date"), listOf(5.0))
        coEvery { archiveApi.getHistoricData(any(), any(), any(), any(), any(), any()) } returns response

        val result = repository.getWeatherHistory(1.0, 2.0, HistoricReference.CLASSIC)

        assertEquals(response.daily.time, result.daily.time)
        assertEquals(response.daily.temperature_2m_mean, result.daily.temperature_2m_mean)
    }

    @Test
    fun buildHistoricDateRange_usesDefaultNow() {
        val now = LocalDate.now()
        val range = repository.buildHistoricDateRange(30)

        assertEquals(LocalDate.of(now.year - 30, now.monthValue, 1).toString(), range.start)
        assertEquals(
            LocalDate.of(now.year - 1, now.monthValue, java.time.YearMonth.of(now.year - 1, now.monthValue).lengthOfMonth())
                .toString(),
            range.end,
        )
    }

    @Test
    fun daoDelegations_invokeRoom() = runTest {
        repository.cleanOutdatedWeatherDate()
        repository.getCachedWeatherById("loc-1")
        repository.insertWeather(mockk(relaxed = true))

        coVerify(exactly = 1) { weatherDao.cleanOutdatedCache(any()) }
        coVerify(exactly = 1) { weatherDao.getWeatherById("loc-1") }
        coVerify(exactly = 1) { weatherDao.insertWeather(any()) }
    }

    private fun sampleResponse(
        dates: List<String>,
        temps: List<Double>,
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
}
