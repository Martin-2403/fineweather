package com.example.fineweather

import WeatherRepository
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.api.RetrofitClient
import com.example.fineweather.data.repositories.StationRepository
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.viewmodels.WeatherViewModel
import com.example.fineweather.viewmodels.WeatherViewModelFactory

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FineWeatherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeatherApp()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherApp() {
    val weatherRepository = remember { WeatherRepository(RetrofitClient.client.create(OpenMeteoApiService::class.java)) }
    val stationRepository = remember { StationRepository() }

    val viewModel: WeatherViewModel = viewModel(factory = WeatherViewModelFactory(weatherRepository, stationRepository))

    var location by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val result by viewModel.result.collectAsState(initial = "Enter a city to get weather data")

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchBar(location, onLocationChange = { location = it }) {
            viewModel.fetchWeather(context, location)
            viewModel.getStation(context, location)
        }
        Spacer(modifier = Modifier.height(16.dp))
        WeatherDisplay(result)
    }
}

@Composable
fun SearchBar(location: String, onLocationChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = location,
        onValueChange = onLocationChange,
        label = { Text("Enter city") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onSearch,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Search city")
    }
}

@Composable
fun WeatherDisplay(result: String) {
    Text(
        textAlign = TextAlign.Center,
        text = result,
        modifier = Modifier.fillMaxWidth()
    )
}
