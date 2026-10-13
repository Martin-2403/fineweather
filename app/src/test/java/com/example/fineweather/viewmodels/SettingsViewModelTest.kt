package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.CacheRepository
import com.example.fineweather.data.repositories.CacheStats
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setForecastDays_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        assertEquals(SettingsDefaults.DEFAULT_FORECAST_DAYS, viewModel.forecastDays.value)

        viewModel.setForecastDays(SettingsDefaults.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        assertEquals(SettingsDefaults.FORECAST_DAYS_LONG, viewModel.forecastDays.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setHistoricReference_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        assertEquals(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE, viewModel.historicReference.value)

        viewModel.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals(HistoricReference.CURRENT, viewModel.historicReference.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setForecastDays_rejectsInvalidValues() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        assertThrows(IllegalArgumentException::class.java) {
            viewModel.setForecastDays(999)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setSearchLanguage_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        assertEquals(SettingsDefaults.DEFAULT_SEARCH_LANGUAGE, viewModel.searchLanguage.value)

        viewModel.setSearchLanguage("fr")
        advanceUntilIdle()

        assertEquals("fr", viewModel.searchLanguage.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setTemperatureComparisonMode_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        assertEquals(
            SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
            viewModel.temperatureComparisonMode.value,
        )

        viewModel.setTemperatureComparisonMode(TemperatureComparisonMode.NORMALIZED_ANOMALY)
        advanceUntilIdle()

        assertEquals(
            TemperatureComparisonMode.NORMALIZED_ANOMALY,
            viewModel.temperatureComparisonMode.value,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setHasSeenInfo_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeCacheRepository())

        advanceUntilIdle()
        assertEquals(false, viewModel.hasSeenInfo.value)

        viewModel.setHasSeenInfo(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.hasSeenInfo.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cacheStats_reflectsRepository() = runTest {
        val cacheRepository = FakeCacheRepository(CacheStats(weatherEntries = 3, searchEntries = 5))
        val viewModel = SettingsViewModel(FakeSettingsRepository(), cacheRepository)

        advanceUntilIdle()

        assertEquals(CacheStats(weatherEntries = 3, searchEntries = 5), viewModel.cacheStats.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clearCache_clearsRepositoryAndResetsStats() = runTest {
        val cacheRepository = FakeCacheRepository(CacheStats(weatherEntries = 3, searchEntries = 5))
        val viewModel = SettingsViewModel(FakeSettingsRepository(), cacheRepository)
        advanceUntilIdle()

        viewModel.clearCache()
        advanceUntilIdle()

        assertEquals(1, cacheRepository.clearCalls)
        assertEquals(CacheStats(), viewModel.cacheStats.value)
    }

    @Test
    fun settingsViewModelFactory_createsViewModel() {
        val repository = FakeSettingsRepository()
        val factory = SettingsViewModelFactory(repository, FakeCacheRepository())

        val viewModel = factory.create(SettingsViewModel::class.java)

        assertEquals(SettingsViewModel::class.java, viewModel::class.java)
    }

    @Test
    fun settingsViewModelFactory_throwsForUnknownClass() {
        val repository = FakeSettingsRepository()
        val factory = SettingsViewModelFactory(repository, FakeCacheRepository())

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(WeatherViewModel::class.java)
        }
    }

    private class FakeCacheRepository(
        initial: CacheStats = CacheStats(),
    ) : CacheRepository {
        private val state = MutableStateFlow(initial)
        var clearCalls = 0
            private set
        override val stats: Flow<CacheStats> = state

        override suspend fun clear() {
            clearCalls++
            state.value = CacheStats()
        }
    }

    private class FakeSettingsRepository(
        initialDays: Int = SettingsDefaults.DEFAULT_FORECAST_DAYS,
        initialReference: HistoricReference = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
        initialLanguage: String = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
        initialTemperatureComparisonMode: TemperatureComparisonMode =
            SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
        initialHasSeenInfo: Boolean = false,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialDays)
        private val referenceState = MutableStateFlow(initialReference)
        private val languageState = MutableStateFlow(initialLanguage)
        private val comparisonModeState = MutableStateFlow(initialTemperatureComparisonMode)
        private val hasSeenInfoState = MutableStateFlow(initialHasSeenInfo)
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
}
