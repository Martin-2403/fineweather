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
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.utils.formatDouble
import kotlinx.coroutines.flow.asStateFlow

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository
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
            setDataValues("loading...")
            try {
                _coordinates.value = geoCodeRepository.getGeoCode(location.trim()) ?: Pair(0.0, 0.0)
                val geoCodeLocation = geoCodeRepository.getGeoCodeLocation()
                val data = FineWeatherData(geoCodeLocation, _coordinates.value)
                val (latitude, longitude) = _coordinates.value
                _status.value =
                    "Set location: $geoCodeLocation\nCoordinates: ${data.coordinates}"
                var historicData: Pair<String, Double?> = Pair("no data", null)
                try {
                    _resultCurrentMonthAverage.value = "Fetching..."
                    weatherRepository.getWeatherCurrent(data.coordinates.first,data.coordinates.second, 31)
                    data.currentAverage = weatherRepository.calculateAverageCurrentTemperature()
                    _resultCurrentAverage.value =
                        "${formatDouble(data.currentAverage)}°C"
                    historicData =
                        weatherRepository.calculateAverageCurrentMonthlyTemperature()!!
                    data.currentMonthAverage = historicData.second
                    _resultCurrentMonthAverage.value =
                        "${formatDouble(historicData.second)}°C"
                } catch (e: Exception) {

                    _resultCurrentAverage.value = "Error"
                    _resultCurrentMonthAverage.value = "Error"
                    _status.value += "\nError fetching data: ${e.message}"
                }
                try {
                    _resultForecastAverage.value = "Fetching..."
                    weatherRepository.getWeatherForecast(data.coordinates.first,data.coordinates.second, 14)
                    data.forecastAverage =
                        weatherRepository.calculateAverageForecastTemperature()
                    _resultForecastAverage.value =
                        "${formatDouble(data.forecastAverage)}°C"
                } catch (e: Exception) {
                    _resultForecastAverage.value = "Error"
                    _status.value += "\nError fetching data: ${e.message}"

                }
                try {
                    _resultHistoricAverage.value = "Fetching..."
                    weatherRepository.getWeatherHistory(data.coordinates.first,data.coordinates.second, 30)
                    historicData =
                        weatherRepository.calculateAverageHistoricMonthlyTemperature()!!
//                    _resultHistoricAverage.value = "Historic (1970-1999) average for ${historicData.first}: ${historicData.second}°C"
                    data.historicMonthlyAverage = historicData.second
                    _resultHistoricAverage.value =
                        "${formatDouble(data.historicMonthlyAverage)}°C"
                } catch (e: Exception) {
                    _resultHistoricAverage.value = "Error"
                    _status.value += "\nError fetching data: ${e.message}"
                }
                Log.i("WeatherAPI", "temp difference: " + data.tempDifference.toString())
            } catch (ex: Exception) {
                _status.value = "City not found"
                setDataValues("N/A")
                    Log.e(
                    "WeatherAPI",
                    ex.message ?: "Error occurred while trying to geocode the location"

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
}

class WeatherViewModelFactory(
    private val weatherRepository: WeatherRepository,
    private val geoCodeRepository: GeoCodeRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(weatherRepository, geoCodeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}



