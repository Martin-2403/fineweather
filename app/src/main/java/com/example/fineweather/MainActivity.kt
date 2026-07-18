package com.example.fineweather

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fineweather.api.OpenMeteoRetrofitClients
import com.example.fineweather.data.local.settingsDataStore
import com.example.fineweather.data.local.WeatherDatabase
import com.example.fineweather.data.repositories.GeoCodeRepository
import com.example.fineweather.data.repositories.DataStoreSettingsRepository
import com.example.fineweather.data.repositories.RoomFavoriteRepository
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
    val favoriteRepository =
        remember {
            RoomFavoriteRepository(db.favoriteDao())
        }

    val weatherViewModel: WeatherViewModel =
        viewModel(
            factory =
                WeatherViewModelFactory(
                    weatherRepository,
                    geoCodeRepository,
                    settingsRepository,
                    favoriteRepository,
                ),
        )
    val settingsViewModel: SettingsViewModel =
        viewModel(
            factory = SettingsViewModelFactory(settingsRepository),
        )
    val forecastDays by settingsViewModel.forecastDays.collectAsState(
        initial = SettingsDefaults.DEFAULT_FORECAST_DAYS,
    )
    val historicReference by settingsViewModel.historicReference.collectAsState(
        initial = SettingsDefaults.DEFAULT_HISTORIC_REFERENCE,
    )
    val searchLanguage by settingsViewModel.searchLanguage.collectAsState(
        initial = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
    )
    val temperatureComparisonMode by settingsViewModel.temperatureComparisonMode.collectAsState(
        initial = SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
    )
    val hasSeenInfo by settingsViewModel.hasSeenInfo.collectAsState(initial = null)
    var showInfo by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(hasSeenInfo) {
        if (hasSeenInfo == false) {
            showInfo = true
        } else if (hasSeenInfo == true) {
            showInfo = false
        }
    }

    Scaffold(
        topBar = {
            when (currentScreen) {
                AppScreen.WEATHER ->
                    CompactTopBar(
                        title = "FineWeather",
                        actionIconRes = R.drawable.settings,
                        actionContentDescription = "Open settings",
                        actionTestTag = "settingsButton",
                        onAction = { currentScreen = AppScreen.SETTINGS },
                        secondaryActionIconRes = R.drawable.info,
                        secondaryActionContentDescription = "Open info",
                        secondaryActionTestTag = "infoButton",
                        onSecondaryAction = { showInfo = true },
                    )

                AppScreen.SETTINGS ->
                    CompactTopBar(
                        title = "Settings",
                        actionIconRes = R.drawable.search,
                        actionContentDescription = "Back to weather",
                        actionTestTag = "backToWeatherButton",
                        onAction = { currentScreen = AppScreen.WEATHER },
                    )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LaunchedEffect(snackbarHostState) {
            weatherViewModel.snackbarMessages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }
        if (currentScreen == AppScreen.SETTINGS) {
            BackHandler {
                currentScreen = AppScreen.WEATHER
            }
        }
        when (currentScreen) {
            AppScreen.WEATHER ->
                WeatherScreen(
                    viewModel = weatherViewModel,
                    showInfo = showInfo,
                    onDismissInfo = {
                        showInfo = false
                        settingsViewModel.setHasSeenInfo(true)
                    },
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                )

            AppScreen.SETTINGS ->
                SettingsScreen(
                    forecastDays = forecastDays,
                    historicReference = historicReference,
                    searchLanguage = searchLanguage,
                    temperatureComparisonMode = temperatureComparisonMode,
                    onForecastDaysChange = settingsViewModel::setForecastDays,
                    onHistoricReferenceChange = settingsViewModel::setHistoricReference,
                    onSearchLanguageChange = settingsViewModel::setSearchLanguage,
                    onTemperatureComparisonModeChange = settingsViewModel::setTemperatureComparisonMode,
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                )
        }
    }
}

private enum class AppScreen {
    WEATHER,
    SETTINGS,
}

@Composable
private fun CompactTopBar(
    title: String,
    actionIconRes: Int,
    actionContentDescription: String,
    actionTestTag: String,
    onAction: () -> Unit,
    secondaryActionIconRes: Int? = null,
    secondaryActionContentDescription: String? = null,
    secondaryActionTestTag: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (secondaryActionIconRes != null && onSecondaryAction != null) {
                IconButton(
                    onClick = onSecondaryAction,
                    modifier =
                        if (secondaryActionTestTag != null) {
                            Modifier.testTag(secondaryActionTestTag)
                        } else {
                            Modifier
                        },
                ) {
                    Icon(
                        painter = painterResource(id = secondaryActionIconRes),
                        contentDescription = secondaryActionContentDescription,
                    )
                }
            }
            IconButton(
                onClick = onAction,
                modifier = Modifier.testTag(actionTestTag),
            ) {
                Icon(
                    painter = painterResource(id = actionIconRes),
                    contentDescription = actionContentDescription,
                )
            }
        }
    }
}
