package com.example.fineweather.viewmodels

import android.util.Log
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.utils.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun newViewModel(favorites: FavoriteRepository) =
        WeatherViewModel(
            weatherRepository,
            geoCodeRepository,
            settingsRepository,
            favorites,
            nowProvider = { today },
        )

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
}
