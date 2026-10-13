package com.example.fineweather.viewmodels

import android.util.Log
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.fineweather.data.local.entities.WeatherEntity
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
import io.mockk.slot
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
        assertEquals("12.34°C", viewModel.resultForecastAverage.value)
        assertEquals("Berlin", viewModel.resolvedLocationName.value)
        assertNotNull(viewModel.climate.value)
        assertEquals(fixedNow, viewModel.climate.value!!.today)
        assertTrue(viewModel.status.value.startsWith("Berlin"))
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
    fun fetchWeather_usesCachedForecastWhenAvailable() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        stubSearch(
            data = data,
            cached = forecastEntity(data.id, data.timestamp, forecast = 12.0),
        )

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 0) { weatherRepository.getWeatherForecast(any(), any(), any()) }
        assertEquals("12.00°C", viewModel.resultForecastAverage.value)
        assertTrue(viewModel.cacheHitCount.value >= 1)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_ignoresCachedForecastWhenDaysMismatch() = runTest {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        // cached for 14 days, but the setting is 7
        stubSearch(
            data = data,
            cached = forecastEntity(
                data.id,
                data.timestamp,
                days = WeatherViewModel.FORECAST_DAYS_LONG,
                forecast = 15.0,
            ),
        )

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getWeatherForecast(1.0, 2.0, WeatherViewModel.FORECAST_DAYS_SHORT)
        }
        assertEquals("12.34°C", viewModel.resultForecastAverage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_usesConfiguredForecastDays() = runTest {
        val settings = FakeSettingsRepository(initialDays = WeatherViewModel.FORECAST_DAYS_LONG)
        viewModel = newViewModel(settings = settings)
        stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getWeatherForecast(1.0, 2.0, WeatherViewModel.FORECAST_DAYS_LONG)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun fetchWeather_persistsOnlyTheForecast() = runTest {
        val saved = slot<WeatherEntity>()
        val data = stubSearch()
        coEvery { weatherRepository.insertWeather(capture(saved)) } returns Unit

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals(data.id, saved.captured.id)
        assertEquals(12.34, saved.captured.forecastAverage7!!, 1e-9)
        assertEquals(data.timestamp, saved.captured.forecastDate7)
        assertNull(saved.captured.forecastAverage14)
        assertNull(saved.captured.currentAverage)
        assertNull(saved.captured.historicMonthlyAverageClassic)
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
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } throws RuntimeException("boom")

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()

        assertEquals("Error", viewModel.resultForecastAverage.value)
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

        viewModel.fetchWeather("   ")
        advanceUntilIdle()

        assertEquals("Enter a location to see temperature averages", viewModel.status.value)
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
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate(data.id, 1.0, 2.0, HistoricReference.CURRENT, fixedNow)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_useCachedForecastForTheNewForecastDays() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany")
        val cached =
            forecastEntity(data.id, data.timestamp, forecast = 12.0)
                .copy(forecastAverage14 = 15.0, forecastDate14 = data.timestamp)
        stubSearch(data = data, cached = cached)

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        settings.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        assertEquals("15.00°C", viewModel.resultForecastAverage.value)
        coVerify(exactly = 0) { weatherRepository.getWeatherForecast(any(), any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun settingsUpdates_fetchForecastWhenNotCachedForTheNewDays() = runTest {
        val settings = FakeSettingsRepository()
        viewModel = newViewModel(settings = settings)
        stubSearch()

        viewModel.fetchWeather("Berlin")
        advanceUntilIdle()
        settings.setForecastDays(WeatherViewModel.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            weatherRepository.getWeatherForecast(1.0, 2.0, WeatherViewModel.FORECAST_DAYS_LONG)
        }
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
     * Stubs a successful search for [data]: geocoding, cached forecast row [cached], a fresh
     * forecast of 12.34 °C and the given daily [series] and reference [climate].
     */
    private fun stubSearch(
        data: FineWeatherData = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "Germany"),
        cached: WeatherEntity? = null,
        series: DailySeries = yearSeries,
        climate: ReferenceClimate = monthlyClimate,
        seriesFromCache: Boolean = false,
        climateFromCache: Boolean = false,
    ): FineWeatherData {
        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns buildSelectionFromData(data)
        coEvery { weatherRepository.getCachedWeatherById(any()) } returns cached
        coEvery { weatherRepository.getWeatherForecast(any(), any(), any()) } returns mockk()
        every { weatherRepository.calculateAverageForecastTemperature() } returns 12.34
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } returns
            CacheResult(series, seriesFromCache)
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } returns
            CacheResult(climate, climateFromCache)
        coEvery { weatherRepository.insertWeather(any()) } returns Unit
        return data
    }

    private fun forecastEntity(
        id: String,
        date: String,
        days: Int = WeatherViewModel.FORECAST_DAYS_SHORT,
        forecast: Double? = null,
    ): WeatherEntity =
        WeatherEntity(
            id = id,
            date = date,
            locationName = "Test City",
            country = "TC",
            latitude = 1.0,
            longitude = 2.0,
            currentAverage = null,
            currentMonthAverage = null,
            forecastAverage7 = if (days == WeatherViewModel.FORECAST_DAYS_SHORT) forecast else null,
            forecastAverage14 = if (days == WeatherViewModel.FORECAST_DAYS_LONG) forecast else null,
            forecastDate7 = if (days == WeatherViewModel.FORECAST_DAYS_SHORT && forecast != null) date else null,
            forecastDate14 = if (days == WeatherViewModel.FORECAST_DAYS_LONG && forecast != null) date else null,
            historicMonthlyAverageClassic = null,
            historicMonthlyAverageCurrent = null,
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
