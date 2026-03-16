package com.example.fineweather

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fineweather.api.OpenMeteoRetrofitClients
import com.example.fineweather.data.local.WeatherDatabase
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.viewmodels.WeatherViewModel
import com.example.fineweather.viewmodels.WeatherViewModelFactory

val nunitoSansFamily =
    FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal),
    )

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FineWeatherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
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
    LocalContext.current
    val db = WeatherDatabase.getDatabase(LocalContext.current)

    val weatherRepository =
        remember {
            WeatherRepository(
                openMeteoWeatherApi = OpenMeteoRetrofitClients.forecastApi,
                openMeteoArchiveApi = OpenMeteoRetrofitClients.archiveApi,
                weatherDao = db.weatherDao()

            )
        }
    val geoCodeRepository =
        remember { GeoCodeRepository(openMeteoGeoCodeApi = OpenMeteoRetrofitClients.geocodingApi) }

    val viewModel: WeatherViewModel =
        viewModel(factory = WeatherViewModelFactory(weatherRepository, geoCodeRepository))

    var location by rememberSaveable { mutableStateOf("") }
    val status by viewModel.status.collectAsState(initial = "Enter city name to get weather data")
    val resultForecast by viewModel.resultForecastAverage.collectAsState(initial = "")
    val resultCurrent by viewModel.resultCurrentAverage.collectAsState(initial = "")
    val resultHistoric by viewModel.resultHistoricAverage.collectAsState(initial = "")
    val resultCurrentMonth by viewModel.resultCurrentMonthAverage.collectAsState(initial = "")

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EarthStatus()
//      Row {
//         NumberDropdown(label = "Time in years", onSelected = { selectedTimeframe = it })
//      }
        SearchBar(location, onLocationChange = { location = it }) {
            viewModel.fetchWeather(location)
        }
        Spacer(modifier = Modifier.height(16.dp))
        StatusDisplay(status)
        Row {
            Column {
                Text(
                    "31 day average:",
                    fontFamily = nunitoSansFamily,
                )
                Text(
                    "Average current month:",
                    fontFamily = nunitoSansFamily,
                )
                Text(
                    "14 day forecast average:",
                    fontFamily = nunitoSansFamily,
                )
                Text(
                    "Historic monthly temperature:",
                    fontFamily = nunitoSansFamily,
                )
            }
            Column {
                WeatherDisplay(resultCurrent)
                WeatherDisplay(resultCurrentMonth)
                WeatherDisplay(resultForecast)
                WeatherDisplay(resultHistoric)
            }
        }
    }
}

@Composable
fun SearchBar(
    location: String,
    onLocationChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = location,
        onValueChange = onLocationChange,
        label = {
            Text(
                "Enter city",
                fontFamily = nunitoSansFamily,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = onSearch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Search city", fontFamily = nunitoSansFamily)
    }
}

@Composable
fun WeatherDisplay(result: String) {
    Text(
        textAlign = TextAlign.Center,
        text = result,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = nunitoSansFamily,
    )
}

@Composable
fun StatusDisplay(status: String) {
    Text(
        textAlign = TextAlign.Center,
        text = status,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = nunitoSansFamily,
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun EarthStatus() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(R.drawable.initial),
            contentDescription = "Picture of the earth looking questioning at different weather types",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(0.dp), // Remove any padding from the Image composable
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberDropdown(
    label: String = "Select number",
    options: List<Int> = listOf(10, 20, 30),
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(options.first().toString()) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        TextField(
            readOnly = true,
            value = selectedOptionText,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { number ->
                DropdownMenuItem(
                    text = { Text(number.toString()) },
                    onClick = {
                        selectedOptionText = number.toString()
                        expanded = false
                        onSelected(number)
                    },
                )
            }
        }
    }
}
