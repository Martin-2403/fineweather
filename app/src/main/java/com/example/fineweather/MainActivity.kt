package com.example.fineweather

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fineweather.api.OpenMeteoRetrofitClients
import com.example.fineweather.data.local.WeatherDatabase
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.ui.screens.WeatherScreen
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
    val context = LocalContext.current
    val db = WeatherDatabase.getDatabase(context)

    val weatherRepository =
        remember {
            WeatherRepository(
                openMeteoWeatherApi = OpenMeteoRetrofitClients.forecastApi,
                openMeteoArchiveApi = OpenMeteoRetrofitClients.archiveApi,
                weatherDao = db.weatherDao(),
            )
        }
    val geoCodeRepository =
        remember { GeoCodeRepository(openMeteoGeoCodeApi = OpenMeteoRetrofitClients.geocodingApi) }

    val viewModel: WeatherViewModel =
        viewModel(factory = WeatherViewModelFactory(weatherRepository, geoCodeRepository))

    WeatherScreen(viewModel = viewModel)
}
