package com.example.fineweather.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.fineweather.R
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.viewmodels.WeatherViewModel
import java.util.Locale
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    var location by rememberSaveable { mutableStateOf("") }
    var showCurrentMonthWarning by rememberSaveable { mutableStateOf(false) }
    val status by viewModel.status.collectAsState(initial = "Enter a city to see temperature averages")
    val resultForecast by viewModel.resultForecastAverage.collectAsState(initial = "")
    val resultCurrent by viewModel.resultCurrentAverage.collectAsState(initial = "")
    val resultHistoric by viewModel.resultHistoricAverage.collectAsState(initial = "")
    val resultCurrentMonth by viewModel.resultCurrentMonthAverage.collectAsState(initial = "")
    val currentMonthDayCount by viewModel.currentMonthDayCount.collectAsState(initial = 0)
    val apiCallCount by viewModel.apiCallCount.collectAsState(initial = 0)
    val cacheHitCount by viewModel.cacheHitCount.collectAsState(initial = 0)
    val resolvedLocation by viewModel.resolvedLocationName.collectAsState(initial = "")
    val statusCardValue = buildStatusCardValue(resultCurrentMonth, resultHistoric)
    val trendCardValue = buildTrendCardValue(resultForecast, resultCurrentMonth)
    val statusImageRes = resolveStatusImageRes(
        currentMonth = resultCurrentMonth,
        historic = resultHistoric,
        status = status,
    )
    val cardShape = RoundedCornerShape(12.dp)
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val showCurrentMonthWarningIcon = currentMonthDayCount in 0..6 && resultCurrentMonth !== "-"

    LaunchedEffect(resolvedLocation) {
        if (resolvedLocation.isNotBlank() && resolvedLocation != location) {
            location = resolvedLocation
        }
    }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusHeaderCard(
            imageRes = statusImageRes,
            statusCardValue = statusCardValue,
            trendCardValue = trendCardValue,
            shape = cardShape,
        )


        SearchBar(
            location = location,
            onLocationChange = { location = it },
            onSearch = { viewModel.fetchWeather(location) },
            onClear = {
                location = ""
                viewModel.fetchWeather("")
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
        StatusDisplay(status)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            shape = cardShape,
            colors = cardColors,
            modifier = Modifier.fillMaxWidth(),
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
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val temperatureRows =
                    listOf(
                        TemperatureRowData("Last 31 days", resultCurrent, false),
                        TemperatureRowData("This month so far", resultCurrentMonth, showCurrentMonthWarningIcon),
                        TemperatureRowData("Next 7 days (forecast)", resultForecast, false),
                        TemperatureRowData("Historical (30y) for this month", resultHistoric, false),
                    )
                temperatureRows.forEachIndexed { index, row ->
                    TemperatureRow(
                        label = row.label,
                        value = row.value,
                        showWarning = row.showWarning,
                        onWarningClick = if (row.showWarning) { { showCurrentMonthWarning = true } } else null,
                    )
                    if (index != temperatureRows.lastIndex) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                    ),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "API calls: $apiCallCount • Cache hits: $cacheHitCount",
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }

    if (showCurrentMonthWarning) {
        AlertDialog(
            onDismissRequest = { showCurrentMonthWarning = false },
            title = {
                Text(
                    text = "Limited data so far",
                    fontFamily = nunitoSansFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text =
                        if (currentMonthDayCount == 0) {
                            "This month has no data yet. Check back soon for a more stable average."
                        } else {
                            "This month's average is based on $currentMonthDayCount days of data. " +
                                "Values can shift as more data arrives."
                        },
                    fontFamily = nunitoSansFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCurrentMonthWarning = false }) {
                    Text("OK", fontFamily = nunitoSansFamily)
                }
            },
        )
    }
}

@Composable
internal fun SearchBar(
    location: String,
    onLocationChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
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
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("clearButton"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.delete_24),
                    contentDescription = "Clear search",
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("searchField"),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row {
        ElevatedButton(
            onClick = onSearch,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp
            ),
            modifier = Modifier
                .weight(1f),
        ) {
            Text("Get weather averages", fontFamily = nunitoSansFamily)
        }
    }
}

private data class TemperatureRowData(
    val label: String,
    val value: String,
    val showWarning: Boolean,
)

@Composable
private fun TemperatureRow(
    label: String,
    value: String,
    showWarning: Boolean = false,
    onWarningClick: (() -> Unit)? = null,
) {
    val isPlaceholder = isPlaceholderValue(value)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showWarning && onWarningClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.warning),
                    contentDescription = "Limited forecast data",
                    tint = WarningIconTint,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clickable { onWarningClick() },
                )
            }
        }
        Text(
            text = value,
            fontFamily = nunitoSansFamily,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold,
            color = valueColor(value),
        )
    }
}

