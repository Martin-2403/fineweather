package com.example.fineweather.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
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
import java.util.Locale
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    var location by rememberSaveable { mutableStateOf("") }
    val status by viewModel.status.collectAsState(initial = "Enter a city to see temperature averages")
    val resultForecast by viewModel.resultForecastAverage.collectAsState(initial = "")
    val resultCurrent by viewModel.resultCurrentAverage.collectAsState(initial = "")
    val resultHistoric by viewModel.resultHistoricAverage.collectAsState(initial = "")
    val resultCurrentMonth by viewModel.resultCurrentMonthAverage.collectAsState(initial = "")
    val statusCardText = buildStatusCardText(resultCurrentMonth, resultHistoric)
    val trendCardText = buildTrendCardText(resultForecast, resultCurrentMonth)
    val statusImageRes = resolveStatusImageRes(
        currentMonth = resultCurrentMonth,
        historic = resultHistoric,
        status = status,
    )

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EarthStatus(statusImageRes)
//      Row {
//         NumberDropdown(label = "Time in years", onSelected = { selectedTimeframe = it })
//      }
        Row {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .weight(3f)
                    .padding(vertical = 2.dp)
                    .border(
                        1.dp, MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.shapes.medium
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Status:",
                        fontFamily = nunitoSansFamily,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .padding(2.dp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = statusCardText,
                        fontFamily = nunitoSansFamily,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .padding(2.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,

                    ),
                modifier = Modifier
                    .weight(2f)
                    .padding(vertical = 2.dp)
                    .border(
                        1.dp, MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.shapes.medium
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Trend:",
                        fontFamily = nunitoSansFamily,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .padding(2.dp),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.weight(1f))
                    Text(
                        text = trendCardText,
                        fontFamily = nunitoSansFamily,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .padding(2.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }


        SearchBar(
            location = location,
            onLocationChange = { location = it },
            onSearch = { viewModel.fetchWeather(location) },
        )

        Spacer(modifier = Modifier.height(8.dp))
        StatusDisplay(status)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.shapes.medium
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
            ) {
                Text(
                    "Average temperatures",
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TemperatureRow(label = "Last 31 days", value = resultCurrent)
                TemperatureRow(label = "This month so far", value = resultCurrentMonth)
                TemperatureRow(label = "Next 14 days (forecast)", value = resultForecast)
                TemperatureRow(label = "Historical (30y) for this month", value = resultHistoric)
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
                "City or place",
                fontFamily = nunitoSansFamily,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    ElevatedButton(
        onClick = onSearch,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Get weather averages", fontFamily = nunitoSansFamily)
    }
}

@Composable
private fun TemperatureRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = nunitoSansFamily,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = nunitoSansFamily,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatusDisplay(status: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp, MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.shapes.medium
            ),
    ) {
        Text(
            textAlign = TextAlign.Center,
            text = status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            fontFamily = nunitoSansFamily,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EarthStatus(imageRes: Int) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = "Status illustration",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
        )
    }
}

internal fun buildStatusCardText(
    currentMonth: String,
    historic: String,
): String {
    val currentValue = parseTemperature(currentMonth)
    val historicValue = parseTemperature(historic)
    if (currentValue == null || historicValue == null) {
        return "-"
    }
    val delta = currentValue - historicValue
    val absDelta = abs(delta)
    if (absDelta < 1.0) {
        return "Normal (+/- 1°C)"
    }
    val emoji = if (delta > 0) {
        when {
            absDelta < 2.0 -> "🔥"
            absDelta < 3.0 -> "🔥🔥"
            else -> "🔥🔥🔥"
        }
    } else {
        when {
            absDelta < 2.0 -> "❄️"
            absDelta < 3.0 -> "❄️❄️"
            else -> "❄️❄️❄️"
        }
    }
    val formattedDelta = String.format(Locale.US, "%.2f", absDelta)
    val sign = if (delta >= 0) "+" else "-"
    return "$sign$formattedDelta°C $emoji"
}

internal fun buildTrendCardText(
    forecast: String,
    currentMonth: String,
): String {
    val forecastValue = parseTemperature(forecast)
    val currentMonthValue = parseTemperature(currentMonth)
    if (forecastValue == null || currentMonthValue == null) {
        return "-"
    }
    val delta = forecastValue - currentMonthValue
    val absDelta = abs(delta)
    if (absDelta < 0.25) {
        return "-"
    }
    val emoji = if (delta > 0) {
        when {
            absDelta > 2.25 -> "🔺🔺🔺"
            absDelta > 1.25 -> "🔺🔺"
            absDelta > 0.25 -> "🔺"
            else -> "-"
        }
    } else {
        when {
            absDelta > 2.25 -> "\uD83D\uDD3B\uD83D\uDD3B\uD83D\uDD3B"
            absDelta > 1.25 -> "\uD83D\uDD3B\uD83D\uDD3B"
            absDelta > 0.25 -> "\uD83D\uDD3B"
            else -> "-"
        }
    }
    return emoji
}

internal fun parseTemperature(value: String): Double? {
    val match = Regex("[-+]?\\d+(?:\\.\\d+)?").find(value) ?: return null
    return match.value.toDoubleOrNull()
}

internal fun resolveStatusImageRes(
    currentMonth: String,
    historic: String,
    status: String,
): Int {
    val currentValue = parseTemperature(currentMonth)
    val historicValue = parseTemperature(historic)
    val isInitial =
        currentValue == null &&
                historicValue == null &&
                status.startsWith("Enter a city", ignoreCase = true)

    if (isInitial) {
        return R.drawable.initial
    }
    if (currentValue == null || historicValue == null) {
        return R.drawable.initial
    }
    val delta = currentValue - historicValue
    return when {
        delta > 1 -> R.drawable.warm
        delta < -1 -> R.drawable.cold
        else -> R.drawable.equal
    }
}
