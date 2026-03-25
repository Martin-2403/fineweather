package com.example.fineweather.viewmodels

import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private class FakeSettingsRepository(
        initialDays: Int = SettingsDefaults.DEFAULT_FORECAST_DAYS,
    ) : SettingsRepository {
        private val state = MutableStateFlow(initialDays)
        override val forecastDays: Flow<Int> = state

        override suspend fun setForecastDays(days: Int) {
            state.value = days
        }
    }
}