@Composable
private fun valueColor(value: String): Color {
    return if (isPlaceholderValue(value)) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}

private fun isPlaceholderValue(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isEmpty() ||
            trimmed == "-" ||
            trimmed.equals("N/A", ignoreCase = true) ||
            trimmed.equals("Fetching...", ignoreCase = true) ||
            trimmed.equals("Error", ignoreCase = true) ||
            trimmed.equals("Loading...", ignoreCase = true) ||
            trimmed.equals("No data yet", ignoreCase = true)
}

@Composable
private fun StatusDisplay(status: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            textAlign = TextAlign.Center,
            text = status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EarthStatus(
    imageRes: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = "Status illustration",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun StatusHeaderCard(
    imageRes: Int,
    statusCardValue: ValueWithIcons,
    trendCardValue: ValueWithIcons,
    shape: RoundedCornerShape,
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    shape,
                ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            EarthStatus(
                imageRes = imageRes,
                modifier = Modifier.fillMaxWidth(),
            )
            DividerLine()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
            ) {
                StatusTrendCell(
                    label = "Status",
                    value = statusCardValue,
                    modifier = Modifier.weight(3f),
                )
                VerticalDividerLine()
                StatusTrendCell(
                    label = "Trend",
                    value = trendCardValue,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

@Composable
private fun StatusTrendCell(
    label: String,
    value: ValueWithIcons,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (value.text.isNotBlank()) {
                Text(
                    text = value.text,
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor(value.text),
                    textAlign = TextAlign.End,
                )
            }
            val icons = value.icons
            if (icons != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    repeat(icons.count) { index ->
                        Icon(
                            painter = painterResource(icons.resId),
                            contentDescription = if (index == 0) icons.contentDescription else null,
                            tint = icons.tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    )
}

@Composable
private fun VerticalDividerLine() {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    )
}

private val WarmIconTint = Color(0xFFE53935)
private val CoolIconTint = Color(0xFF1E88E5)
private val NeutralIconTint = Color(0xFF43A047)
private val WarningIconTint = Color(0xFFF9A825)

internal fun buildStatusCardValue(
    currentMonth: String,
    historic: String,
): ValueWithIcons {
    val currentValue = parseTemperature(currentMonth)
    val historicValue = parseTemperature(historic)
    if (currentValue == null || historicValue == null) {
        return ValueWithIcons("-", null)
    }
    val delta = currentValue - historicValue
    val absDelta = abs(delta)
    if (absDelta < 1.0) {
        return ValueWithIcons("Normal (+/- 1°C)", null)
    }
    val count =
        when {
            absDelta < 2.0 -> 1
            absDelta < 3.0 -> 2
            else -> 3
        }
    val (resId, tint, description) =
        if (delta > 0) {
            Triple(R.drawable.fire, WarmIconTint, "Warmer than average")
        } else {
            Triple(R.drawable.frost, CoolIconTint, "Colder than average")
        }
    val formattedDelta = String.format(Locale.US, "%.2f", absDelta)
    val sign = if (delta >= 0) "+" else "-"
    return ValueWithIcons(
        "$sign$formattedDelta°C",
        IconStack(resId = resId, count = count, tint = tint, contentDescription = description),
    )
}

internal fun buildTrendCardValue(
    forecast: String,
    currentMonth: String,
): ValueWithIcons {
    val forecastValue = parseTemperature(forecast)
    val currentMonthValue = parseTemperature(currentMonth)
    if (forecastValue == null || currentMonthValue == null) {
        return ValueWithIcons("-", null)
    }
    val delta = forecastValue - currentMonthValue
    val absDelta = abs(delta)
    if (absDelta < 0.25) {
        return ValueWithIcons(
            text = "",
            icons =
                IconStack(
                    resId = R.drawable.arrow_right,
                    count = 1,
                    tint = NeutralIconTint,
                    contentDescription = "Trend steady",
                ),
        )
    }
    val count =
        when {
            absDelta > 2.25 -> 3
            absDelta > 1.25 -> 2
            else -> 1
        }
    val (resId, tint, description) =
        if (delta > 0) {
            Triple(R.drawable.up, WarmIconTint, "Trending warmer")
        } else {
            Triple(R.drawable.down, CoolIconTint, "Trending cooler")
        }
    return ValueWithIcons(
        text = "",
        icons = IconStack(resId = resId, count = count, tint = tint, contentDescription = description),
    )
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
