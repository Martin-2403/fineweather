package com.example.fineweather.viewmodels

import com.example.fineweather.data.repositories.WeatherRepository
import android.content.Context
import android.os.Build
import android.view.View
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

    private val _result = MutableStateFlow("Search results will be shown here")
    val result: StateFlow<String> = _result

    private val _stations = MutableStateFlow("Stations results will be shown here")
    val stations: StateFlow<String> = _stations

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(context: Context, location: String) {
        viewModelScope.launch {
            _result.value = "Searching for $location..."
            val coordinates = GeocoderUtil.getCoordinates(context, location)
            if (coordinates != null) {
                val (latitude, longitude) = coordinates
                _result.value = "\n$location \nCoordinates: ($latitude, $longitude)"
                try {
                    val forecast = weatherRepository.getWeatherForecast(latitude, longitude)
                    _result.value += "\n7 day forecast daily average: ${weatherRepository.calculateForecastAverageTemperature()}"
                } catch (e: Exception) {
                    _result.value += "\nError fetching forecast: ${e.message}"
                }
            } else {
                _result.value = "City not found"
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

