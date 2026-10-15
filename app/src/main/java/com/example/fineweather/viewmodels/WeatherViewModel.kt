package com.example.fineweather.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.GeoPlace
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
import com.example.fineweather.utils.formatCoordinates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository,
    private val settingsRepository: SettingsRepository,
    private val favoriteRepository: FavoriteRepository,
    private val nowProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    companion object {
        const val FORECAST_DAYS_SHORT = SettingsDefaults.FORECAST_DAYS_SHORT
        const val FORECAST_DAYS_LONG = SettingsDefaults.FORECAST_DAYS_LONG
        private const val RATE_LIMIT_MESSAGE = "Weather service limit reached. Showing cached data where available."
    }

    private val _forecastDays = MutableStateFlow(FORECAST_DAYS_SHORT)
    val forecastDays: StateFlow<Int> = _forecastDays
    private val _historicReference = MutableStateFlow(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE)
    private val _temperatureComparisonMode =
        MutableStateFlow(SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE)
    private val _searchLanguage = MutableStateFlow(SettingsDefaults.DEFAULT_SEARCH_LANGUAGE)

    private val _state = MutableStateFlow(WeatherUiState(today = nowProvider()))
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    /** What the screen shows: the loaded [state] under the current settings. */
    val report: StateFlow<WeatherReport> =
        combine(_state, _forecastDays, _historicReference, _temperatureComparisonMode) { state, days, reference, mode ->
            buildReport(state, days, mode, reference)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue =
                buildReport(
                    _state.value,
                    _forecastDays.value,
                    _temperatureComparisonMode.value,
                    _historicReference.value,
                ),
        )

    private val _apiCallCount = MutableStateFlow(0)
    val apiCallCount: StateFlow<Int> = _apiCallCount
    private val _cacheHitCount = MutableStateFlow(0)
    val cacheHitCount: StateFlow<Int> = _cacheHitCount

    val favorites: StateFlow<List<GeoPlace>> =
        favoriteRepository.favorites.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    // Never suspends the emitter: a snackbar stays up for seconds and loading must not wait for it.
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    /** The running geocoding of a search; starting another search or selection cancels it. */
    private var searchJob: Job? = null

    /** One running load per slice, so a slice can be reloaded without touching the others. */
    private val sliceJobs = mutableMapOf<Slice, Job>()

    /** Counts searches and selections; a result only applies while it still belongs to the current one. */
    private var searchGeneration = 0L
    private val rateLimitShown = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            settingsRepository.forecastDays.collect { days ->
                _forecastDays.value = days
            }
        }
        viewModelScope.launch {
            settingsRepository.historicReference.collect { reference ->
                val changed = _historicReference.value != reference
                _historicReference.value = reference
                if (changed) {
                    reloadReference()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.searchLanguage.collect { language ->
                _searchLanguage.value = language
            }
        }
        viewModelScope.launch {
            settingsRepository.temperatureComparisonMode.collect { mode ->
                _temperatureComparisonMode.value = mode
            }
        }
    }

    fun fetchWeather(location: String) {
        val generation = beginSearch()
        if (location.trim().isEmpty()) {
            _state.value = WeatherUiState(today = nowProvider())
            return
        }
        val today = nowProvider()
        _state.update { it.copy(today = today, search = SearchStatus.Searching(location)).withAllLoading() }
        searchJob =
            viewModelScope.launch {
                val selection =
                    try {
                        weatherRepository.cleanOutdatedWeatherDate()
                        geoCodeRepository.getGeoCode(
                            name = location.trim(),
                            language = _searchLanguage.value,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ex: Exception) {
                        // Some layer may have wrapped the cancellation of a superseded search.
                        currentCoroutineContext().ensureActive()
                        Log.e(
                            "WeatherAPI",
                            ex.message ?: "Error occurred while trying to geocode the location",
                        )
                        updateIfCurrent(generation) {
                            it.copy(
                                search = SearchStatus.NotFound(location),
                                selectedPlace = null,
                                places = emptyList(),
                                forecast = Loadable.Unavailable(Reason.NOT_AVAILABLE),
                                year = Loadable.Unavailable(Reason.NOT_AVAILABLE),
                                reference = Loadable.Unavailable(Reason.NOT_AVAILABLE),
                            )
                        }
                        return@launch
                    }
                val place = selection.primary
                updateIfCurrent(generation) {
                    it.copy(
                        search = SearchStatus.Resolved(place.toPlaceLabel()),
                        selectedPlace = place,
                        places = selection.candidates,
                    )
                }
                loadAllSlices(place, today, generation)
            }
    }

    fun selectPlace(place: GeoPlace) {
        val generation = beginSearch()
        val today = nowProvider()
        _state.update {
            it.copy(
                today = today,
                search = SearchStatus.Resolved(place.toPlaceLabel()),
                selectedPlace = place,
            ).withAllLoading()
        }
        loadAllSlices(place, today, generation)
    }

    /** Cancels everything of the previous search and starts a new generation. */
    private fun beginSearch(): Long {
        searchJob?.cancel()
        sliceJobs.values.forEach { it.cancel() }
        sliceJobs.clear()
        rateLimitShown.set(false)
        return ++searchGeneration
    }

    /** Loads the reference of the shown place again; the other slices carry on undisturbed. */
    private fun reloadReference() {
        val current = _state.value
        val place = current.selectedPlace ?: return
        if (current.search !is SearchStatus.Resolved) return
        _state.update { it.copy(reference = Loadable.Loading, extras = Loadable.Idle) }
        loadReference(place, current.today, searchGeneration)
    }

    private fun loadAllSlices(
        place: GeoPlace,
        today: LocalDate,
        generation: Long,
    ) {
        loadForecast(place, today, generation)
        loadYear(place, today, generation)
        loadReference(place, today, generation)
    }

    private fun loadForecast(
        place: GeoPlace,
        today: LocalDate,
        generation: Long,
    ) = launchSlice(Slice.FORECAST, generation, { copy(forecast = it) }) {
        weatherRepository.getForecast(place.id, place.latitude, place.longitude, today)
    }

    private fun loadYear(
        place: GeoPlace,
        today: LocalDate,
        generation: Long,
    ) = launchSlice(Slice.YEAR, generation, { copy(year = it) }) {
        weatherRepository.getYearToDate(place.id, place.latitude, place.longitude, today)
    }

    private fun loadReference(
        place: GeoPlace,
        today: LocalDate,
        generation: Long,
    ) = launchSlice(Slice.REFERENCE, generation, { copy(reference = it) }) {
        weatherRepository.getReferenceClimate(
            place.id,
            place.latitude,
            place.longitude,
            _historicReference.value,
            today,
        )
    }

    /**
     * Loads one slice in its own job, replacing a running load of the same slice. The scope's
     * supervisor job keeps a failing slice from affecting the others, and [loadSlice] catches its
     * errors anyway.
     */
    private fun <T> launchSlice(
        slice: Slice,
        generation: Long,
        store: WeatherUiState.(Loadable<T>) -> WeatherUiState,
        load: suspend () -> CacheResult<T>,
    ) {
        sliceJobs[slice]?.cancel()
        sliceJobs[slice] =
            viewModelScope.launch {
                val result = loadSlice(slice, generation, load)
                updateIfCurrent(generation) { it.store(result) }
            }
    }

    private fun updateIfCurrent(
        generation: Long,
        transform: (WeatherUiState) -> WeatherUiState,
    ) {
        _state.update { if (generation == searchGeneration) transform(it) else it }
    }

    private suspend fun <T> loadSlice(
        slice: Slice,
        generation: Long,
        load: suspend () -> CacheResult<T>,
    ): Loadable<T> =
        try {
            val result = load()
            if (result.fromCache) incrementCacheHits() else incrementApiCalls()
            Loadable.Ready(result.value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Some layer may have wrapped the cancellation of a superseded load.
            currentCoroutineContext().ensureActive()
            incrementApiCalls()
            Log.e("WeatherAPI", e.message ?: "Error occurred while trying to fetch the ${slice.label}")
            Loadable.Failed(
                when {
                    e is RateLimitException -> {
                        // At most once per search, as soon as the first slice runs into the limit.
                        if (generation == searchGeneration && rateLimitShown.compareAndSet(false, true)) {
                            _snackbarMessages.tryEmit(RATE_LIMIT_MESSAGE)
                        }
                        FailKind.RATE_LIMITED
                    }

                    generateSequence<Throwable>(e) { it.cause }.any { it is IOException } -> FailKind.NETWORK
                    else -> FailKind.OTHER
                },
            )
        }

    fun toggleFavorite() {
        val selected = _state.value.selectedPlace ?: return
        viewModelScope.launch {
            val isFavorite = favorites.value.any { it.id == selected.id }
            if (isFavorite) {
                favoriteRepository.removeFavorite(selected.id)
                return@launch
            }
            val result = favoriteRepository.addFavorite(selected)
            if (result == FavoriteAddResult.LIMIT_REACHED) {
                _snackbarMessages.tryEmit("Favorites limit reached (10). Remove one to add another.")
            }
        }
    }

    fun removeFavorite(placeId: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(placeId)
        }
    }

    private fun incrementApiCalls() {
        _apiCallCount.update { it + 1 }
    }

    private fun incrementCacheHits() {
        _cacheHitCount.update { it + 1 }
    }

    fun setForecastDays(days: Int) {
        require(days == FORECAST_DAYS_SHORT || days == FORECAST_DAYS_LONG) {
            "Forecast days must be $FORECAST_DAYS_SHORT or $FORECAST_DAYS_LONG"
        }
        if (_forecastDays.value == days) {
            return
        }
        _forecastDays.value = days
        viewModelScope.launch {
            settingsRepository.setForecastDays(days)
        }
    }

}

private enum class Slice(val label: String) {
    FORECAST("forecast"),
    YEAR("current weather"),
    REFERENCE("historic weather"),
}

/** All slices back to loading, so nothing of the previous place is left on screen. */
private fun WeatherUiState.withAllLoading() =
    copy(forecast = Loadable.Loading, year = Loadable.Loading, reference = Loadable.Loading, extras = Loadable.Idle)

private fun GeoPlace.toPlaceLabel(): PlaceLabel =
    PlaceLabel(
        name =
            listOfNotNull(name, admin1?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
                .filter { it.isNotBlank() }
                .joinToString(", "),
        coordinates = formatCoordinates(latitude, longitude),
    )

class WeatherViewModelFactory(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository,
    private val settingsRepository: SettingsRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(
                weatherRepository,
                geoCodeRepository,
                settingsRepository,
                favoriteRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
