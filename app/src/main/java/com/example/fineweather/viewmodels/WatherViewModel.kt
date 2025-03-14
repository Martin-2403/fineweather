package com.example.fineweather.viewmodels

import WeatherRepository
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fineweather.utils.GeocoderUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel(private val weatherRepository: WeatherRepository) : ViewModel() {
    private val _result = MutableStateFlow("Search results will be shown here")
    val result: StateFlow<String> = _result

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(context: Context, location: String) {
        viewModelScope.launch {
            _result.value = "Searching for $location..."
            val coordinates = GeocoderUtil.getCoordinates(context, location)
            println(coordinates)
            if (coordinates != null) {
                val (latitude, longitude) = coordinates
                _result.value = "Coordinates: ($latitude, $longitude)"
                try {
                    val forecast = weatherRepository.getWeatherForecast(latitude, longitude)
                    _result.value += "\nForecast: ${forecast.daily}"
                } catch (e: Exception) {
                    _result.value += "\nError fetching forecast: ${e.message}"
                }
            } else {
                _result.value = "City not found"
            }
        }
    }
}


class WeatherViewModelFactory(private val weatherRepository: WeatherRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(weatherRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

