package com.example.fineweather.viewmodels

import android.util.Log
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.GeocodeSelection
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.FavoriteAddResult
import com.example.fineweather.data.repositories.WeatherRepository.CurrentMonthStats
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.WeatherRepository
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fineweather.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var geoCodeRepository: GeoCodeRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var viewModel: WeatherViewModel
    private val fixedNow = LocalDate.of(2026, 4, 2)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        weatherRepository = mockk(relaxed = true)
        geoCodeRepository = mockk(relaxed = true)
        settingsRepository = FakeSettingsRepository()
        favoriteRepository = FakeFavoriteRepository()
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
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
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
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
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateHistoricMonthlyStats() } returns
            WeatherRepository.HistoricMonthlyStats("March", 6.0, 0.0)
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
            date = LocalDate.now().minusDays(1).toString(),
            historicMonthlyAverage = 5.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cachedHistoric
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
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
            date = LocalDate.now().minusDays(1).toString(),
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_resetsStateWhenLocationIsBlank() = runTest {
        viewModel.setDataValues("10.00°C")

        viewModel.fetchWeather("   ")
        advanceUntilIdle()

        assertEquals("Enter a location to see temperature averages", viewModel.status.value)
        assertEquals("-", viewModel.resultCurrentAverage.value)
        assertEquals("-", viewModel.resultCurrentMonthAverage.value)
        assertEquals("-", viewModel.resultForecastAverage.value)
        assertEquals("-", viewModel.resultHistoricAverage.value)

        coVerify(exactly = 0) { geoCodeRepository.getGeoCode(any(), any()) }
        coVerify(exactly = 0) { weatherRepository.cleanOutdatedWeatherDate() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_setsResolvedLocationAndCounters() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 12.0
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Berlin", viewModel.resolvedLocationName.value)
        assertEquals(1, viewModel.apiCallCount.value)
        assertEquals(2, viewModel.cacheHitCount.value)
    }

    @Test
    fun exposesForecastAndHistoricStateFlows() {
        assertEquals(WeatherViewModel.FORECAST_DAYS_SHORT, viewModel.forecastDays.value)
        assertEquals(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE, viewModel.historicReference.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesCachedForecastWhenAvailable() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 0) { weatherRepository.getWeatherForecast(any(), any(), any()) }
        assertEquals(0, viewModel.apiCallCount.value)
        assertEquals(3, viewModel.cacheHitCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesUpdatedForecastDaysWhenSet() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 6.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getWeatherForecast(any(), any(), WeatherViewModel.FORECAST_DAYS_LONG)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_ignoresCachedForecastWhenDaysMismatch() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            forecastDays = WeatherViewModel.FORECAST_DAYS_SHORT,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) { weatherRepository.getWeatherForecast(any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_setsNoDataWhenCurrentMonthIsEmpty() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", null, 0)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 6.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("No data yet", viewModel.resultCurrentMonthAverage.value)
        assertEquals(0, viewModel.currentMonthDayCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_filtersBlankLocationParts() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertTrue(viewModel.status.value.startsWith("Berlin "))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailure() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("City not found", viewModel.status.value)
        assertEquals("N/A", viewModel.resultCurrentAverage.value)
        assertEquals("N/A", viewModel.resultForecastAverage.value)
        assertEquals("N/A", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailureWithNullMessage() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("City not found", viewModel.status.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesCurrentFetchFailure() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } throws RuntimeException("boom")
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.0
        every { weatherRepository.calculateHistoricMonthlyStats() } returns
            WeatherRepository.HistoricMonthlyStats("March", 6.0, 0.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultCurrentAverage.value)
        assertEquals("Error", viewModel.resultCurrentMonthAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesForecastFetchFailure() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } throws RuntimeException("boom")
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 6.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultForecastAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesHistoricFetchFailure() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } throws RuntimeException("boom")
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.0
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_fetchWhenCacheMissing() = runTest {
        val settingsRepository = FakeSettingsRepository()
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            forecastDays = WeatherViewModel.FORECAST_DAYS_SHORT,
            historicReference = HistoricReference.CLASSIC,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 8.0
        every { weatherRepository.calculateHistoricMonthlyStats() } returns
            WeatherRepository.HistoricMonthlyStats("March", 5.0, 0.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals("8.00°C", viewModel.resultForecastAverage.value)
        assertEquals("5.00°C", viewModel.resultHistoricAverage.value)
        coVerify(atLeast = 1) { weatherRepository.getWeatherForecast(any(), any(), any()) }
        coVerify(atLeast = 1) { weatherRepository.getWeatherHistory(any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_useCachedShortForecastAndClassicHistoric() = runTest {
        val settingsRepository =
            FakeSettingsRepository(
                initialDays = WeatherViewModel.FORECAST_DAYS_LONG,
                initialReference = HistoricReference.CURRENT,
            )
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            forecastDays = WeatherViewModel.FORECAST_DAYS_LONG,
            historicReference = HistoricReference.CURRENT,
            historicMonthlyAverage = 9.0,
        ).copy(
            forecastAverage7 = 7.0,
            forecastDate7 = data.timestamp,
            historicMonthlyAverageClassic = 6.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_SHORT)
        settingsRepository.setHistoricReference(HistoricReference.CLASSIC)
        advanceUntilIdle()

        assertEquals("7.00°C", viewModel.resultForecastAverage.value)
        assertEquals("6.00°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesCurrentHistoricCacheWhenReferenceCurrent() = runTest {
        val settingsRepository = FakeSettingsRepository(initialReference = HistoricReference.CURRENT)
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicReference = HistoricReference.CURRENT,
            historicMonthlyAverage = 13.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("13.00°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_persistsHistoricCurrentWhenAvailable() = runTest {
        val settingsRepository = FakeSettingsRepository(initialReference = HistoricReference.CURRENT)
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateHistoricMonthlyStats() } returns
            WeatherRepository.HistoricMonthlyStats("March", 6.0, 0.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("6.00°C", viewModel.resultHistoricAverage.value)
        coVerify(atLeast = 1) { weatherRepository.insertWeather(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesNullHistoricAverage() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.1
        every { weatherRepository.calculateHistoricMonthlyStats() } returns null
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns null
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("N/A°C", viewModel.resultHistoricAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_handleNullCache() = runTest {
        val settingsRepository = FakeSettingsRepository()
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns null
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 8.0
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 5.0)
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageCurrentTemperature() } returns 7.0
        every { weatherRepository.calculateCurrentMonthStats() } returns CurrentMonthStats("03", 8.5, 5)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_SHORT)
        settingsRepository.setHistoricReference(HistoricReference.CLASSIC)
        advanceUntilIdle()

        coVerify(atLeast = 1) { weatherRepository.getWeatherForecast(any(), any(), any()) }
        coVerify(atLeast = 1) { weatherRepository.getWeatherHistory(any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesExistingCurrentWhenCurrentFetchFails() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1")
        val cached = buildEntity(
            id = data.id,
            date = LocalDate.now().minusDays(1).toString(),
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherCurrent(any(), any(), any()) } throws RuntimeException("boom")
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.getWeatherHistory(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 9.0
        every { weatherRepository.calculateAverageHistoricMonthlyTemperature() } returns Pair("March", 6.0)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(atLeast = 1) { weatherRepository.insertWeather(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_useCachedValuesWhenAvailable() = runTest {
        val settingsRepository = FakeSettingsRepository()
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached = buildEntity(
            id = data.id,
            date = data.timestamp,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            forecastDays = WeatherViewModel.FORECAST_DAYS_SHORT,
            historicReference = HistoricReference.CLASSIC,
            historicMonthlyAverage = 9.0,
        ).copy(
            forecastAverage14 = 15.0,
            forecastDate14 = data.timestamp,
            historicMonthlyAverageCurrent = 13.0,
        )

        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        coEvery { weatherRepository.insertWeather(any()) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals("15.00°C", viewModel.resultForecastAverage.value)
        assertEquals("13.00°C", viewModel.resultHistoricAverage.value)
        assertTrue(viewModel.cacheHitCount.value >= 1)
    }

    @Test
    fun setForecastDays_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            viewModel.setForecastDays(999)
        }
    }

    @Test
    fun setForecastDays_noopWhenSame() {
        val countingRepository = CountingSettingsRepository()
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                countingRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )

        viewModel.setForecastDays(WeatherViewModel.FORECAST_DAYS_SHORT)

        assertEquals(0, countingRepository.setForecastCalls)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun toggleFavorite_addsSelectedPlaceToFavorites() = runTest {
        val selected = GeoPlace("loc-1", "Berlin", "Germany", null, 1.0, 2.0)
        coEvery { weatherRepository.getCachedWeatherById(selected.id) } returns buildEntity(
            id = selected.id,
            date = fixedNow.toString(),
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )
        viewModel.selectPlace(selected)
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(listOf(selected), viewModel.favorites.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun toggleFavorite_whenFavoritesFull_emitsSnackbarMessage() = runTest {
        val fullFavorites =
            (1..10).map { index ->
                GeoPlace(
                    id = "loc-$index",
                    name = "City $index",
                    country = "Country",
                    admin1 = null,
                    latitude = index.toDouble(),
                    longitude = index.toDouble(),
                )
            }
        favoriteRepository = FakeFavoriteRepository(fullFavorites)
        viewModel =
            WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
                nowProvider = { fixedNow },
            )
        val selected = GeoPlace("loc-11", "New City", "Country", null, 11.0, 11.0)
        coEvery { weatherRepository.getCachedWeatherById(selected.id) } returns buildEntity(
            id = selected.id,
            date = fixedNow.toString(),
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )
        viewModel.selectPlace(selected)
        advanceUntilIdle()

        val snackbarDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.snackbarMessages.first() }
        viewModel.toggleFavorite()
        advanceUntilIdle()

        assertEquals(
            "Favorites limit reached (10). Remove one to add another.",
            snackbarDeferred.await(),
        )
        assertEquals(fullFavorites, viewModel.favorites.value)
    }

    @Test
    fun weatherViewModelFactory_createsViewModel() {
        val factory =
            WeatherViewModelFactory(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
            )

        val created = factory.create(WeatherViewModel::class.java)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_createsWithExtras() {
        val factory =
            WeatherViewModelFactory(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
            )

        val created = factory.create(WeatherViewModel::class.java, CreationExtras.Empty)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_createsWithKClassExtras() {
        val factory =
            WeatherViewModelFactory(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
            )

        val created = factory.create(WeatherViewModel::class, CreationExtras.Empty)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_throwsForUnknownClass() {
        val factory =
            WeatherViewModelFactory(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
            )

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(SettingsViewModel::class.java)
        }
    }

    private fun buildEntity(
        id: String,
        date: String,
        currentAverage: Double? = null,
        currentMonthAverage: Double? = null,
        forecastAverage: Double? = null,
        forecastDays: Int = WeatherViewModel.FORECAST_DAYS_SHORT,
        historicReference: HistoricReference = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
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
            forecastAverage7 = if (forecastDays == WeatherViewModel.FORECAST_DAYS_SHORT) {
                forecastAverage
            } else {
                null
            },
            forecastAverage14 = if (forecastDays == WeatherViewModel.FORECAST_DAYS_LONG) {
                forecastAverage
            } else {
                null
            },
            forecastDate7 = if (forecastDays == WeatherViewModel.FORECAST_DAYS_SHORT &&
                forecastAverage != null
            ) {
                date
            } else {
                null
            },
            forecastDate14 = if (forecastDays == WeatherViewModel.FORECAST_DAYS_LONG &&
                forecastAverage != null
            ) {
                date
            } else {
                null
            },
            historicMonthlyAverageClassic =
                if (historicReference == HistoricReference.CLASSIC) {
                    historicMonthlyAverage
                } else {
                    null
                },
            historicMonthlyAverageCurrent =
                if (historicReference == HistoricReference.CURRENT) {
                    historicMonthlyAverage
                } else {
                    null
                },
        )

    private fun buildSelectionFromData(
        data: FineWeatherData,
        admin1: String? = null,
    ): GeocodeSelection {
        val place =
            GeoPlace(
                id = data.id,
                name = data.location,
                country = data.country,
                admin1 = admin1,
                latitude = data.coordinates.first,
                longitude = data.coordinates.second,
            )
        return GeocodeSelection(primary = place, candidates = emptyList())
    }

    private class FakeFavoriteRepository(
        initialFavorites: List<GeoPlace> = emptyList(),
    ) : FavoriteRepository {
        private val state = MutableStateFlow(initialFavorites)
        override val favorites: Flow<List<GeoPlace>> = state

        override suspend fun addFavorite(place: GeoPlace): FavoriteAddResult {
            if (state.value.none { it.id == place.id } && state.value.size >= 10) {
                return FavoriteAddResult.LIMIT_REACHED
            }
            state.value =
                state.value
                    .filterNot { it.id == place.id } + place
            return FavoriteAddResult.ADDED
        }

        override suspend fun removeFavorite(placeId: String) {
            state.value = state.value.filterNot { it.id == placeId }
        }
    }

    private class FakeSettingsRepository(
        initialDays: Int = WeatherViewModel.FORECAST_DAYS_SHORT,
        initialReference: HistoricReference = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
        initialLanguage: String = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
        initialComparisonMode: TemperatureComparisonMode = SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialDays)
        private val referenceState = MutableStateFlow(initialReference)
        private val languageState = MutableStateFlow(initialLanguage)
        private val comparisonModeState = MutableStateFlow(initialComparisonMode)
        private val hasSeenInfoState = MutableStateFlow(false)
        override val forecastDays: Flow<Int> = state
        override val historicReference: Flow<HistoricReference> = referenceState
        override val searchLanguage: Flow<String> = languageState
        override val temperatureComparisonMode: Flow<TemperatureComparisonMode> = comparisonModeState
        override val hasSeenInfo: Flow<Boolean> = hasSeenInfoState

        override suspend fun setForecastDays(days: Int) {
            state.value = days
        }

        override suspend fun setHistoricReference(reference: HistoricReference) {
            referenceState.value = reference
        }

        override suspend fun setSearchLanguage(language: String) {
            languageState.value = language
        }

        override suspend fun setTemperatureComparisonMode(mode: TemperatureComparisonMode) {
            comparisonModeState.value = mode
        }

        override suspend fun setHasSeenInfo(seen: Boolean) {
            hasSeenInfoState.value = seen
        }
    }

    private class CountingSettingsRepository(
        initialDays: Int = WeatherViewModel.FORECAST_DAYS_SHORT,
        initialReference: HistoricReference = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
        initialLanguage: String = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
        initialComparisonMode: TemperatureComparisonMode = SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialDays)
        private val referenceState = MutableStateFlow(initialReference)
        private val languageState = MutableStateFlow(initialLanguage)
        private val comparisonModeState = MutableStateFlow(initialComparisonMode)
        private val hasSeenInfoState = MutableStateFlow(false)
        var setForecastCalls = 0
            private set
        override val forecastDays: Flow<Int> = state
        override val historicReference: Flow<HistoricReference> = referenceState
        override val searchLanguage: Flow<String> = languageState
        override val temperatureComparisonMode: Flow<TemperatureComparisonMode> = comparisonModeState
        override val hasSeenInfo: Flow<Boolean> = hasSeenInfoState

        override suspend fun setForecastDays(days: Int) {
            setForecastCalls += 1
            state.value = days
        }

        override suspend fun setHistoricReference(reference: HistoricReference) {
            referenceState.value = reference
        }

        override suspend fun setSearchLanguage(language: String) {
            languageState.value = language
        }

        override suspend fun setTemperatureComparisonMode(mode: TemperatureComparisonMode) {
            comparisonModeState.value = mode
        }

        override suspend fun setHasSeenInfo(seen: Boolean) {
            hasSeenInfoState.value = seen
        }
    }

}
