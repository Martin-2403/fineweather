package com.example.fineweather

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fineweather.api.OpenMeteoRetrofitClients
import com.example.fineweather.data.local.settingsDataStore
import com.example.fineweather.data.local.WeatherDatabase
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.DataStoreSettingsRepository
import com.example.fineweather.data.repositories.SettingsDefaults
import com.example.fineweather.data.repositories.SettingsRepository
import com.example.fineweather.data.repositories.WeatherRepository
import com.example.fineweather.ui.screens.SettingsScreen
import com.example.fineweather.ui.screens.WeatherScreen
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.viewmodels.SettingsViewModel
import com.example.fineweather.viewmodels.SettingsViewModelFactory
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

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherApp() {
    val context = LocalContext.current
    val db = WeatherDatabase.getDatabase(context)
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.WEATHER) }

    val weatherRepository =
        remember {
            WeatherRepository(
                openMeteoWeatherApi = OpenMeteoRetrofitClients.forecastApi,
                openMeteoArchiveApi = OpenMeteoRetrofitClients.archiveApi,
                weatherDao = db.weatherDao(),
            )
        }
    val geoCodeRepository =
        remember {
            GeoCodeRepository(
                openMeteoGeoCodeApi = OpenMeteoRetrofitClients.geocodingApi,
                geoCodeDao = db.geoCodeDao(),
            )
        }
    val settingsRepository: SettingsRepository =
        remember {
            DataStoreSettingsRepository(context.settingsDataStore)
        }

    val weatherViewModel: WeatherViewModel =
        viewModel(
            factory =
                WeatherViewModelFactory(
                    weatherRepository,
                    geoCodeRepository,
                    settingsRepository,
                ),
        )
    val settingsViewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(settingsRepository),
        )
    val forecastDays by settingsViewModel.forecastDays.collectAsState(
        initial = SettingsDefaults.DEFAULT_FORECAST_DAYS,
    )

    Scaffold(
        topBar = {
            when (currentScreen) {
                AppScreen.WEATHER ->
                    TopAppBar(
                        title = {
                            Text(
                                text = "FineWeather",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { currentScreen = AppScreen.SETTINGS },
                                modifier = Modifier.testTag("settingsButton"),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.settings),
                                    contentDescription = "Open settings",
                                )
                            }
                        },
                    )

                AppScreen.SETTINGS ->
                    TopAppBar(
                        title = {
                            Text(
                                text = "Settings",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { currentScreen = AppScreen.WEATHER },
                                modifier = Modifier.testTag("backToWeatherButton"),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.search),
                                    contentDescription = "Back to weather",
                                )
                            }
                        },
                    )
            }
        },
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.WEATHER ->
                WeatherScreen(
                    viewModel = weatherViewModel,
                    modifier = Modifier.padding(innerPadding),
                )

            AppScreen.SETTINGS ->
                SettingsScreen(
                    forecastDays = forecastDays,
                    onForecastDaysChange = settingsViewModel::setForecastDays,
                    modifier = Modifier.padding(innerPadding),
                )
        }
    }
}

private enum class AppScreen {
    WEATHER,
    SETTINGS,
}
