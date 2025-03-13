package com.example.fineweather.viewmodels

import WeatherRepository
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.utils.GeocoderUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {
    private val weatherRepository = WeatherRepository()

    private val _result = MutableStateFlow("Search results will be shown here")
    val result: StateFlow<String> = _result

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun fetchWeather(context: Context, location: String) {
        viewModelScope.launch {
            _result.value = "Searching for $location..."
            val coordinates = GeocoderUtil.getCoordinates(context, location)
            if (coordinates != null) {
                val (latitude, longitude) = coordinates
                _result.value = "Coordinates: ($latitude, $longitude)"
                try {
                    val forecast = weatherRepository.getWeatherForecast(latitude, longitude)
                    _result.value += "\nForecast: ${forecast.daily.temperature2mMax}"
                } catch (e: Exception) {
                    _result.value += "\nError fetching forecast: ${e.message}"
                }
            } else {
                _result.value = "City not found"
            }
        }
    }
}
