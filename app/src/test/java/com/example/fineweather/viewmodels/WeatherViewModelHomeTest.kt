package com.example.fineweather.viewmodels

import android.util.Log
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.GeocodeSelection
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.repositories.CacheResult
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.domain.metricSeriesOf
import com.example.fineweather.domain.syntheticReference
import com.example.fineweather.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The home place: marking one of the favorites, and loading it when the app starts. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelHomeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var weatherRepository: WeatherRepository
    private lateinit var geoCodeRepository: GeoCodeRepository
    private lateinit var settingsRepository: FakeSettingsRepository
    private val today = LocalDate.of(2026, 4, 2)

    private val berlin = GeoPlace("loc-1", "Berlin", "Germany", null, 1.0, 2.0)
    private val hamburg = GeoPlace("loc-2", "Hamburg", "Germany", null, 3.0, 4.0)
    private val oslo = GeoPlace("loc-3", "Oslo", "Norway", null, 5.0, 6.0)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        weatherRepository = mockk(relaxed = true)
        geoCodeRepository = mockk(relaxed = true)
        settingsRepository = FakeSettingsRepository()
        stubLoads()
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newViewModel(
        favorites: FavoriteRepository,
        settings: SettingsRepository = settingsRepository,
    ) = WeatherViewModel(
        weatherRepository,
        geoCodeRepository,
        settings,
        favorites,
        nowProvider = { today },
    )

    /** Every place loads successfully, so what a test sees is which place was asked for. */
    private fun stubLoads() {
        coEvery { weatherRepository.cleanOutdatedWeatherDate() } returns Unit
        coEvery { weatherRepository.getForecast(any(), any(), any(), any()) } returns
            CacheResult(metricSeriesOf(today, today.plusDays(13)) { 12.0 }, false)
        coEvery { weatherRepository.getYearToDate(any(), any(), any(), any()) } returns
            CacheResult(metricSeriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)) { 10.0 }, false)
        coEvery { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) } returns
            CacheResult(syntheticReference { it.monthValue.toDouble() }, false)
    }

    /** A repository whose home is not known until [gate] opens, like a Room read that is still running. */
    private fun slowHome(
        favorites: FakeFavoriteRepository,
        gate: CompletableDeferred<Unit>,
    ) = object : FavoriteRepository by favorites {
        override suspend fun getHome(): GeoPlace? {
            gate.await()
            return favorites.getHome()
        }
    }

    // ---- marking the home ----

    @Test
    fun withoutAHome_homeIdIsNull() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg)))
        advanceUntilIdle()

        assertNull(viewModel.homeId.value)
    }

    @Test
    fun homeId_showsTheStoredHomeAndTheFavoritesListItFirst() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg, oslo), initialHomeId = "loc-3"))
        advanceUntilIdle()

        assertEquals("loc-3", viewModel.homeId.value)
        assertEquals(listOf(oslo, berlin, hamburg), viewModel.favorites.value)
    }

    @Test
    fun toggleHome_marksThePlaceAndMovesItToTheTop() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg)))

        viewModel.toggleHome("loc-2")
        advanceUntilIdle()

        assertEquals("loc-2", viewModel.homeId.value)
        assertEquals(listOf(hamburg, berlin), viewModel.favorites.value)
    }

    @Test
    fun toggleHome_onTheHome_leavesNoHome() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-2"))

        viewModel.toggleHome("loc-2")
        advanceUntilIdle()

        assertNull(viewModel.homeId.value)
        assertEquals(listOf(berlin, hamburg), viewModel.favorites.value)
    }

    @Test
    fun toggleHome_onAnotherPlace_replacesTheHome() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-1"))

        viewModel.toggleHome("loc-2")
        advanceUntilIdle()

        assertEquals("loc-2", viewModel.homeId.value)
    }

    @Test
    fun toggleHome_onANonFavorite_createsNoHome() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin)))

        viewModel.toggleHome("loc-9")
        advanceUntilIdle()

        assertNull(viewModel.homeId.value)
    }

    @Test
    fun removingTheHomeFavorite_leavesNoHome() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-1"))

        viewModel.removeFavorite("loc-1")
        advanceUntilIdle()

        assertNull(viewModel.homeId.value)
        assertEquals(listOf(hamburg), viewModel.favorites.value)
    }

    // ---- loading the home at the start ----

    @Test
    fun loadHomeOnStart_selectsTheHomeLikeAnyPlace() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-2"))

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertEquals(hamburg, viewModel.state.value.selectedPlace)
        assertEquals(
            SearchStatus.Resolved(PlaceLabel("Hamburg, Germany", "3.00, 4.00", "Germany")),
            viewModel.state.value.search,
        )
        coVerify(exactly = 1) { weatherRepository.getForecast("loc-2", 3.0, 4.0, today) }
        coVerify(exactly = 1) { weatherRepository.getYearToDate("loc-2", 3.0, 4.0, today) }
        coVerify(exactly = 1) { weatherRepository.getReferenceClimate("loc-2", 3.0, 4.0, any(), today) }
    }

    @Test
    fun loadHomeOnStart_withoutAHome_changesNothing() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg)))

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertEquals(WeatherUiState(today = today), viewModel.state.value)
        coVerify(exactly = 0) { weatherRepository.getForecast(any(), any(), any(), any()) }
        coVerify(exactly = 0) { weatherRepository.getYearToDate(any(), any(), any(), any()) }
    }

    @Test
    fun loadHomeOnStart_aSecondCallAfterARotationDoesNothing() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-1"))
        viewModel.loadHomeOnStart()
        advanceUntilIdle()
        viewModel.selectPlace(oslo)
        advanceUntilIdle()

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertEquals(oslo, viewModel.state.value.selectedPlace)
        coVerify(exactly = 1) { weatherRepository.getForecast("loc-1", any(), any(), any()) }
    }

    @Test
    fun loadHomeOnStart_doesNotBringTheHomeBackAfterTheSearchWasCleared() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin), initialHomeId = "loc-1"))
        viewModel.loadHomeOnStart()
        advanceUntilIdle()
        viewModel.fetchWeather("")

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertEquals(WeatherUiState(today = today), viewModel.state.value)
    }

    @Test
    fun loadHomeOnStart_aHomeSetAfterTheFirstCallWaitsForTheNextStart() = runTest {
        val favorites = FakeFavoriteRepository(listOf(berlin))
        val viewModel = newViewModel(favorites)
        viewModel.loadHomeOnStart()
        advanceUntilIdle()
        viewModel.toggleHome("loc-1")

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedPlace)
    }

    @Test
    fun loadHomeOnStart_aSearchThatCameFirstIsNotOverridden() = runTest {
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin), initialHomeId = "loc-1"))
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns GeocodeSelection(hamburg, emptyList())
        viewModel.fetchWeather("Hamburg")
        advanceUntilIdle()

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        assertEquals(hamburg, viewModel.state.value.selectedPlace)
        coVerify(exactly = 0) { weatherRepository.getForecast("loc-1", any(), any(), any()) }
    }

    @Test
    fun loadHomeOnStart_aSelectionDuringTheReadWins() = runTest {
        val gate = CompletableDeferred<Unit>()
        val favorites = FakeFavoriteRepository(listOf(berlin, hamburg), initialHomeId = "loc-1")
        val viewModel = newViewModel(slowHome(favorites, gate))
        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        viewModel.selectPlace(hamburg)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(hamburg, viewModel.state.value.selectedPlace)
        coVerify(exactly = 0) { weatherRepository.getForecast("loc-1", any(), any(), any()) }
        coVerify(exactly = 1) { weatherRepository.getForecast("loc-2", any(), any(), any()) }
    }

    @Test
    fun loadHomeOnStart_aSearchDuringTheReadWins() = runTest {
        val gate = CompletableDeferred<Unit>()
        val favorites = FakeFavoriteRepository(listOf(berlin), initialHomeId = "loc-1")
        val viewModel = newViewModel(slowHome(favorites, gate))
        coEvery { geoCodeRepository.getGeoCode(any(), any()) } returns GeocodeSelection(hamburg, emptyList())
        viewModel.loadHomeOnStart()

        viewModel.fetchWeather("Hamburg")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(hamburg, viewModel.state.value.selectedPlace)
        coVerify(exactly = 0) { weatherRepository.getForecast("loc-1", any(), any(), any()) }
    }

    @Test
    fun loadHomeOnStart_usesTheStoredReferenceAndLoadsOnce() = runTest {
        settingsRepository = FakeSettingsRepository(initialReference = HistoricReference.CURRENT)
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin), initialHomeId = "loc-1"))

        viewModel.loadHomeOnStart()
        advanceUntilIdle()

        coVerify(exactly = 1) { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)
        }
    }

    @Test
    fun loadHomeOnStart_waitsForTheStoredReferenceThatIsStillBeingRead() = runTest {
        val gate = CompletableDeferred<Unit>()
        val stored = FakeSettingsRepository(initialReference = HistoricReference.CURRENT)
        val slowSettings =
            object : SettingsRepository by stored {
                override val historicReference: Flow<HistoricReference> =
                    flow {
                        gate.await()
                        emitAll(stored.historicReference)
                    }
            }
        val viewModel = newViewModel(FakeFavoriteRepository(listOf(berlin), initialHomeId = "loc-1"), slowSettings)
        viewModel.loadHomeOnStart()
        advanceUntilIdle()
        coVerify(exactly = 0) { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) }

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(berlin, viewModel.state.value.selectedPlace)
        coVerify(exactly = 1) { weatherRepository.getReferenceClimate(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            weatherRepository.getReferenceClimate("loc-1", 1.0, 2.0, HistoricReference.CURRENT, today)
        }
    }
}
