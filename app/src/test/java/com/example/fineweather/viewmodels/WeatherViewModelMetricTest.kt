package com.example.fineweather.viewmodels

import android.util.Log
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.GeocodeSelection
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.repositories.CacheResult
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.RateLimitException
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.metricSeriesOf
import com.example.fineweather.domain.syntheticReference
import com.example.fineweather.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The metric switch: which metric the report shows, and when the 30-year extras of sunshine and rain are loaded. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelMetricTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var geoCodeRepository: GeoCodeRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var viewModel: WeatherViewModel
    private val today = LocalDate.of(2026, 4, 2)

    /** The monotonic clock of the cool-down after a rate limit. */
    private var clockMs = 0L

    /** Complete days Jan 1..Apr 1 2026: 10 degrees, 5 h of sunshine, 1 mm of rain. */
    private val yearSeries = metricSeriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { 10.0 }
    private val forecastSeries = metricSeriesOf(today, today.plusDays(13)) { 12.0 }
    private val climate = syntheticReference { it.monthValue.toDouble() }

    /** The normal is 6 h of sunshine and 1.5 mm of rain a day. */
    private val extras: Map<Metric, ReferenceClimate> =
        mapOf(
            Metric.SUNSHINE to syntheticReference { 6.0 },
            Metric.PRECIPITATION to syntheticReference { 1.5 },
        )

    private val berlin = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
    private val hamburgPlace = GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        weatherRepository = mockk(relaxed = true)
        geoCodeRepository = mockk(relaxed = true)
        settingsRepository = FakeSettingsRepository()
        favoriteRepository = FakeFavoriteRepository()
        viewModel = newViewModel()
        stubSearch()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newViewModel() =
        WeatherViewModel(
            weatherRepository,
            geoCodeRepository,
            settingsRepository,
            favoriteRepository,
            nowProvider = { today },
            elapsedMillis = { clockMs },
        )

    private val state get() = viewModel.state.value
    private val report get() = viewModel.report.value

    private fun extrasCalls(count: Int) =
        coVerify(exactly = count) { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) }

    // ---- the selection itself ----

    @Test
    fun theMetricIsTemperatureAtFirstAndFollowsTheSelection() {
        assertEquals(Metric.TEMPERATURE, viewModel.metric.value)
        assertEquals(Metric.TEMPERATURE, report.metric)

        viewModel.selectMetric(Metric.SUNSHINE)

        assertEquals(Metric.SUNSHINE, viewModel.metric.value)
        assertEquals(Metric.SUNSHINE, report.metric)
    }

    @Test
    fun theSelectionSurvivesANewSearch() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.PRECIPITATION)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Metric.PRECIPITATION, viewModel.metric.value)
        assertEquals(Metric.PRECIPITATION, report.metric)
    }

    @Test
    fun selectingBeforeAnySearchRequestsNothingUntilAPlaceIsLoaded() = runTest {
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(0)
        assertEquals(Loadable.Idle, state.extras)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        extrasCalls(1)
        assertTrue(state.extras is Loadable.Ready)
    }

    @Test
    fun selectingWhileThePlaceIsStillBeingSearchedLeavesTheLoadToTheSearch() = runTest {
        val geocode = CompletableDeferred<GeocodeSelection>()
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } coAnswers { geocode.await() }
        viewModel.fetchWeather("Berlin")

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(0)

        geocode.complete(selectionOf(berlin))
        advanceUntilIdle()

        extrasCalls(1)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    // ---- temperature never asks for the extras ----

    @Test
    fun temperatureOnlyUseMakesNoExtrasRequest() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectPlace(hamburgPlace)
        advanceUntilIdle()
        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()
        viewModel.selectMetric(Metric.TEMPERATURE)
        advanceUntilIdle()

        extrasCalls(0)
        assertEquals(Loadable.Idle, state.extras)
    }

    @Test
    fun switchingBackToTemperatureRequestsNothingAndKeepsTheExtras() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        viewModel.selectMetric(Metric.TEMPERATURE)
        advanceUntilIdle()

        extrasCalls(1)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    // ---- loading them ----

    @Test
    fun selectingSunshineLoadsTheExtrasOfTheShownPlace() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getReferenceExtras("loc-1", 1.0, 2.0, HistoricReference.CLASSIC, today)
        }
        assertEquals(Loadable.Ready(extras), state.extras)
        // five hours of sunshine on the one complete day of April against a normal of six
        assertEquals(Loadable.Ready(5.0), report.rows[1].value)
        assertEquals(Loadable.Ready(6.0), report.rows[3].value)
        assertEquals(-1.0, (report.headline as Loadable.Ready).value.delta, 1e-9)
    }

    @Test
    fun rainIsReportedAsATotalAgainstTheNormalTotalOfTheSameDays() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(1.0), report.rows[1].value)
        assertEquals(Loadable.Ready(1.5), report.rows[3].value)
        assertEquals(Metric.PRECIPITATION, (report.headline as Loadable.Ready).value.metric)
    }

    @Test
    fun theExtrasShowAsLoadingUntilTheyArrive() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        viewModel.selectMetric(Metric.SUNSHINE)

        assertEquals(Loadable.Loading, state.extras)
        assertEquals(Loadable.Loading, report.rows[3].value)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        // what does not need the normal is there already, and the picture stays the temperature's
        assertEquals(Loadable.Ready(5.0), report.rows[1].value)
        assertEquals(StatusPicture.WARM, report.picture)

        gate.complete(CacheResult(extras, fromCache = false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(6.0), report.rows[3].value)
    }

    @Test
    fun theHeaderFollowsTheMetricAndThePictureStaysTemperature() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val temperaturePicture = report.picture
        assertEquals(StatusPicture.WARM, temperaturePicture)

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        // 1 h/day less sunshine than normal, but 6 degrees warmer: the text says dull, the picture warm
        assertEquals(Band.COLD, (report.headline as Loadable.Ready).value.band)
        assertEquals(temperaturePicture, report.picture)
        assertEquals(Metric.SUNSHINE, (report.trend as Loadable.Ready).value.metric)
    }

    @Test
    fun theExtrasWaitForTheReferenceOfTheSamePlace() = runTest {
        val reference = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } coAnswers {
            reference.await()
        }
        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        // everything else is in, the reference is not: nothing is requested for the extras yet
        assertEquals(Loadable.Loading, state.extras)
        extrasCalls(0)

        reference.complete(CacheResult(climate, fromCache = false))
        advanceUntilIdle()

        extrasCalls(1)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun theExtrasOfAFailedReferenceFailTheSameWayWithoutARequest() = runTest {
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } throws
            RuntimeException("boom")
        viewModel.selectMetric(Metric.SUNSHINE)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.OTHER), state.reference)
        assertEquals(Loadable.Failed(FailKind.OTHER), state.extras)
        extrasCalls(0)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.headline)
        // the failed extras are no request: only the three slices were counted
        assertEquals(3, viewModel.apiCallCount.value)

        // tapping again cannot help while the reference itself is missing
        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()
        extrasCalls(0)
    }

    @Test
    fun aRateLimitedReferenceRateLimitsTheExtrasWithoutAnotherSnackbar() = runTest {
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } throws
            RateLimitException()
        val messages = collectSnackbars()
        viewModel.selectMetric(Metric.PRECIPITATION)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
        assertEquals(1, messages.size)
        extrasCalls(0)
    }

    // ---- selecting again ----

    @Test
    fun selectingAgainWhileLoadingOrReadyDoesNotRequestAgain() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()
        extrasCalls(1)
        assertEquals(Loadable.Loading, state.extras)

        gate.complete(CacheResult(extras, fromCache = false))
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()

        extrasCalls(1)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun selectingAgainAfterAFailureIsARetry() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws
            RuntimeException("boom")
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        assertEquals(Loadable.Failed(FailKind.OTHER), state.extras)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.headline)

        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } returns
            CacheResult(extras, fromCache = false)
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(extras), state.extras)
        extrasCalls(2)
    }

    // ---- new place, new search, new reference ----

    @Test
    fun aNewSearchDropsTheExtrasAtOnceAndLoadsThemForTheNewPlace() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        val geocode = CompletableDeferred<GeocodeSelection>()
        coEvery { geoCodeRepository.getGeoCode("Hamburg", any()) } coAnswers { geocode.await() }

        viewModel.fetchWeather("Hamburg")

        assertEquals(Loadable.Idle, state.extras)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.rows[3].value)
        geocode.complete(selectionOf(hamburg))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getReferenceExtras("loc-2", 3.0, 4.0, HistoricReference.CLASSIC, today)
        }
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun selectingAPlaceLoadsTheExtrasOfThatPlaceForTheSelectedMetric() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()

        viewModel.selectPlace(hamburgPlace)
        advanceUntilIdle()

        extrasCalls(2)
        coVerify(exactly = 1) { weatherRepository.getReferenceExtras("loc-2", 3.0, 4.0, any(), any()) }
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun aNewSearchUnderTemperatureLeavesTheExtrasUnrequestedUntilSunIsSelected() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        viewModel.selectMetric(Metric.TEMPERATURE)

        viewModel.selectPlace(hamburgPlace)
        advanceUntilIdle()
        assertEquals(Loadable.Idle, state.extras)
        extrasCalls(1)

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(2)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun aChangedHistoricReferenceLoadsTheExtrasAgainAfterTheNewReference() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        val reference = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } coAnswers { reference.await() }
        val newExtras = mapOf(Metric.SUNSHINE to syntheticReference(1991, 2020) { 7.0 })
        coEvery {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(newExtras, fromCache = false)

        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        // dropped with the old reference, and waiting for the new one
        assertEquals(Loadable.Loading, state.extras)
        coVerify(exactly = 0) {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        }

        reference.complete(CacheResult(syntheticReference(1991, 2020) { 6.0 }, fromCache = false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(newExtras), state.extras)
        assertEquals(Loadable.Ready(7.0), report.rows[3].value)
        // the forecast and the year were not requested again
        coVerify(exactly = 1) { weatherRepository.getForecast(any(), any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getYearToDate(any(), any(), any(), any()) }
    }

    @Test
    fun aChangedHistoricReferenceDropsTheExtrasWhenTemperatureIsShown() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        viewModel.selectMetric(Metric.TEMPERATURE)

        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals(Loadable.Idle, state.extras)
        extrasCalls(1)

        // and sunshine asks for the new period's extras when it is selected
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        }
    }

    @Test
    fun aChangedHistoricReferenceReplacesTheExtrasStillLoading() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val classic = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
        coEvery {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CLASSIC, any())
        } coAnswers { classic.await() }
        val newExtras = mapOf(Metric.SUNSHINE to syntheticReference(1991, 2020) { 7.0 })
        coEvery {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(newExtras, fromCache = false)
        viewModel.selectMetric(Metric.SUNSHINE)

        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()
        classic.complete(CacheResult(extras, fromCache = false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(newExtras), state.extras)
    }

    @Test
    fun theForecastSettingOnlyRecomputesTheReport() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        settingsRepository.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        extrasCalls(1)
        assertEquals(14, (report.trend as Loadable.Ready).value.days)
    }

    @Test
    fun selectingSunThenTempThenSunWhileLoadingIssuesOneRequest() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.selectMetric(Metric.TEMPERATURE)
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(1)
        assertEquals(Loadable.Loading, state.extras)

        gate.complete(CacheResult(extras, fromCache = false))
        advanceUntilIdle()
        extrasCalls(1)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun aReferenceChangeWhileTheExtrasWaitForTheReferenceRestartsThem() = runTest {
        val classic = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CLASSIC, any())
        } coAnswers { classic.await() }
        val currentExtras = mapOf(Metric.SUNSHINE to syntheticReference(1991, 2020) { 7.0 })
        coEvery {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(currentExtras, fromCache = false)
        viewModel.selectMetric(Metric.SUNSHINE)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(Loadable.Loading, state.extras)

        // the setting changes while both the reference and the extras wait for the old reference
        settingsRepository.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()
        classic.complete(CacheResult(climate, fromCache = false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(currentExtras), state.extras)
        assertEquals(Loadable.Ready(7.0), report.rows[3].value)
        extrasCalls(1)
        coVerify(exactly = 1) {
            weatherRepository.getReferenceExtras(any(), any(), any(), HistoricReference.CURRENT, any())
        }
    }

    @Test
    fun aSearchWithoutAResultUnderSunshineIsNotLeftLoading() = runTest {
        viewModel.selectMetric(Metric.SUNSHINE)
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Nowhereville")
        advanceUntilIdle()

        assertEquals(SearchStatus.NotFound("Nowhereville"), state.search)
        assertEquals(Loadable.Idle, state.extras)
        listOf(report.headline, report.trend, report.snapshot, report.summary).forEach {
            assertTrue("$it", it !is Loadable.Loading)
        }
        report.rows.forEach { assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), it.value) }
        assertEquals(StatusPicture.INITIAL, report.picture)
        extrasCalls(0)
    }

    // ---- the cool-down after a rate limit ----

    private fun TestScope.failExtrasWithRateLimit() {
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws RateLimitException()
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
    }

    @Test
    fun aRetryAfterARateLimitWaitsForTheCooldown() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        clockMs = 5_000
        failExtrasWithRateLimit()
        extrasCalls(1)

        // every tap inside the minute is a no-op
        clockMs = 5_000 + WeatherViewModel.EXTRAS_RATE_LIMIT_COOLDOWN_MS - 1
        viewModel.selectMetric(Metric.PRECIPITATION)
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(1)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
        assertEquals(Metric.SUNSHINE, viewModel.metric.value)

        // at the minute it retries
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } returns
            CacheResult(extras, fromCache = false)
        clockMs = 5_000 + WeatherViewModel.EXTRAS_RATE_LIMIT_COOLDOWN_MS
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(2)
        assertEquals(Loadable.Ready(extras), state.extras)
    }

    @Test
    fun aRateLimitedRetryStartsTheCooldownAgain() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        failExtrasWithRateLimit()
        clockMs = WeatherViewModel.EXTRAS_RATE_LIMIT_COOLDOWN_MS
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(2)

        clockMs += WeatherViewModel.EXTRAS_RATE_LIMIT_COOLDOWN_MS - 1
        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        extrasCalls(2)
    }

    @Test
    fun otherFailuresRetryAtOnce() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        listOf(RuntimeException("boom"), java.io.IOException("offline")).forEachIndexed { index, failure ->
            coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws failure
            viewModel.selectMetric(Metric.SUNSHINE)
            advanceUntilIdle()
            assertTrue(state.extras is Loadable.Failed)
            viewModel.selectMetric(Metric.SUNSHINE)
            advanceUntilIdle()
            extrasCalls(2 * (index + 1))
        }
    }

    @Test
    fun theCooldownDoesNotCarryOverToANewSearch() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        failExtrasWithRateLimit()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } returns
            CacheResult(extras, fromCache = false)

        viewModel.selectPlace(hamburgPlace)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(extras), state.extras)
        extrasCalls(2)
    }

    // ---- counters ----

    @Test
    fun theExtrasAreCountedAsAnApiCallOrACacheHit() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        // forecast, year and reference came from the network
        assertEquals(3, viewModel.apiCallCount.value)
        assertEquals(0, viewModel.cacheHitCount.value)

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()
        assertEquals(4, viewModel.apiCallCount.value)

        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } returns
            CacheResult(extras, fromCache = true)
        viewModel.selectPlace(hamburgPlace)
        advanceUntilIdle()

        assertEquals(7, viewModel.apiCallCount.value)
        assertEquals(1, viewModel.cacheHitCount.value)
    }

    @Test
    fun aFailedExtrasRequestCountsAsAnApiCall() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws
            RuntimeException("boom")

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        assertEquals(4, viewModel.apiCallCount.value)
    }

    // ---- rate limit ----

    @Test
    fun aRateLimitedExtrasRequestFailsAndShowsTheSnackbarOnlyOncePerSearch() = runTest {
        val messages = collectSnackbars()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws
            RateLimitException()

        viewModel.selectMetric(Metric.SUNSHINE)
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
        assertEquals(1, messages.size)

        // a retry in the same search, once the cool-down is over, runs into the limit again and stays quiet
        clockMs += WeatherViewModel.EXTRAS_RATE_LIMIT_COOLDOWN_MS
        viewModel.selectMetric(Metric.PRECIPITATION)
        advanceUntilIdle()
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
        assertEquals(1, messages.size)

        // a new search may speak up again
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(2, messages.size)
    }

    @Test
    fun theLimitHitByAnotherSliceAndTheExtrasShowsOneSnackbar() = runTest {
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RateLimitException()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } throws
            RateLimitException()
        val messages = collectSnackbars()
        viewModel.selectMetric(Metric.SUNSHINE)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.forecast)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.extras)
        assertEquals(1, messages.size)
    }

    // ---- superseded searches (a standard main dispatcher: a cancelled job runs its handlers late) ----

    private fun useStandardMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = newViewModel()
    }

    @Test
    fun theExtrasOfASupersededSearchAreNotApplied() {
        useStandardMainDispatcher()
        runTest {
            stubHamburg()
            val gate = CompletableDeferred<Unit>()
            // cannot be cancelled and still returns after the next search began
            coEvery { weatherRepository.getReferenceExtras("loc-1", any(), any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                CacheResult(mapOf(Metric.SUNSHINE to syntheticReference { 99.0 }), fromCache = false)
            }
            val messages = collectSnackbars()
            viewModel.selectMetric(Metric.SUNSHINE)
            viewModel.fetchWeather("Berlin")
            runCurrent()
            viewModel.fetchWeather("Hamburg")
            runCurrent()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(hamburgPlace, state.selectedPlace)
            assertEquals(Loadable.Ready(extras), state.extras)
            assertEquals(Loadable.Ready(6.0), report.rows[3].value)
            assertEquals(emptyList<String>(), messages)
        }
    }

    @Test
    fun aRateLimitOfASupersededExtrasRequestShowsNoSnackbar() {
        useStandardMainDispatcher()
        runTest {
            stubHamburg()
            val gate = CompletableDeferred<Unit>()
            coEvery { weatherRepository.getReferenceExtras("loc-1", any(), any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                throw RateLimitException()
            }
            val messages = collectSnackbars()
            viewModel.selectMetric(Metric.SUNSHINE)
            viewModel.fetchWeather("Berlin")
            runCurrent()
            viewModel.fetchWeather("Hamburg")
            runCurrent()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(Loadable.Ready(extras), state.extras)
            assertEquals(emptyList<String>(), messages)
        }
    }

    @Test
    fun aWrappedCancellationOfASupersededExtrasRequestIsNeitherAFailureNorCounted() {
        useStandardMainDispatcher()
        runTest {
            stubHamburg()
            val berlinExtras = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
            coEvery { weatherRepository.getReferenceExtras("loc-1", any(), any(), any(), any()) } coAnswers {
                try {
                    berlinExtras.await()
                } catch (cancellation: CancellationException) {
                    throw Exception("Failed to fetch weather data", cancellation)
                }
            }
            viewModel.selectMetric(Metric.SUNSHINE)
            viewModel.fetchWeather("Berlin")
            runCurrent()

            viewModel.fetchWeather("Hamburg")
            advanceUntilIdle()

            assertEquals(Loadable.Ready(extras), state.extras)
            // Berlin's three slices and Hamburg's four; Berlin's cancelled extras are not counted
            assertEquals(7, viewModel.apiCallCount.value)
        }
    }

    @Test
    fun aSupersededExtrasRequestIsCancelledAndNotCountedWhenTheNewSearchIsBlank() = runTest {
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<Map<Metric, ReferenceClimate>>>()
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.selectMetric(Metric.SUNSHINE)

        viewModel.fetchWeather("")
        gate.complete(CacheResult(extras, fromCache = false))
        advanceUntilIdle()

        assertEquals(Loadable.Idle, state.extras)
        assertEquals(3, viewModel.apiCallCount.value)
        assertEquals(Metric.SUNSHINE, viewModel.metric.value)
    }

    // ---- helpers ----

    private val hamburg = FineWeatherData("Hamburg", Pair(3.0, 4.0), "loc-2", "Germany")

    private fun TestScope.collectSnackbars(): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.snackbarMessages.toList(messages)
        }
        return messages
    }

    private fun selectionOf(data: FineWeatherData): GeocodeSelection =
        GeocodeSelection(
            primary = GeoPlace(data.id, data.location, data.country, null, data.coordinates.first, data.coordinates.second),
            candidates = emptyList(),
        )

    private fun stubHamburg() {
        coEvery { geoCodeRepository.getGeoCode("Hamburg", any()) } returns selectionOf(hamburg)
    }

    /** Every request succeeds, extras included. */
    private fun stubSearch(
        forecast: MetricSeries = forecastSeries,
        year: MetricSeries = yearSeries,
    ) {
        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns selectionOf(berlin)
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } returns CacheResult(forecast, false)
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } returns CacheResult(year, false)
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } returns
            CacheResult(climate, false)
        coEvery { weatherRepository.getReferenceExtras(any(), any(), any(), any(), any()) } returns
            CacheResult(extras, false)
    }
}
