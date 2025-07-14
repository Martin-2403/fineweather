package com.example.fineweather

import com.example.fineweather.data.repositories.WeatherRepository
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
import com.example.fineweather.api.OpenMeteoRetrofitClient
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
    val context = LocalContext.current
    val weatherRepository =
        remember { WeatherRepository(OpenMeteoRetrofitClient.client.create(OpenMeteoApiService::class.java)) }
    val stationRepository = remember { StationRepository(context) }

    val viewModel: WeatherViewModel =
        viewModel(factory = WeatherViewModelFactory(weatherRepository, stationRepository))

    var location by rememberSaveable { mutableStateOf("") }
    var selectedTimeframe by remember { mutableStateOf(10) }
    val result by viewModel.resultForecastAverage.collectAsState(initial = "Enter a city to get weather data")
    val stations by viewModel.stations.collectAsState(initial = "Known stations are being displayed here")

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            NumberDropdown(label = "Time in years", onSelected = { selectedTimeframe = it })
        }
        SearchBar(location, onLocationChange = { location = it }) {
            viewModel.fetchWeather(context, location)
            viewModel.getStation(context, location)
        }
        Spacer(modifier = Modifier.height(16.dp))
        WeatherDisplay(result)
        HistoricWeatherDisplay(result)
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

@Composable
fun HistoricWeatherDisplay(result: String) {
    Text(
        textAlign = TextAlign.Center,
        text = result,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun StationDisplay(stations: String) {
    Text(
        textAlign = TextAlign.Center,
        text = stations,
        modifier = Modifier.fillMaxWidth()
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberDropdown(
    label: String = "Select number",
    options: List<Int> = listOf(10, 20, 30),
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(options.first().toString()) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            readOnly = true,
            value = selectedOptionText,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { number ->
                DropdownMenuItem(
                    text = { Text(number.toString()) },
                    onClick = {
                        selectedOptionText = number.toString()
                        expanded = false
                        onSelected(number)
                    }
                )
            }
        }
    }
}

