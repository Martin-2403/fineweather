package com.example.fineweather.viewmodels

import com.example.fineweather.data.repositories.WeatherRepository
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.data.repositories.StationRepository
import com.example.fineweather.utils.GeocoderUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val stationRepository: StationRepository
) : ViewModel() {

    private val _resultForecastAverage = MutableStateFlow("Forecast results will be shown here")
    val resultForecastAverage: StateFlow<String> = _resultForecastAverage
    
//    private val _resultForecastAverage = MutableStateFlow("Forecast results will be shown here")
//    val resultForecastAverage: StateFlow<String> = _resultForecastAverage

    private val _stations = MutableStateFlow("Stations results will be shown here")
    val stations: StateFlow<String> = _stations

    private val _coordinates = MutableStateFlow(Pair(0.0,0.0))
    val coordinates: StateFlow<Pair<Double, Double>> = _coordinates

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(context: Context, location: String) {
        viewModelScope.launch {
            _resultForecastAverage.value = "Searching for $location..."
            _coordinates.value = GeocoderUtil.getCoordinates(context, location) ?: Pair(0.0,0.0)
            if (coordinates != null) {
                val (latitude, longitude) = _coordinates.value
                _resultForecastAverage.value = "\n$location \nCoordinates: ($latitude, $longitude)"
                try {
                    weatherRepository.getWeatherForecast(latitude, longitude)
                    //weatherRepository.getWeatherHistory(latitude, longitude, 30)
                    _resultForecastAverage.value += "\n7 day forecast daily average: ${weatherRepository.calculateForecastAverageTemperature()}"
                    _resultForecastAverage.value += "\n 30 Year historic data: ${weatherRepository.getWeatherHistory(latitude,longitude,30)}"
                } catch (e: Exception) {
                    _resultForecastAverage.value += "\nError fetching forecast: ${e.message}"
                }
            } else {
                _resultForecastAverage.value = "City not found"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getStation(context: Context, location: String) {
        stationRepository.initialize(context)
        viewModelScope.launch {
            val coordinates = GeocoderUtil.getCoordinates(context, location)
            if (coordinates != null) {
                _stations.value = "\nNearest Station: " + stationRepository.findClosestCoordinate(coordinates)?.Stationsname
            } else
                _stations.value = "\nNo nearest station found"
        }
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

