package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.FavoriteAddResult
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFavoriteRepository(
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

class FakeSettingsRepository(
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
