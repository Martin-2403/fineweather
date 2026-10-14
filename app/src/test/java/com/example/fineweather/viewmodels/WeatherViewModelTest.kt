package com.example.fineweather.viewmodels

import android.util.Log
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.GeocodeSelection
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.CacheResult
import com.example.fineweather.data.repositories.FavoriteAddResult
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.RateLimitException
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.seriesOf
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class WeatherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var geoCodeRepository: GeoCodeRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var viewModel: WeatherViewModel
    private val fixedNow = LocalDate.of(2026, 4, 2)

    /** Complete days Jan 1..Apr 1 2026, all 10.0. */
    private val yearSeries = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { 10.0 }

    /** 14 forecast days from today: 12.0 for the first week, 15.0 for the second. */
    private val forecastSeries =
        seriesOf(fixedNow, fixedNow.plusDays(13)) { date -> if (date.isBefore(fixedNow.plusDays(7))) 12.0 else 15.0 }

    /** Every day of month m is worth m in all reference years (April: 4.0, std 0). */
    private val monthlyClimate = syntheticReference { it.monthValue.toDouble() }

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
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newViewModel(
        settings: SettingsRepository = settingsRepository,
        favorites: FavoriteRepository = favoriteRepository,
        now: LocalDate = fixedNow,
    ) = WeatherViewModel(
        weatherRepository,
        geoCodeRepository,
        settings,
        favorites,
        nowProvider = { now },
    )

    private val state get() = viewModel.state.value
    private val report get() = viewModel.report.value

    private fun row(index: Int) = report.rows[index].value

    // ---- fetching ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_publishesMonthToDateAgainstTheSameDaysOfTheReference() = runTest {
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Ready(10.0), row(0))
        assertEquals(Loadable.Ready(10.0), row(1))
        assertEquals(1, report.dayCount)
        assertEquals(Loadable.Ready(4.0), row(3))
        assertEquals(Loadable.Ready(12.0), row(2))
        assertEquals(0.0, (report.headline as Loadable.Ready).value.stdDev!!, 1e-9)
        assertEquals("Berlin", state.selectedPlace?.name)
        val snapshot = (report.snapshot as Loadable.Ready).value
        assertEquals(fixedNow, snapshot.today)
        assertEquals(fixedNow, report.today)
        assertEquals(
            SearchStatus.Resolved(PlaceLabel("Berlin, Germany", "1.00, 2.00")),
            state.search,
        )
        coVerify(exactly = 1) {
            weatherRepository.getYearToDate(data.id, 1.0, 2.0, fixedNow)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_countsApiCallsAndCacheHits() = runTest {
        stubSearch(seriesFromCache = false, climateFromCache = true)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        // year series + forecast from the network, reference from the cache
        assertEquals(2, viewModel.apiCallCount.value)
        assertEquals(1, viewModel.cacheHitCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_countsTheForecastAsACacheHitWhenItIsStored() = runTest {
        stubSearch(climateFromCache = true, forecastFromCache = true)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Ready(12.0), row(2))
        assertEquals(1, viewModel.apiCallCount.value) // only the year series
        assertEquals(2, viewModel.cacheHitCount.value) // reference and forecast
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_averagesTheFirstSevenOfTheStoredFourteenDays() = runTest {
        stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Ready(12.0), row(2))
        val outlook = (report.trend as Loadable.Ready).value
        assertEquals(7, outlook.days)
        assertEquals(12.0, outlook.mean, 1e-9)
        // reference days Apr 2..8 are 4.0 in every year
        assertEquals(4.0, outlook.referenceMean!!, 1e-9)
        assertEquals(8.0, outlook.delta!!, 1e-9)
        assertEquals(forecastSeries.start, (report.snapshot as Loadable.Ready).value.forecast!!.start)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesConfiguredForecastDays() = runTest {
        val settings = FakeSettingsRepository(initialDays = WeatherViewModel.FORECAST_DAYS_LONG)
        viewModel = newViewModel(settings = settings)
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Ready(13.5), row(2))
        assertEquals(14, (report.trend as Loadable.Ready).value.days)
        coVerify(exactly = 1) { weatherRepository.getForecast(data.id, 1.0, 2.0, fixedNow) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_requestsTheForecastOnce() = runTest {
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) { weatherRepository.getForecast(data.id, 1.0, 2.0, fixedNow) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_onFirstDayOfMonthShowsNotAvailableAndFallsBackToTheWholeMonthReference() = runTest {
        val firstOfApril = LocalDate.of(2026, 4, 1)
        viewModel = newViewModel(now = firstOfApril)
        stubSearch(
            series = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)) { 10.0 },
        )

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), row(1))
        assertEquals(0, report.dayCount)
        assertEquals(Loadable.Ready(4.0), row(3))
        assertEquals(Loadable.Ready(10.0), row(0))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_showsNoDataYetWhenTheCurrentMonthHasNoValues() = runTest {
        stubSearch(
            series = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { date ->
                if (date.monthValue == 4) null else 10.0
            },
        )

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Unavailable(Reason.NO_DATA_YET), row(1))
        assertEquals(0, report.dayCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesCurrentFetchFailure() = runTest {
        stubSearch()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.OTHER), state.year)
        assertEquals(Loadable.Failed(FailKind.OTHER), row(0))
        assertEquals(Loadable.Failed(FailKind.OTHER), row(1))
        assertEquals(0, report.dayCount)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.snapshot)
        assertEquals(Loadable.Ready(4.0), row(3))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesHistoricFetchFailure() = runTest {
        stubSearch()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), any(), any())
        } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.OTHER), row(3))
        assertEquals(Loadable.Failed(FailKind.OTHER), report.headline)
        assertEquals(Loadable.Ready(10.0), row(1))
        assertNull((report.snapshot as Loadable.Ready).value.reference)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesForecastFetchFailure() = runTest {
        stubSearch()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.OTHER), row(2))
        assertEquals(Loadable.Failed(FailKind.OTHER), report.trend)
        assertNull((report.snapshot as Loadable.Ready).value.forecast)
        assertEquals(Loadable.Ready(10.0), row(1))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_classifiesNetworkFailures() = runTest {
        stubSearch()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws
            Exception("Failed to fetch weather data", IOException("offline"))

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Failed(FailKind.NETWORK), state.year)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_tellsTheUserWhenTheRateLimitIsHit() = runTest {
        stubSearch()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws RateLimitException()
        val message = async(start = CoroutineStart.UNDISPATCHED) { viewModel.snackbarMessages.first() }

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(
            "Weather service limit reached. Showing cached data where available.",
            message.await(),
        )
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), row(1))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_showsTheRateLimitSnackbarOnlyOncePerSearch() = runTest {
        stubSearch()
        val limit = RateLimitException()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws limit
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws limit
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } throws limit
        val messages = collectSnackbars()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(1, messages.size)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.forecast)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.year)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.reference)
        assertEquals(3, viewModel.apiCallCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_finishesLoadingWhileASnackbarIsStillShowing() = runTest {
        stubSearch()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws RateLimitException()
        // a collector that never finishes its first message, like showSnackbar during its 4 seconds
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.snackbarMessages.collect { awaitCancellation() }
        }

        repeat(3) {
            viewModel.fetchWeather("Berlin")
            advanceUntilIdle()

            assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.year)
            assertEquals(Loadable.Ready(12.0), row(2))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_filtersBlankLocationParts() = runTest {
        stubSearch(data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", ""))

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Berlin", (state.search as SearchStatus.Resolved).label.name)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_resetsStateWhenLocationIsBlank() = runTest {
        stubSearch()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertTrue(state.search is SearchStatus.Resolved)

        viewModel.fetchWeather("   ")
        advanceUntilIdle()

        assertEquals(SearchStatus.Idle, state.search)
        assertNull(state.selectedPlace)
        assertTrue(state.places.isEmpty())
        assertEquals(Loadable.Idle, state.forecast)
        assertEquals(Loadable.Idle, state.year)
        assertEquals(Loadable.Idle, state.reference)
        report.rows.forEach { assertEquals(Loadable.Idle, it.value) }
        assertEquals(Loadable.Idle, report.snapshot)
        assertEquals(StatusPicture.INITIAL, report.picture)
        coVerify(exactly = 1) { geoCodeRepository.getGeoCode(any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailure() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(SearchStatus.NotFound("Berlin"), state.search)
        assertNull(state.selectedPlace)
        report.rows.forEach { assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), it.value) }
        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.snapshot)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailureWithNullMessage() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertTrue(state.search is SearchStatus.NotFound)
    }

    // ---- loading state ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_clearsThePreviousPlaceAsSoonAsASearchStarts() = runTest {
        stubSearch()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertTrue(report.snapshot is Loadable.Ready)
        val geocode = CompletableDeferred<GeocodeSelection>()
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } coAnswers { geocode.await() }

        viewModel.fetchWeather("Hamburg")

        assertEquals(SearchStatus.Searching("Hamburg"), state.search)
        assertEquals(Loadable.Loading, state.forecast)
        assertEquals(Loadable.Loading, state.year)
        assertEquals(Loadable.Loading, state.reference)
        report.rows.forEach { assertEquals(Loadable.Loading, it.value) }
        assertEquals(Loadable.Loading, report.snapshot)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(StatusPicture.LOADING, report.picture)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectPlace_clearsThePreviousPlaceAsSoonAsItIsSelected() = runTest {
        stubSearch()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<DailySeries>>()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } coAnswers { gate.await() }

        viewModel.selectPlace(GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0))

        assertEquals(Loadable.Loading, report.snapshot)
        assertEquals(Loadable.Loading, row(1))
        assertEquals("Hamburg", state.selectedPlace?.name)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_loadsForecastYearAndReferenceInParallel() = runTest {
        stubSearch()
        val forecast = CompletableDeferred<CacheResult<DailySeries>>()
        val year = CompletableDeferred<CacheResult<DailySeries>>()
        val reference = CompletableDeferred<CacheResult<ReferenceClimate>>()
        var started = 0
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } coAnswers {
            started++
            forecast.await()
        }
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } coAnswers {
            started++
            year.await()
        }
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } coAnswers {
            started++
            reference.await()
        }

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        // all three requests are on their way before any of them finished
        assertEquals(3, started)
        assertEquals(Loadable.Loading, state.forecast)

        reference.complete(CacheResult(monthlyClimate, false))
        advanceUntilIdle()
        assertTrue(state.reference is Loadable.Ready)
        assertEquals(Loadable.Loading, state.year)
        // the reference alone does not complete the month, so the headline waits for the year
        assertEquals(Loadable.Loading, report.headline)

        year.complete(CacheResult(yearSeries, false))
        forecast.complete(CacheResult(forecastSeries, false))
        advanceUntilIdle()
        assertEquals(Loadable.Ready(12.0), row(2))
        assertTrue(report.snapshot is Loadable.Ready)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_oneFailingSliceDoesNotAffectTheOthers() = runTest {
        stubSearch()
        val forecast = CompletableDeferred<CacheResult<DailySeries>>()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } coAnswers { forecast.await() }
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } throws
            RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        // the failure came while the forecast was still on its way
        assertEquals(Loadable.Failed(FailKind.OTHER), state.reference)
        assertEquals(Loadable.Loading, state.forecast)
        assertEquals(Loadable.Ready(yearSeries), state.year)

        forecast.complete(CacheResult(forecastSeries, false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(forecastSeries), state.forecast)
        assertEquals(Loadable.Failed(FailKind.OTHER), state.reference)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_aNewSearchCancelsThePreviousOne() = runTest {
        val berlin = stubSearch()
        val hamburg = FineWeatherData("Hamburg", Pair(3.0, 4.0), "loc-2", "Germany")
        val berlinGate = CompletableDeferred<Unit>()
        coEvery { geoCodeRepository.getGeoCode("Hamburg", any()) } returns buildSelectionFromData(hamburg)
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } coAnswers {
            if (firstArg<String>() == berlin.id) {
                berlinGate.await()
                CacheResult(seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { 99.0 }, false)
            } else {
                CacheResult(yearSeries, false)
            }
        }

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(Loadable.Loading, state.year)

        viewModel.fetchWeather("Hamburg")
        advanceUntilIdle()
        berlinGate.complete(Unit)
        advanceUntilIdle()

        // Berlin's late answer must not replace Hamburg's data
        assertEquals("Hamburg", state.selectedPlace?.name)
        assertEquals(Loadable.Ready(10.0), row(1))
        assertEquals(Loadable.Ready(yearSeries), state.year)
        // Berlin's cancelled year request is neither a failure nor a result and is not counted:
        // Berlin's other two finished before the second search, plus Hamburg's three
        assertEquals(5, viewModel.apiCallCount.value)
        assertEquals(0, viewModel.cacheHitCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectPlace_cancelsTheSearchStillRunning() = runTest {
        stubSearch()
        val geocode = CompletableDeferred<GeocodeSelection>()
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } coAnswers { geocode.await() }
        viewModel.fetchWeather("Berlin")

        viewModel.selectPlace(GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0))
        advanceUntilIdle()
        geocode.complete(buildSelectionFromData(FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")))
        advanceUntilIdle()

        assertEquals("Hamburg", state.selectedPlace?.name)
        assertEquals(Loadable.Ready(yearSeries), state.year)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_blankLocationCancelsTheRunningSearch() = runTest {
        stubSearch()
        val gate = CompletableDeferred<CacheResult<DailySeries>>()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } coAnswers { gate.await() }
        viewModel.fetchWeather("Berlin")

        viewModel.fetchWeather("")
        gate.complete(CacheResult(yearSeries, false))
        advanceUntilIdle()

        assertEquals(SearchStatus.Idle, state.search)
        assertEquals(Loadable.Idle, state.year)
        // the cancelled year request is not counted, the two that had finished are
        assertEquals(2, viewModel.apiCallCount.value)
    }

    // ---- settings ----

    @Test
    fun exposesForecastAndHistoricStateFlows() {
        assertEquals(WeatherViewModel.FORECAST_DAYS_SHORT, viewModel.forecastDays.value)
        assertEquals(
            SettingsDefaults.DEFAULT_HISTORIC_REFERENCE.displayLabel(fixedNow),
            report.referenceLabel,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_reloadTheReferenceWhenTheHistoricReferenceChanges() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        val data = stubSearch()
        val currentClimate = syntheticReference(1991, 2020) { 6.0 }
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(currentClimate, fromCache = false)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(Loadable.Ready(4.0), row(3))

        settings.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(6.0), row(3))
        assertEquals(6.0, (report.trend as Loadable.Ready).value.referenceMean!!, 1e-9)
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate(data.id, 1.0, 2.0, HistoricReference.CURRENT, fixedNow)
        }
        // nothing but the reference was requested again
        coVerify(exactly = 1) { weatherRepository.getForecast(data.id, 1.0, 2.0, fixedNow) }
        coVerify(exactly = 1) { weatherRepository.getYearToDate(data.id, 1.0, 2.0, fixedNow) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_clearTheReferenceWhileItReloads() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        val gate = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } coAnswers { gate.await() }

        settings.setHistoricReference(HistoricReference.CURRENT)

        assertEquals(Loadable.Loading, state.reference)
        assertEquals(Loadable.Loading, row(3))
        assertEquals(Loadable.Ready(10.0), row(1))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_reloadOnlyTheReferenceWhileOtherSlicesAreStillLoading() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()
        val forecast = CompletableDeferred<CacheResult<DailySeries>>()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } coAnswers { forecast.await() }
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(syntheticReference(1991, 2020) { 6.0 }, fromCache = false)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settings.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()
        // the forecast still runs, unharmed by the reference reload
        assertEquals(Loadable.Loading, state.forecast)
        forecast.complete(CacheResult(forecastSeries, false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(forecastSeries), state.forecast)
        assertEquals(Loadable.Ready(6.0), row(3))
        coVerify(exactly = 1) { weatherRepository.getForecast(any(), any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getYearToDate(any(), any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_aReferenceReloadReplacesTheOneStillRunning() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()
        val classic = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CLASSIC, any())
        } coAnswers { classic.await() }
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } returns CacheResult(syntheticReference(1991, 2020) { 6.0 }, fromCache = false)
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        settings.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()
        classic.complete(CacheResult(monthlyClimate, false))
        advanceUntilIdle()

        assertEquals(Loadable.Ready(6.0), row(3))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_ignoreAReferenceChangeWhileSearching() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()
        val geocode = CompletableDeferred<GeocodeSelection>()
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } coAnswers { geocode.await() }
        viewModel.fetchWeather("Berlin")

        settings.setHistoricReference(HistoricReference.CURRENT)
        geocode.complete(buildSelectionFromData(FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")))
        advanceUntilIdle()

        // the search carried on and used the new reference right away
        assertTrue(state.reference is Loadable.Ready)
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        }
        coVerify(exactly = 0) {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CLASSIC, any())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_recomputeTheForecastWithoutRequestingItAgain() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(Loadable.Ready(12.0), row(2))

        settings.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        assertEquals(Loadable.Ready(13.5), row(2))
        assertEquals(14, (report.trend as Loadable.Ready).value.days)
        coVerify(exactly = 1) { weatherRepository.getForecast(data.id, 1.0, 2.0, fixedNow) }
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
        viewModel = newViewModel(settings = countingRepository)

        viewModel.setForecastDays(WeatherViewModel.FORECAST_DAYS_SHORT)

        assertEquals(0, countingRepository.setForecastCalls)
    }

    // ---- favorites ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectPlace_loadsThatPlace() = runTest {
        val selected = GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0)
        stubSearch(data = FineWeatherData("Hamburg", Pair(3.0, 4.0), "loc-2", "Germany"))

        viewModel.selectPlace(selected)
        advanceUntilIdle()

        assertEquals(selected, state.selectedPlace)
        assertEquals(
            SearchStatus.Resolved(PlaceLabel("Hamburg, Germany", "3.00, 4.00")),
            state.search,
        )
        assertEquals(Loadable.Ready(10.0), row(1))
        coVerify(exactly = 1) { weatherRepository.getYearToDate("loc-2", 3.0, 4.0, fixedNow) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun toggleFavorite_addsSelectedPlaceToFavorites() = runTest {
        val selected = GeoPlace("loc-1", "Berlin", "Germany", null, 1.0, 2.0)
        stubSearch(data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany"))
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
        viewModel = newViewModel(favorites = favoriteRepository)
        val selected = GeoPlace("loc-11", "New City", "Country", null, 11.0, 11.0)
        stubSearch(data = FineWeatherData("New City", Pair(11.0, 11.0), "loc-11", "Country"))
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

    // ---- superseded searches (the main dispatcher is a standard one here, so a cancelled job
    // runs its handlers only after the new search has changed the state) ----

    private fun useStandardMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = newViewModel()
    }

    /** Like a layer that wraps whatever it catches, cancellation included. */
    private fun <T> CompletableDeferred<T>.awaitWrappingCancellation(): suspend () -> T = {
        try {
            await()
        } catch (cancellation: CancellationException) {
            throw Exception("Failed to fetch geocoding", cancellation)
        }
    }

    private val hamburgPlace = GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0)

    private fun stubHamburg() {
        coEvery { geoCodeRepository.getGeoCode("Hamburg", any()) } returns
            buildSelectionFromData(FineWeatherData("Hamburg", Pair(3.0, 4.0), "loc-2", "Germany"))
    }

    private fun assertHamburgIsShown() {
        assertEquals(hamburgPlace, state.selectedPlace)
        assertEquals(
            SearchStatus.Resolved(PlaceLabel("Hamburg, Germany", "3.00, 4.00")),
            state.search,
        )
        assertEquals(Loadable.Ready(yearSeries), state.year)
        assertEquals(Loadable.Ready(forecastSeries), state.forecast)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun selectPlace_aWrappedCancellationOfTheSupersededSearchDoesNotClearTheState() {
        useStandardMainDispatcher()
        runTest {
            stubSearch()
            val geocode = CompletableDeferred<GeocodeSelection>()
            val wrapped = geocode.awaitWrappingCancellation()
            coEvery { geoCodeRepository.getGeoCode("Berlin", any()) } coAnswers { wrapped() }
            viewModel.fetchWeather("Berlin")
            runCurrent()

            viewModel.selectPlace(hamburgPlace)
            advanceUntilIdle()

            assertHamburgIsShown()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_aWrappedCancellationOfTheSupersededSearchDoesNotClearTheState() {
        useStandardMainDispatcher()
        runTest {
            stubSearch()
            stubHamburg()
            val geocode = CompletableDeferred<GeocodeSelection>()
            val wrapped = geocode.awaitWrappingCancellation()
            coEvery { geoCodeRepository.getGeoCode("Berlin", any()) } coAnswers { wrapped() }
            viewModel.fetchWeather("Berlin")
            runCurrent()

            viewModel.fetchWeather("Hamburg")
            advanceUntilIdle()

            assertHamburgIsShown()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_aWrappedCancellationOfASupersededSliceIsNotAFailure() {
        useStandardMainDispatcher()
        runTest {
            stubSearch()
            stubHamburg()
            val berlinYear = CompletableDeferred<CacheResult<DailySeries>>()
            coEvery { weatherRepository.getYearToDate("loc-1", any(), any(), any()) } coAnswers {
                try {
                    berlinYear.await()
                } catch (cancellation: CancellationException) {
                    throw Exception("Failed to fetch weather data", cancellation)
                }
            }
            viewModel.fetchWeather("Berlin")
            runCurrent()

            viewModel.fetchWeather("Hamburg")
            advanceUntilIdle()

            assertHamburgIsShown()
            // Berlin's forecast and reference had finished; its cancelled year is not counted
            assertEquals(5, viewModel.apiCallCount.value)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_aResultOfASupersededSearchIsNotApplied() {
        useStandardMainDispatcher()
        runTest {
            stubSearch()
            stubHamburg()
            val gate = CompletableDeferred<Unit>()
            // a load that cannot be cancelled and still returns after the next search began
            coEvery { weatherRepository.getYearToDate("loc-1", any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                CacheResult(seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { 99.0 }, false)
            }
            viewModel.fetchWeather("Berlin")
            runCurrent()
            viewModel.fetchWeather("Hamburg")
            runCurrent()

            gate.complete(Unit)
            advanceUntilIdle()

            assertHamburgIsShown()
        }
    }

    // ---- rate limit snackbar ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_showsTheRateLimitSnackbarAsSoonAsItIsHitWithoutWaitingForTheOtherSlices() = runTest {
        stubSearch()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RateLimitException()
        val reference = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } coAnswers {
            reference.await()
        }
        val messages = collectSnackbars()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(Loadable.Loading, state.reference)
        assertEquals(1, messages.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_aReferenceReloadAfterARateLimitDoesNotShowTheSnackbarAgain() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RateLimitException()
        val classic = CompletableDeferred<CacheResult<ReferenceClimate>>()
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CLASSIC, any())
        } coAnswers { classic.await() }
        coEvery {
            weatherRepository.getReferenceClimate(any(), any(), any(), HistoricReference.CURRENT, any())
        } throws RateLimitException()
        val messages = collectSnackbars()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertEquals(1, messages.size)

        // the reference setting changes while the reference still loads, and runs into the limit too
        settings.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals(1, messages.size)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.reference)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), state.forecast)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_everySearchMayShowTheRateLimitSnackbarOnce() = runTest {
        stubSearch()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RateLimitException()
        val messages = collectSnackbars()

        viewModel.fetchWeather("Berlin")
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(2, messages.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_aSupersededSearchShowsNoRateLimitSnackbar() {
        useStandardMainDispatcher()
        runTest {
            stubSearch()
            stubHamburg()
            val gate = CompletableDeferred<Unit>()
            // keeps running after its cancellation and then hits the limit
            coEvery { weatherRepository.getYearToDate("loc-1", any(), any(), any()) } coAnswers {
                withContext(NonCancellable) { gate.await() }
                throw RateLimitException()
            }
            val messages = collectSnackbars()
            viewModel.fetchWeather("Berlin")
            runCurrent()
            viewModel.fetchWeather("Hamburg")
            runCurrent()

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(emptyList<String>(), messages)
            assertHamburgIsShown()
        }
    }

    // ---- factory ----

    @Test
    fun weatherViewModelFactory_createsViewModel() {
        val created = factory().create(WeatherViewModel::class.java)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_createsWithExtras() {
        val created = factory().create(WeatherViewModel::class.java, CreationExtras.Empty)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_createsWithKClassExtras() {
        val created = factory().create(WeatherViewModel::class, CreationExtras.Empty)

        assertEquals(WeatherViewModel::class.java, created::class.java)
    }

    @Test
    fun weatherViewModelFactory_throwsForUnknownClass() {
        assertThrows(IllegalArgumentException::class.java) {
            factory().create(SettingsViewModel::class.java)
        }
    }

    // ---- helpers ----

    private fun TestScope.collectSnackbars(): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.snackbarMessages.toList(messages)
        }
        return messages
    }

    private fun factory() =
        WeatherViewModelFactory(
            weatherRepository,
            geoCodeRepository,
            settingsRepository,
            favoriteRepository,
        )

    /**
     * Stubs a successful search for [data]: geocoding, the 14-day [forecast] and the given daily
     * [series] and reference [climate].
     */
    private fun stubSearch(
        data: FineWeatherData = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany"),
        series: DailySeries = yearSeries,
        climate: ReferenceClimate = monthlyClimate,
        forecast: DailySeries = forecastSeries,
        seriesFromCache: Boolean = false,
        climateFromCache: Boolean = false,
        forecastFromCache: Boolean = false,
    ): FineWeatherData {
        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } returns
            CacheResult(forecast, forecastFromCache)
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } returns
            CacheResult(series, seriesFromCache)
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } returns
            CacheResult(climate, climateFromCache)
        return data
    }

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
