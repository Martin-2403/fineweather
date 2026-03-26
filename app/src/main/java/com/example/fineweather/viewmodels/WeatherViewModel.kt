package com.example.fineweather.viewmodels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.utils.formatDouble
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    companion object {
        const val FORECAST_DAYS_SHORT = SettingsDefaults.FORECAST_DAYS_SHORT
        const val FORECAST_DAYS_LONG = SettingsDefaults.FORECAST_DAYS_LONG
    }

    private val _forecastDays = MutableStateFlow(FORECAST_DAYS_SHORT)
    val forecastDays: StateFlow<Int> = _forecastDays
    private val _historicReference = MutableStateFlow(SettingsDefaults.DEFAULT_HISTORIC_REFERENCE)
    val historicReference: StateFlow<HistoricReference> = _historicReference
    private val _resultForecastAverage = MutableStateFlow("-")
    val resultForecastAverage: StateFlow<String> = _resultForecastAverage

    private val _resultCurrentAverage = MutableStateFlow("-")
    val resultCurrentAverage: StateFlow<String> = _resultCurrentAverage

    private val _resultCurrentMonthAverage =
        MutableStateFlow("-")
    val resultCurrentMonthAverage: StateFlow<String> = _resultCurrentMonthAverage

    private val _resultHistoricAverage = MutableStateFlow("-")
    val resultHistoricAverage: StateFlow<String> = _resultHistoricAverage

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

    init {
        viewModelScope.launch {
            settingsRepository.forecastDays.collect { days ->
                val changed = _forecastDays.value != days
                _forecastDays.value = days
                if (changed) {
                    lastResolvedData?.let { data ->
                        updateForecastForSettings(data)
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
                        updateHistoricForSettings(data)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(location: String) {
        if (location.trim().isEmpty()) {
            _status.value = "Enter a city to see temperature averages"
            setDataValues("-")
            _currentMonthDayCount.value = 0
            _coordinates.value = Pair(0.0, 0.0)
            _resolvedLocationName.value = ""
            lastResolvedData = null
            return
        }
        viewModelScope.launch {
            _status.value = "Searching for $location..."
            setDataValues("Loading...")
            try {
                weatherRepository.cleanOutdatedWeatherDate()
                val data = geoCodeRepository.getGeoCode(location.trim())
//                if (geoCodeRepository.wasLastLookupFromCache()) {
//                    incrementCacheHits()
//                } else {
//                    incrementApiCalls()
//                }
                _coordinates.value = data.coordinates
                lastResolvedData = data

                val resolvedLocationDisplay =
                    listOfNotNull(data.location, data.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                _resolvedLocationName.value = data.location
                _status.value =
                    "Set location: $resolvedLocationDisplay\nCoordinates: ${data.coordinates}"
                fetchWeatherForData(data)
                Log.i("WeatherAPI", "temp difference: " + data.tempDifference.toString())
            } catch (ex: Exception) {
                _status.value = "City not found"
                setDataValues("N/A")
                _resolvedLocationName.value = ""
                _currentMonthDayCount.value = 0
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
        val cachedLatest = weatherRepository.getCachedWeatherById(data.id)
        val isCurrentToday = cachedLatest?.date == data.timestamp
        val hasCachedCurrent =
            cachedLatest?.currentAverage != null &&
                    cachedLatest.currentMonthAverage != null

        if (isCurrentToday && hasCachedCurrent) {
            incrementCacheHits()
            data.currentAverage = cachedLatest.currentAverage
            data.currentMonthAverage = cachedLatest.currentMonthAverage
            _resultCurrentAverage.value =
                "${formatDouble(data.currentAverage)}°C"
            _resultCurrentMonthAverage.value =
                "${formatDouble(data.currentMonthAverage)}°C"
            _currentMonthDayCount.value = java.time.LocalDate.now().dayOfMonth
        } else {
            fetchCurrentWeather(data)
        }

        val cachedForecastAverage =
            if (_forecastDays.value == FORECAST_DAYS_LONG) {
                cachedLatest?.forecastAverage14
            } else {
                cachedLatest?.forecastAverage7
            }
        val cachedForecastDate =
            if (_forecastDays.value == FORECAST_DAYS_LONG) {
                cachedLatest?.forecastDate14
            } else {
                cachedLatest?.forecastDate7
            }
        val hasCachedForecast = cachedForecastAverage != null
        val cachedForecastIsCurrent = cachedForecastDate == data.timestamp
        if (hasCachedForecast && cachedForecastIsCurrent) {
            incrementCacheHits()
            data.forecastAverage = cachedForecastAverage
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
        } else {
            fetchForecastWeather(data)
        }

        val cachedHistoricAverage =
            if (_historicReference.value == HistoricReference.CURRENT) {
                cachedLatest?.historicMonthlyAverageCurrent
            } else {
                cachedLatest?.historicMonthlyAverageClassic
            }
        val hasCachedHistoric = cachedHistoricAverage != null
        if (hasCachedHistoric) {
            incrementCacheHits()
            data.historicMonthlyAverage = cachedHistoricAverage
            _resultHistoricAverage.value =
                "${formatDouble(data.historicMonthlyAverage)}°C"
        } else {
            fetchHistoricWeather(data)
        }
    }

    private suspend fun fetchCurrentWeather(data: FineWeatherData) {
        _resultCurrentMonthAverage.value = "Fetching..."
        runCatching {
            incrementApiCalls()
            weatherRepository.getWeatherCurrent(
                data.coordinates.first,
                data.coordinates.second,
                31,
            )
            data.currentAverage = weatherRepository.calculateAverageCurrentTemperature()
            _resultCurrentAverage.value = "${formatDouble(data.currentAverage)}°C"

            val stats = weatherRepository.calculateCurrentMonthStats()
            _currentMonthDayCount.value = stats.dayCount
            data.currentMonthAverage = stats.average
            _resultCurrentMonthAverage.value =
                if (stats.average == null) {
                    "No data yet"
                } else {
                    "${formatDouble(stats.average)}°C"
                }
            persistWeatherCache(data, updateDate = true)
        }.onFailure { e ->
            _resultCurrentAverage.value = "Error"
            _resultCurrentMonthAverage.value = "Error"
            _currentMonthDayCount.value = 0
            _status.value += "\nError fetching data: ${e.message}"
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
            persistWeatherCache(data, forecastDaysOverride = _forecastDays.value)
        }.onFailure { e ->
            _resultForecastAverage.value = "Error"
            _status.value += "\nError fetching data: ${e.message}"
        }
    }

    private suspend fun fetchHistoricWeather(data: FineWeatherData) {
        _resultHistoricAverage.value = "Fetching..."
        runCatching {
            incrementApiCalls()
            weatherRepository.getWeatherHistory(
                data.coordinates.first,
                data.coordinates.second,
                _historicReference.value,
            )
            val historicData = weatherRepository.calculateAverageHistoricMonthlyTemperature()
            data.historicMonthlyAverage = historicData?.second
            _resultHistoricAverage.value =
                "${formatDouble(data.historicMonthlyAverage)}°C"
            persistWeatherCache(data, historicReferenceOverride = _historicReference.value)
        }.onFailure { e ->
            _resultHistoricAverage.value = "Error"
            _status.value += "\nError fetching data: ${e.message}"
        }
    }

    private suspend fun updateForecastForSettings(data: FineWeatherData) {
        val cachedLatest = weatherRepository.getCachedWeatherById(data.id)
        val cachedForecastAverage =
            if (_forecastDays.value == FORECAST_DAYS_LONG) {
                cachedLatest?.forecastAverage14
            } else {
                cachedLatest?.forecastAverage7
            }
        val cachedForecastDate =
            if (_forecastDays.value == FORECAST_DAYS_LONG) {
                cachedLatest?.forecastDate14
            } else {
                cachedLatest?.forecastDate7
            }
        val hasCachedForecast = cachedForecastAverage != null
        val cachedForecastIsCurrent = cachedForecastDate == data.timestamp
        if (hasCachedForecast && cachedForecastIsCurrent) {
            incrementCacheHits()
            data.forecastAverage = cachedForecastAverage
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
        } else {
            fetchForecastWeather(data)
        }
    }

    private suspend fun updateHistoricForSettings(data: FineWeatherData) {
        val cachedLatest = weatherRepository.getCachedWeatherById(data.id)
        val cachedHistoricAverage =
            if (_historicReference.value == HistoricReference.CURRENT) {
                cachedLatest?.historicMonthlyAverageCurrent
            } else {
                cachedLatest?.historicMonthlyAverageClassic
            }
        if (cachedHistoricAverage != null) {
            incrementCacheHits()
            data.historicMonthlyAverage = cachedHistoricAverage
            _resultHistoricAverage.value =
                "${formatDouble(data.historicMonthlyAverage)}°C"
        } else {
            fetchHistoricWeather(data)
        }
    }

    private suspend fun persistWeatherCache(
        data: FineWeatherData,
        updateDate: Boolean = false,
        forecastDaysOverride: Int? = null,
        historicReferenceOverride: HistoricReference? = null,
    ) {
        val existing = weatherRepository.getCachedWeatherById(data.id)
        val resolvedForecastDays = forecastDaysOverride
        val resolvedHistoricReference = historicReferenceOverride
        val resolvedDate =
            if (updateDate) {
                data.timestamp
            } else {
                existing?.date ?: data.timestamp
            }
        val forecastAverage7 =
            if (resolvedForecastDays == FORECAST_DAYS_SHORT && data.forecastAverage != null) {
                data.forecastAverage
            } else {
                existing?.forecastAverage7
            }
        val forecastAverage14 =
            if (resolvedForecastDays == FORECAST_DAYS_LONG && data.forecastAverage != null) {
                data.forecastAverage
            } else {
                existing?.forecastAverage14
            }
        val forecastDate7 =
            if (resolvedForecastDays == FORECAST_DAYS_SHORT && data.forecastAverage != null) {
                data.timestamp
            } else {
                existing?.forecastDate7
            }
        val forecastDate14 =
            if (resolvedForecastDays == FORECAST_DAYS_LONG && data.forecastAverage != null) {
                data.timestamp
            } else {
                existing?.forecastDate14
            }
        val historicClassic =
            if (resolvedHistoricReference == HistoricReference.CLASSIC &&
                data.historicMonthlyAverage != null
            ) {
                data.historicMonthlyAverage
            } else {
                existing?.historicMonthlyAverageClassic
            }
        val historicCurrent =
            if (resolvedHistoricReference == HistoricReference.CURRENT &&
                data.historicMonthlyAverage != null
            ) {
                data.historicMonthlyAverage
            } else {
                existing?.historicMonthlyAverageCurrent
            }
        val entity =
            WeatherEntity(
                id = data.id,
                date = resolvedDate,
                locationName = data.location,
                country = data.country,
                latitude = data.coordinates.first,
                longitude = data.coordinates.second,
                currentAverage = data.currentAverage ?: existing?.currentAverage,
                currentMonthAverage = data.currentMonthAverage ?: existing?.currentMonthAverage,
                forecastAverage7 = forecastAverage7,
                forecastAverage14 = forecastAverage14,
                forecastDate7 = forecastDate7,
                forecastDate14 = forecastDate14,
                historicMonthlyAverageClassic = historicClassic,
                historicMonthlyAverageCurrent = historicCurrent,
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
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(weatherRepository, geoCodeRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
