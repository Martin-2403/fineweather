package com.example.fineweather.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.viewmodels.WeatherViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
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

        SearchBar(
            location = location,
            onLocationChange = { location = it },
            onSearch = { viewModel.fetchWeather(location) },
        )

        Spacer(modifier = Modifier.height(16.dp))
        StatusDisplay(status)
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
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
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                WeatherDisplay(resultCurrent)
                WeatherDisplay(resultCurrentMonth)
                WeatherDisplay(resultForecast)
                WeatherDisplay(resultHistoric)
            }
        }
    }
}

@Composable
private fun SearchBar(
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
private fun WeatherDisplay(result: String) {
    Text(
        textAlign = TextAlign.Center,
        text = result,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = nunitoSansFamily,
    )
}

@Composable
private fun StatusDisplay(status: String) {
    Text(
        textAlign = TextAlign.Center,
        text = status,
        modifier = Modifier.fillMaxWidth(),
        fontFamily = nunitoSansFamily,
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun EarthStatus() {
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
                    .padding(0.dp),
        )
    }
}
