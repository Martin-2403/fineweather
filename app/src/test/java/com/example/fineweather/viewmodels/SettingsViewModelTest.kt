package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.HistoricReference
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
        val viewModel = SettingsViewModel(repository)

        assertEquals(SettingsDefaults.DEFAULT_FORECAST_DAYS, viewModel.forecastDays.value)

        viewModel.setForecastDays(SettingsDefaults.FORECAST_DAYS_LONG)
        advanceUntilIdle()

        assertEquals(SettingsDefaults.FORECAST_DAYS_LONG, viewModel.forecastDays.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setHistoricReference_updatesState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        assertEquals(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE, viewModel.historicReference.value)

        viewModel.setHistoricReference(HistoricReference.CURRENT)
        advanceUntilIdle()

        assertEquals(HistoricReference.CURRENT, viewModel.historicReference.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun setForecastDays_rejectsInvalidValues() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        assertThrows(IllegalArgumentException::class.java) {
            viewModel.setForecastDays(999)
        }
    }

    @Test
    fun settingsViewModelFactory_createsViewModel() {
        val repository = FakeSettingsRepository()
        val factory = SettingsViewModelFactory(repository)

        val viewModel = factory.create(SettingsViewModel::class.java)

        assertEquals(SettingsViewModel::class.java, viewModel::class.java)
    }

    @Test
    fun settingsViewModelFactory_throwsForUnknownClass() {
        val repository = FakeSettingsRepository()
        val factory = SettingsViewModelFactory(repository)

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(WeatherViewModel::class.java)
        }
    }

    private class FakeSettingsRepository(
        initialDays: Int = SettingsDefaults.DEFAULT_FORECAST_DAYS,
        initialReference: HistoricReference = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialDays)
        private val referenceState = MutableStateFlow(initialReference)
        override val forecastDays: Flow<Int> = state
        override val historicReference: Flow<HistoricReference> = referenceState

        override suspend fun setForecastDays(days: Int) {
            state.value = days
        }

        override suspend fun setHistoricReference(reference: HistoricReference) {
            referenceState.value = reference
        }
    }
}
