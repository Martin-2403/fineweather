package com.example.fineweather.viewmodels

import android.util.Log
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var geoCodeRepository: GeoCodeRepository
    private lateinit var viewModel: WeatherViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        weatherRepository = mockk(relaxed = true)
        geoCodeRepository = mockk(relaxed = true)
        viewModel = WeatherViewModel(weatherRepository, geoCodeRepository)
    }

    @org.junit.After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesCachedCurrentAndHistoricWhenAvailable() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any()) } returns data
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 12.34
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 0) { weatherRepository.getWeatherCurrent(any(), any(), any()) }
        coVerify(exactly = 0) { weatherRepository.getWeatherHistory(any(), any(), any()) }
        coVerify(atLeast = 1) { weatherRepository.getCachedWeatherById(any()) }
        coVerify(exactly = 1) { weatherRepository.getWeatherForecast(any(), any(), any()) }

        assertEquals("10.00°C", viewModel.resultCurrentAverage.value)
        assertEquals("11.00°C", viewModel.resultCurrentMonthAverage.value)
        assertEquals("12.34°C", viewModel.resultForecastAverage.value)
        assertEquals("9.00°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_callsApiWhenCacheMissing() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any()) } returns data
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateAverageCurrentMonthlyTemperature() } returns Pair("March", 8.5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 6.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) { weatherRepository.getWeatherCurrent(any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getWeatherForecast(any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getWeatherHistory(any(), any(), any()) }
        coVerify(atLeast = 1) { weatherRepository.getCachedWeatherById(any()) }

        assertEquals("7.00°C", viewModel.resultCurrentAverage.value)
        assertEquals("8.50°C", viewModel.resultCurrentMonthAverage.value)
        assertEquals("9.10°C", viewModel.resultForecastAverage.value)
        assertEquals("6.00°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesLatestHistoricCacheWhenSameDayMissing() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")
        val cachedHistoric = buildEntity(
            id = data.id,
            date = java.time.LocalDate.now().minusDays(1).toString(),
            historicMonthlyAverage = 5.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any()) } returns data
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cachedHistoric
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateAverageCurrentMonthlyTemperature() } returns Pair("March", 8.5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 0) { weatherRepository.getWeatherHistory(any(), any(), any()) }
        coVerify(atLeast = 1) { weatherRepository.getCachedWeatherById(any()) }

        assertEquals("5.00°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_refreshesCurrentWhenCachedDateIsStale() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")
        val cached = buildEntity(
            id = data.id,
            date = java.time.LocalDate.now().minusDays(1).toString(),
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any()) } returns data
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateAverageCurrentMonthlyTemperature() } returns Pair("March", 8.5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) { weatherRepository.getWeatherCurrent(any(), any(), any()) }
        coVerify(exactly = 0) { weatherRepository.getWeatherHistory(any(), any(), any()) }

        assertEquals("7.00°C", viewModel.resultCurrentAverage.value)
        assertEquals("8.50°C", viewModel.resultCurrentMonthAverage.value)
        assertEquals("9.00°C", viewModel.resultHistoricAverage.value)
    }

    private fun buildEntity(
        id: String,
        date: String,
        currentAverage: Double? = null,
        currentMonthAverage: Double? = null,
        forecastAverage: Double? = null,
        historicMonthlyAverage: Double? = null,
    ): WeatherEntity =
        WeatherEntity(
            id = id,
            date = date,
            locationName = "Test City",
            country = "TC",
            latitude = 1.0,
            longitude = 2.0,
            currentAverage = currentAverage,
            currentMonthAverage = currentMonthAverage,
            forecastAverage = forecastAverage,
            historicMonthlyAverage = historicMonthlyAverage,
        )
}
