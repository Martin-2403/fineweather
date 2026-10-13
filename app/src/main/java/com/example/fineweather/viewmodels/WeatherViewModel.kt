package com.example.fineweather.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.toFineWeatherData
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.data.repositories.FavoriteAddResult
import com.example.fineweather.data.repositories.FavoriteRepository
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.CacheResult
import com.example.fineweather.data.repositories.RateLimitException
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.lastDaysMean
import com.example.fineweather.domain.monthToDate
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.utils.formatDouble
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

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
    }

    private val _forecastDays = MutableStateFlow(FORECAST_DAYS_SHORT)
    val forecastDays: StateFlow<Int> = _forecastDays
    private val _historicReference = MutableStateFlow(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE)
    val historicReference: StateFlow<HistoricReference> = _historicReference
    private val _temperatureComparisonMode =
        MutableStateFlow(SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE)
    val temperatureComparisonMode: StateFlow<TemperatureComparisonMode> = _temperatureComparisonMode
    private val _searchLanguage = MutableStateFlow(SettingsDefaults.DEFAULT_SEARCH_LANGUAGE)
    private val _resultForecastAverage = MutableStateFlow("-")
    val resultForecastAverage: StateFlow<String> = _resultForecastAverage
    private val _resultCurrentAverage = MutableStateFlow("-")
    val resultCurrentAverage: StateFlow<String> = _resultCurrentAverage
    private val _resultCurrentMonthAverage = MutableStateFlow("-")
    val resultCurrentMonthAverage: StateFlow<String> = _resultCurrentMonthAverage
    private val _resultHistoricAverage = MutableStateFlow("-")
    val resultHistoricAverage: StateFlow<String> = _resultHistoricAverage
    private val _historicMonthlyStdDev = MutableStateFlow<Double?>(null)
    val historicMonthlyStdDev: StateFlow<Double?> = _historicMonthlyStdDev
    private val _climate = MutableStateFlow<ClimateSnapshot?>(null)
    val climate: StateFlow<ClimateSnapshot?> = _climate
    private val _currentMonthDayCount = MutableStateFlow(0)
    val currentMonthDayCount: StateFlow<Int> = _currentMonthDayCount
    private val _apiCallCount = MutableStateFlow(0)
    val apiCallCount: StateFlow<Int> = _apiCallCount
    private val _cacheHitCount = MutableStateFlow(0)
    val cacheHitCount: StateFlow<Int> = _cacheHitCount

    private val _resolvedLocationName = MutableStateFlow("")
    val resolvedLocationName: StateFlow<String> = _resolvedLocationName

    private val _status = MutableStateFlow("Enter a city to see temperature averages")
    val status: StateFlow<String> = _status
    private val _coordinates = MutableStateFlow(Pair(0.0, 0.0))
    private var lastResolvedData: FineWeatherData? = null
    private val _places = MutableStateFlow<List<GeoPlace>>(emptyList())
    val places: StateFlow<List<GeoPlace>> = _places
    private val _selectedPlace = MutableStateFlow<GeoPlace?>(null)
    val selectedPlace: StateFlow<GeoPlace?> = _selectedPlace
    val favorites: StateFlow<List<GeoPlace>> =
        favoriteRepository.favorites.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )
    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsRepository.forecastDays.collect { days ->
                val changed = _forecastDays.value != days
                _forecastDays.value = days
                if (changed) {
                    lastResolvedData?.let { data ->
                        loadForecast(data)
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.historicReference.collect { reference ->
                val changed = _historicReference.value != reference
                _historicReference.value = reference
                if (changed) {
                    lastResolvedData?.let { data ->
                        loadClimate(data)
                    }
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
        if (location.trim().isEmpty()) {
            _status.value = "Enter a location to see temperature averages"
            setDataValues("-")
            _currentMonthDayCount.value = 0
            _coordinates.value = Pair(0.0, 0.0)
            _resolvedLocationName.value = ""
            _places.value = emptyList()
            _selectedPlace.value = null
            _historicMonthlyStdDev.value = null
            _climate.value = null
            lastResolvedData = null
            return
        }
        viewModelScope.launch {
            _status.value = "Searching for $location..."
            setDataValues("Loading...")
            try {
                weatherRepository.cleanOutdatedWeatherDate()
                val selection = geoCodeRepository.getGeoCode(
                    name = location.trim(),
                    language = _searchLanguage.value,
                )
                val primaryPlace = selection.primary
                _selectedPlace.value = primaryPlace
                _places.value = selection.candidates
                val data = primaryPlace.toFineWeatherData()

                _coordinates.value = data.coordinates
                lastResolvedData = data

                val resolvedLocationDisplay =
                    listOfNotNull(
                        primaryPlace.name,
                        primaryPlace.admin1?.takeIf { it.isNotBlank() },
                        primaryPlace.country?.takeIf { it.isNotBlank() },
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                _resolvedLocationName.value = primaryPlace.name
                _status.value =
                    "$resolvedLocationDisplay ${data.coordinates}"
                fetchWeatherForData(data)
            } catch (ex: Exception) {
                _status.value = "City not found"
                setDataValues("N/A")
                _resolvedLocationName.value = ""
                _currentMonthDayCount.value = 0
                _historicMonthlyStdDev.value = null
                _climate.value = null
                _places.value = emptyList()
                _selectedPlace.value = null
                Log.e(
                    "WeatherAPI",
                    ex.message ?: "Error occurred while trying to geocode the location",
                )
            }
        }
    }

    fun setDataValues(text: String) {
        _resultForecastAverage.value = text
        _resultCurrentAverage.value = text
        _resultHistoricAverage.value = text
        _resultCurrentMonthAverage.value = text
    }

    private suspend fun fetchWeatherForData(data: FineWeatherData) {
        loadForecast(data)
        loadClimate(data)
    }

    private suspend fun loadForecast(data: FineWeatherData) {
        val cachedLatest = weatherRepository.getCachedWeatherById(data.id)
        val isLong = _forecastDays.value == FORECAST_DAYS_LONG
        val cachedForecastAverage =
            if (isLong) cachedLatest?.forecastAverage14 else cachedLatest?.forecastAverage7
        val cachedForecastDate =
            if (isLong) cachedLatest?.forecastDate14 else cachedLatest?.forecastDate7
        if (cachedForecastAverage != null && cachedForecastDate == data.timestamp) {
            incrementCacheHits()
            data.forecastAverage = cachedForecastAverage
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
        } else {
            fetchForecastWeather(data)
        }
    }

    private suspend fun loadClimate(data: FineWeatherData) {
        val today = nowProvider()
        _resultCurrentAverage.value = "Fetching..."
        _resultCurrentMonthAverage.value = "Fetching..."
        _resultHistoricAverage.value = "Fetching..."

        val series =
            loadOrNull("current weather") {
                weatherRepository.getYearToDate(
                    data.id,
                    data.coordinates.first,
                    data.coordinates.second,
                    today,
                )
            }
        val climate =
            loadOrNull("historic weather") {
                weatherRepository.getReferenceClimate(
                    data.id,
                    data.coordinates.first,
                    data.coordinates.second,
                    _historicReference.value,
                    today,
                )
            }
        publishClimate(series, climate, today)
    }

    private suspend fun <T> loadOrNull(
        what: String,
        load: suspend () -> CacheResult<T>,
    ): T? =
        try {
            val result = load()
            if (result.fromCache) incrementCacheHits() else incrementApiCalls()
            result.value
        } catch (e: Exception) {
            incrementApiCalls()
            Log.e("WeatherAPI", e.message ?: "Error occurred while trying to fetch the $what")
            if (e is RateLimitException) {
                _snackbarMessages.emit(
                    "Weather service limit reached. Showing cached data where available.",
                )
            }
            null
        }

    private fun publishClimate(
        series: DailySeries?,
        climate: ReferenceClimate?,
        today: LocalDate,
    ) {
        if (series == null) {
            _resultCurrentAverage.value = "Error"
            _resultCurrentMonthAverage.value = "Error"
            _currentMonthDayCount.value = 0
        } else {
            _resultCurrentAverage.value =
                lastDaysMean(series, today)?.let { "${formatDouble(it)}°C" } ?: "N/A"
        }

        val monthToDate = series?.let { monthToDate(it, climate, today) }
        if (series != null) {
            when {
                monthToDate != null -> {
                    _resultCurrentMonthAverage.value = "${formatDouble(monthToDate.observedMean)}°C"
                    _currentMonthDayCount.value = monthToDate.daysObserved
                }

                today.dayOfMonth == 1 -> {
                    _resultCurrentMonthAverage.value = "N/A"
                    _currentMonthDayCount.value = 0
                }

                else -> {
                    _resultCurrentMonthAverage.value = "No data yet"
                    _currentMonthDayCount.value = 0
                }
            }
        }

        if (climate == null) {
            _resultHistoricAverage.value = "Error"
            _historicMonthlyStdDev.value = null
        } else {
            // Without a complete day of the month yet, fall back to the whole month's normal.
            val referenceMean = monthToDate?.referenceMean ?: climate.monthMean(today.monthValue)
            _historicMonthlyStdDev.value =
                if (monthToDate != null) monthToDate.referenceStdDev else climate.monthStdDev(today.monthValue)
            _resultHistoricAverage.value = referenceMean?.let { "${formatDouble(it)}°C" } ?: "N/A"
        }

        _climate.value = series?.let { ClimateSnapshot(it, climate, today) }
    }

    fun selectPlace(place: GeoPlace) {
        viewModelScope.launch {
            val data = place.toFineWeatherData()
            val display =
                listOfNotNull(
                    place.name,
                    place.admin1?.takeIf { it.isNotBlank() },
                    place.country?.takeIf { it.isNotBlank() },
                )
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            _selectedPlace.value = place
            _resolvedLocationName.value = place.name
            _status.value =
                "$display\nCoordinates: ${data.coordinates}"
            lastResolvedData = data
            fetchWeatherForData(data)
        }
    }

    fun toggleFavorite() {
        val selected = _selectedPlace.value ?: return
        viewModelScope.launch {
            val isFavorite = favorites.value.any { it.id == selected.id }
            if (isFavorite) {
                favoriteRepository.removeFavorite(selected.id)
                return@launch
            }
            val result = favoriteRepository.addFavorite(selected)
            if (result == FavoriteAddResult.LIMIT_REACHED) {
                _snackbarMessages.emit(
                    "Favorites limit reached (10). Remove one to add another.",
                )
            }
        }
    }

    fun removeFavorite(placeId: String) {
        viewModelScope.launch {
            favoriteRepository.removeFavorite(placeId)
        }
    }

    private suspend fun fetchForecastWeather(data: FineWeatherData) {
        _resultForecastAverage.value = "Fetching..."
        runCatching {
            incrementApiCalls()
            weatherRepository.getWeatherForecast(
                data.coordinates.first,
                data.coordinates.second,
                _forecastDays.value,
            )
            data.forecastAverage = weatherRepository.calculateAverageForecastTemperature()
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
            persistForecast(data)
        }.onFailure { e ->
            _resultForecastAverage.value = "Error"

            Log.e(
                "WeatherAPI",
                e.message ?: "Error occurred while trying to fetch the forecast",
            )
        }
    }

    private suspend fun persistForecast(data: FineWeatherData) {
        val existing = weatherRepository.getCachedWeatherById(data.id)
        val isShort = _forecastDays.value == FORECAST_DAYS_SHORT
        val isLong = _forecastDays.value == FORECAST_DAYS_LONG
        val fresh = data.forecastAverage
        val entity =
            WeatherEntity(
                id = data.id,
                date = data.timestamp,
                locationName = data.location,
                country = data.country,
                latitude = data.coordinates.first,
                longitude = data.coordinates.second,
                currentAverage = null,
                currentMonthAverage = null,
                forecastAverage7 = if (isShort && fresh != null) fresh else existing?.forecastAverage7,
                forecastAverage14 = if (isLong && fresh != null) fresh else existing?.forecastAverage14,
                forecastDate7 = if (isShort && fresh != null) data.timestamp else existing?.forecastDate7,
                forecastDate14 = if (isLong && fresh != null) data.timestamp else existing?.forecastDate14,
                historicMonthlyAverageClassic = null,
                historicMonthlyAverageCurrent = null,
            )
        weatherRepository.insertWeather(entity)
    }

    private fun incrementApiCalls(count: Int = 1) {
        _apiCallCount.value += count
    }

    private fun incrementCacheHits(count: Int = 1) {
        _cacheHitCount.value += count
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
