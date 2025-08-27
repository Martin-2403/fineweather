package com.example.fineweather.viewmodels

import com.example.fineweather.data.repositories.WeatherRepository
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.repositories.StationRepository
import com.example.fineweather.utils.GeocoderUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.example.fineweather.utils.formatDouble
import kotlinx.coroutines.flow.asStateFlow

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val stationRepository: StationRepository
) : ViewModel() {

    private val _fineWeatherDataCache = MutableStateFlow<List<FineWeatherData>>(emptyList())
    val fineWeatherDataCache = _fineWeatherDataCache.asStateFlow()

    private val _resultForecastAverage = MutableStateFlow("-")
    val resultForecastAverage: StateFlow<String> = _resultForecastAverage

    private val _resultCurrentAverage = MutableStateFlow("-")
    val resultCurrentAverage: StateFlow<String> = _resultCurrentAverage

    private val _resultCurrentMonthAverage =
        MutableStateFlow("-")
    val resultCurrentMonthAverage: StateFlow<String> = _resultCurrentMonthAverage

    private val _resultHistoricAverage = MutableStateFlow("-")
    val resultHistoricAverage: StateFlow<String> = _resultHistoricAverage

    private val _status = MutableStateFlow("Please enter location")
    val status: StateFlow<String> = _status

    private val _stations = MutableStateFlow("Stations results will be shown here")
    val stations: StateFlow<String> = _stations

    private val _coordinates = MutableStateFlow(Pair(0.0, 0.0))
    val coordinates: StateFlow<Pair<Double, Double>> = _coordinates

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(location: String) {
        viewModelScope.launch {
            _status.value = "Searching for $location..."
            try {
                _coordinates.value = weatherRepository.getGeoCode(location.trim()) ?: Pair(0.0, 0.0)
            } catch (ex: Exception) {
                Log.e(
                    "WeatherAPI",
                    ex.message ?: "Error occurred while trying to geocode the location"
                )
            }
            setLoading()
            val (first, second) = _coordinates.value
            val geoCodeLocation = weatherRepository.getGeoCodeLocation()
            val data = FineWeatherData(geoCodeLocation, _coordinates.value)

            Log.i("WeatherAPI", data.toString())
            if (first != 0.0 && second != 0.0 && geoCodeLocation !== "") {
                val (latitude, longitude) = _coordinates.value
                _status.value =
                    "Set location: $geoCodeLocation \nCoordinates: ($latitude, $longitude)"
                var historicData: Pair<String, Double?> = Pair("no data", null)
                try {
                    _resultCurrentMonthAverage.value = "Fetching..."
                    weatherRepository.getWeatherCurrent(latitude, longitude, 31)
                    _resultCurrentAverage.value =
                        "${formatDouble(weatherRepository.calculateAverageCurrentTemperature())}°C"
                    historicData = weatherRepository.calculateAverageCurrentMonthlyTemperature()!!
                    _resultCurrentMonthAverage.value =
                        "${formatDouble(historicData.second)}°C"
                } catch (e: Exception) {

                    _resultCurrentAverage.value = "Error fetching forecast: ${e.message}"
                    _resultCurrentMonthAverage.value = "Error fetching forecast: ${e.message}"
                }
                try {
                    _resultForecastAverage.value = "Fetching..."
                    weatherRepository.getWeatherForecast(latitude, longitude, 14)
                    _resultForecastAverage.value =
                        "${formatDouble(weatherRepository.calculateAverageForecastTemperature())}°C"
                } catch (e: Exception) {
                    _resultForecastAverage.value = "Error fetching forecast: ${e.message}"
                }
                try {
                    _resultHistoricAverage.value = "Fetching..."
                    weatherRepository.getWeatherHistory(latitude, longitude, 30)
                    historicData = weatherRepository.calculateAverageHistoricMonthlyTemperature()!!
//                    _resultHistoricAverage.value = "Historic (1970-1999) average for ${historicData.first}: ${historicData.second}°C"
                    _resultHistoricAverage.value =
                        "${formatDouble(historicData.second)}°C"
                } catch (e: Exception) {
                    _resultHistoricAverage.value = "Error fetching forecast: ${e.message}"
                }
            } else {
                _status.value = "City not found"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getStation(context: Context, location: String) {
        stationRepository.initialize(context)
        viewModelScope.launch {
            val coordinates = GeocoderUtil.getCoordinates(context, location)
            if (coordinates != null) {
                _stations.value =
                    "Nearest Station: " + stationRepository.findClosestCoordinate(coordinates)?.Stationsname
            } else
                _stations.value = "No nearest station found"
        }
    }

    fun setLoading() {
        _resultForecastAverage.value = "loading..."
        _resultCurrentAverage.value = "loading..."
        _resultHistoricAverage.value = "loading..."
        _resultCurrentMonthAverage.value = "loading..."
    }
}

class WeatherViewModelFactory(
    private val weatherRepository: WeatherRepository,
    private val stationRepository: StationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(weatherRepository, stationRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



