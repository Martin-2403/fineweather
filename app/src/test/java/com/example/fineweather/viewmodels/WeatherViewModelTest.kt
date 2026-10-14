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
import com.example.fineweather.domain.ForecastOutlook
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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

    // ---- fetching ----

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_publishesMonthToDateAgainstTheSameDaysOfTheReference() = runTest {
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("10.00°C", viewModel.resultCurrentAverage.value)
        assertEquals("10.00°C", viewModel.resultCurrentMonthAverage.value)
        assertEquals(1, viewModel.currentMonthDayCount.value)
        assertEquals("4.00°C", viewModel.resultHistoricAverage.value)
        assertEquals(0.0, viewModel.historicMonthlyStdDev.value!!, 1e-9)
        assertEquals("12.00°C", viewModel.resultForecastAverage.value)
        assertEquals("Berlin", viewModel.resolvedLocationName.value)
        assertNotNull(viewModel.climate.value)
        assertEquals(fixedNow, viewModel.climate.value!!.today)
        assertEquals("Berlin, Germany (1.00, 2.00)", viewModel.status.value)
        assertEquals(PlaceLabel("Berlin, Germany", "1.00, 2.00"), viewModel.placeLabel.value)
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

        assertEquals("12.00°C", viewModel.resultForecastAverage.value)
        assertEquals(1, viewModel.apiCallCount.value) // only the year series
        assertEquals(2, viewModel.cacheHitCount.value) // reference and forecast
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_averagesTheFirstSevenOfTheStoredFourteenDays() = runTest {
        stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("12.00°C", viewModel.resultForecastAverage.value)
        val outlook = viewModel.forecastOutlook.value!!
        assertEquals(7, outlook.days)
        assertEquals(12.0, outlook.mean, 1e-9)
        // reference days Apr 2..8 are 4.0 in every year
        assertEquals(4.0, outlook.referenceMean!!, 1e-9)
        assertEquals(8.0, outlook.delta!!, 1e-9)
        assertEquals(forecastSeries.start, viewModel.climate.value!!.forecast!!.start)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesConfiguredForecastDays() = runTest {
        val settings = FakeSettingsRepository(initialDays = WeatherViewModel.FORECAST_DAYS_LONG)
        viewModel = newViewModel(settings = settings)
        val data = stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("13.50°C", viewModel.resultForecastAverage.value)
        assertEquals(14, viewModel.forecastOutlook.value!!.days)
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

        assertEquals("N/A", viewModel.resultCurrentMonthAverage.value)
        assertEquals(0, viewModel.currentMonthDayCount.value)
        assertEquals("4.00°C", viewModel.resultHistoricAverage.value)
        assertEquals("10.00°C", viewModel.resultCurrentAverage.value)
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

        assertEquals("No data yet", viewModel.resultCurrentMonthAverage.value)
        assertEquals(0, viewModel.currentMonthDayCount.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesCurrentFetchFailure() = runTest {
        stubSearch()
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultCurrentAverage.value)
        assertEquals("Error", viewModel.resultCurrentMonthAverage.value)
        assertEquals(0, viewModel.currentMonthDayCount.value)
        assertNull(viewModel.climate.value)
        assertEquals("4.00°C", viewModel.resultHistoricAverage.value)
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

        assertEquals("Error", viewModel.resultHistoricAverage.value)
        assertNull(viewModel.historicMonthlyStdDev.value)
        assertEquals("10.00°C", viewModel.resultCurrentMonthAverage.value)
        assertNotNull(viewModel.climate.value)
        assertNull(viewModel.climate.value!!.reference)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesForecastFetchFailure() = runTest {
        stubSearch()
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultForecastAverage.value)
        assertNull(viewModel.forecastOutlook.value)
        assertNull(viewModel.climate.value!!.forecast)
        assertEquals("10.00°C", viewModel.resultCurrentMonthAverage.value)
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
        assertEquals("Error", viewModel.resultCurrentMonthAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_filtersBlankLocationParts() = runTest {
        stubSearch(data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", ""))

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertTrue(viewModel.status.value.startsWith("Berlin "))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_resetsStateWhenLocationIsBlank() = runTest {
        stubSearch()
        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        assertNotNull(viewModel.placeLabel.value)

        viewModel.fetchWeather("   ")
        advanceUntilIdle()

        assertEquals("Enter a location to see temperature averages", viewModel.status.value)
        assertNull(viewModel.placeLabel.value)
        assertEquals("-", viewModel.resultCurrentAverage.value)
        assertEquals("-", viewModel.resultCurrentMonthAverage.value)
        assertEquals("-", viewModel.resultForecastAverage.value)
        assertEquals("-", viewModel.resultHistoricAverage.value)
        assertNull(viewModel.climate.value)
        coVerify(exactly = 1) { geoCodeRepository.getGeoCode(any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailure() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("City not found", viewModel.status.value)
        assertNull(viewModel.placeLabel.value)
        assertEquals("N/A", viewModel.resultCurrentAverage.value)
        assertEquals("N/A", viewModel.resultForecastAverage.value)
        assertEquals("N/A", viewModel.resultHistoricAverage.value)
        assertNull(viewModel.climate.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_handlesGeoCodeFailureWithNullMessage() = runTest {
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } throws RuntimeException()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("City not found", viewModel.status.value)
    }

    // ---- settings ----

    @Test
    fun exposesForecastAndHistoricStateFlows() {
        assertEquals(WeatherViewModel.FORECAST_DAYS_SHORT, viewModel.forecastDays.value)
        assertEquals(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE, viewModel.historicReference.value)
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
        assertEquals("4.00°C", viewModel.resultHistoricAverage.value)

        settings.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals("6.00°C", viewModel.resultHistoricAverage.value)
        assertEquals(6.0, viewModel.forecastOutlook.value!!.referenceMean!!, 1e-9)
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate(data.id, 1.0, 2.0, HistoricReference.CURRENT, fixedNow)
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
        assertEquals("12.00°C", viewModel.resultForecastAverage.value)

        settings.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        assertEquals("13.50°C", viewModel.resultForecastAverage.value)
        assertEquals(14, viewModel.forecastOutlook.value!!.days)
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

        assertEquals(selected, viewModel.selectedPlace.value)
        assertEquals("Hamburg", viewModel.resolvedLocationName.value)
        assertEquals("Hamburg, Germany (3.00, 4.00)", viewModel.status.value)
        assertEquals(PlaceLabel("Hamburg, Germany", "3.00, 4.00"), viewModel.placeLabel.value)
        assertEquals("10.00°C", viewModel.resultCurrentMonthAverage.value)
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
