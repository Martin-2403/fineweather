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
import kotlinx.coroutines.flow.combine

class FakeFavoriteRepository(
    initialFavorites: List<GeoPlace> = emptyList(),
    initialHomeId: String? = null,
) : FavoriteRepository {
    private val state = MutableStateFlow(initialFavorites)
    private val homeState = MutableStateFlow(initialHomeId)

    // Like the DAO: the home place first, the rest in their order.
    override val favorites: Flow<List<GeoPlace>> =
        combine(state, homeState) { places, homeId -> places.sortedBy { it.id != homeId } }
    override val homeId: Flow<String?> = homeState

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
        if (homeState.value == placeId) homeState.value = null
    }

    override suspend fun setHome(placeId: String) {
        if (state.value.any { it.id == placeId }) homeState.value = placeId
    }

    override suspend fun clearHome() {
        homeState.value = null
    }

    override suspend fun getHome(): GeoPlace? = state.value.firstOrNull { it.id == homeState.value }
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
    private val hasOpenedFavoritesState = MutableStateFlow(false)
    override val forecastDays: Flow<Int> = state
    override val historicReference: Flow<HistoricReference> = referenceState
    override val searchLanguage: Flow<String> = languageState
    override val temperatureComparisonMode: Flow<TemperatureComparisonMode> = comparisonModeState
    override val hasSeenInfo: Flow<Boolean> = hasSeenInfoState
    override val hasOpenedFavorites: Flow<Boolean> = hasOpenedFavoritesState

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

    override suspend fun setHasOpenedFavorites(opened: Boolean) {
        hasOpenedFavoritesState.value = opened
    }
}
