package com.example.fineweather.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val forecastDays: StateFlow<Int> =
        settingsRepository.forecastDays.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsDefaults.DEFAULT_FORECAST_DAYS,
        )

    val historicReference: StateFlow<HistoricReference> =
        settingsRepository.historicReference.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
        )

    fun setForecastDays(days: Int) {
        require(days == SettingsDefaults.FORECAST_DAYS_SHORT ||
                days == SettingsDefaults.FORECAST_DAYS_LONG
        ) {
            "Forecast days must be ${SettingsDefaults.FORECAST_DAYS_SHORT} or " +
                    "${SettingsDefaults.FORECAST_DAYS_LONG}"
        }
        viewModelScope.launch {
            settingsRepository.setForecastDays(days)
        }
    }

    fun setHistoricReference(reference: HistoricReference) {
        viewModelScope.launch {
            settingsRepository.setHistoricReference(reference)
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
