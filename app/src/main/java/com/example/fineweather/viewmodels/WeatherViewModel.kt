package com.example.fineweather.viewmodels

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.FineWeatherData
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
) : ViewModel() {
    private val forecastDays = 7
    private val _resultForecastAverage = MutableStateFlow("-")
    val resultForecastAverage: StateFlow<String> = _resultForecastAverage

    private val _resultCurrentAverage = MutableStateFlow("-")
    val resultCurrentAverage: StateFlow<String> = _resultCurrentAverage

    private val _resultCurrentMonthAverage =
        MutableStateFlow("-")
    val resultCurrentMonthAverage: StateFlow<String> = _resultCurrentMonthAverage

    private val _resultHistoricAverage = MutableStateFlow("-")
    val resultHistoricAverage: StateFlow<String> = _resultHistoricAverage

    private val _forecastDayCount = MutableStateFlow(0)
    val forecastDayCount: StateFlow<Int> = _forecastDayCount

    private val _apiCallCount = MutableStateFlow(0)
    val apiCallCount: StateFlow<Int> = _apiCallCount

    private val _cacheHitCount = MutableStateFlow(0)
    val cacheHitCount: StateFlow<Int> = _cacheHitCount

    private val _resolvedLocationName = MutableStateFlow("")
    val resolvedLocationName: StateFlow<String> = _resolvedLocationName

    private val _status = MutableStateFlow("Enter a city to see temperature averages")
    val status: StateFlow<String> = _status
    private val _coordinates = MutableStateFlow(Pair(0.0, 0.0))

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(location: String) {
        if (location.trim().isEmpty()) {
            _status.value = "Enter a city to see temperature averages"
            setDataValues("-")
            _forecastDayCount.value = 0
            _coordinates.value = Pair(0.0, 0.0)
            _resolvedLocationName.value = ""
            return
        }
        viewModelScope.launch {
            _status.value = "Searching for $location..."
            setDataValues("Loading...")
            try {
                weatherRepository.cleanOutdatedWeatherDate()
                val data = geoCodeRepository.getGeoCode(location.trim())
                if (geoCodeRepository.wasLastLookupFromCache()) {
                    incrementCacheHits()
                } else {
                    incrementApiCalls()
                }
                _coordinates.value = data.coordinates

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
        } else {
            fetchCurrentWeather(data)
        }

        val hasCachedForecast = cachedLatest?.forecastAverage != null
        if (isCurrentToday && hasCachedForecast) {
            incrementCacheHits()
            data.forecastAverage = cachedLatest.forecastAverage
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
            _forecastDayCount.value = forecastDays
        } else {
            fetchForecastWeather(data)
        }

        val hasCachedHistoric = cachedLatest?.historicMonthlyAverage != null
        if (hasCachedHistoric) {
            incrementCacheHits()
            data.historicMonthlyAverage = cachedLatest.historicMonthlyAverage
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

            val historicData = weatherRepository.calculateAverageCurrentMonthlyTemperature()
            data.currentMonthAverage = historicData.second
            _resultCurrentMonthAverage.value = "${formatDouble(historicData.second)}°C"
            persistWeatherCache(data, updateDate = true)
        }.onFailure { e ->
            _resultCurrentAverage.value = "Error"
            _resultCurrentMonthAverage.value = "Error"
            _status.value += "\nError fetching data: ${e.message}"
        }
    }

    private suspend fun fetchForecastWeather(data: FineWeatherData) {
        _resultForecastAverage.value = "Fetching..."
        runCatching {
            incrementApiCalls()
            val response =
                weatherRepository.getWeatherForecast(
                    data.coordinates.first,
                    data.coordinates.second,
                    forecastDays,
                )
            _forecastDayCount.value = response.daily.time.size
            data.forecastAverage = weatherRepository.calculateAverageForecastTemperature()
            _resultForecastAverage.value = "${formatDouble(data.forecastAverage)}°C"
            persistWeatherCache(data)
        }.onFailure { e ->
            _resultForecastAverage.value = "Error"
            _forecastDayCount.value = 0
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
                30,
            )
            val historicData = weatherRepository.calculateAverageHistoricMonthlyTemperature()
            data.historicMonthlyAverage = historicData?.second
            _resultHistoricAverage.value =
                "${formatDouble(data.historicMonthlyAverage)}°C"
            persistWeatherCache(data)
        }.onFailure { e ->
            _resultHistoricAverage.value = "Error"
            _status.value += "\nError fetching data: ${e.message}"
        }
    }

    private suspend fun persistWeatherCache(
        data: FineWeatherData,
        updateDate: Boolean = false,
    ) {
        val existing = weatherRepository.getCachedWeatherById(data.id)
        val resolvedDate =
            if (updateDate) {
                data.timestamp
            } else {
                existing?.date ?: data.timestamp
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
                forecastAverage = data.forecastAverage ?: existing?.forecastAverage,
                historicMonthlyAverage =
                    data.historicMonthlyAverage ?: existing?.historicMonthlyAverage,
            )
        weatherRepository.insertWeather(entity)
    }

    private fun incrementApiCalls(count: Int = 1) {
        _apiCallCount.value += count
    }

    private fun incrementCacheHits(count: Int = 1) {
        _cacheHitCount.value += count
    }

}

class WeatherViewModelFactory(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(weatherRepository, geoCodeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
