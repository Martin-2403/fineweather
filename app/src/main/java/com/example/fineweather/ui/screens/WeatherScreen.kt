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
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.fineweather.R
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.viewmodels.WeatherViewModel
import java.util.Locale
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier,
) {
    var location by rememberSaveable { mutableStateOf("") }
    var showCurrentMonthWarning by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(WeatherTab.RESULTS) }
    val status by viewModel.status.collectAsState(initial = "Enter a location to see temperature averages")
    val resultForecast by viewModel.resultForecastAverage.collectAsState(initial = "")
    val resultCurrent by viewModel.resultCurrentAverage.collectAsState(initial = "")
    val resultHistoric by viewModel.resultHistoricAverage.collectAsState(initial = "")
    val resultCurrentMonth by viewModel.resultCurrentMonthAverage.collectAsState(initial = "")
    val forecastDays by viewModel.forecastDays.collectAsState(
        initial = WeatherViewModel.FORECAST_DAYS_SHORT,
    )
    val currentMonthDayCount by viewModel.currentMonthDayCount.collectAsState(initial = 0)
    val apiCallCount by viewModel.apiCallCount.collectAsState(initial = 0)
    val cacheHitCount by viewModel.cacheHitCount.collectAsState(initial = 0)
    val resolvedLocation by viewModel.resolvedLocationName.collectAsState(initial = "")
    val places by viewModel.places.collectAsState(initial = emptyList())
    val favoritePlace by viewModel.favorite.collectAsState(initial = null)
    val selectedPlace by viewModel.selectedPlace.collectAsState(initial = null)
    val isFavoriteSelected = favoritePlace?.id != null && favoritePlace?.id == selectedPlace?.id
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
    val showCurrentMonthWarningIcon =
        currentMonthDayCount in 0..6
                && resultCurrentMonth !== "-"
                && resultCurrentMonth !== "N/A"


    LaunchedEffect(resolvedLocation) {
        if (resolvedLocation.isNotBlank() && resolvedLocation != location) {
            location = resolvedLocation
        }
    }

    Column(
        modifier =
            modifier
                .padding(12.dp)
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

        Spacer(modifier = Modifier.height(8.dp))

        SearchBar(
            location = location,
            onLocationChange = { location = it },
            onSearch = {
                selectedTab = WeatherTab.RESULTS
                viewModel.fetchWeather(location)
            },
            onClear = {
                location = ""
                selectedTab = WeatherTab.RESULTS
                viewModel.fetchWeather("")
            },
            isFavorite = isFavoriteSelected,
            canFavorite = selectedPlace != null,
            onToggleFavorite = { viewModel.toggleFavorite() },
        )

        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WeatherTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index, WeatherTab.entries.size),
                    icon = {},
                    contentPadding = SegmentedButtonDefaults.ContentPadding

                ) {
                    Text(
                        text = tab.label,
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (selectedTab) {
            WeatherTab.RESULTS -> {
                Card(
                    shape = cardShape,
                    colors = cardColors,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                cardShape,
                            ),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusDisplay(status)
                        DividerLine()
                        Text(
                            "Average temperatures",
                            fontFamily = nunitoSansFamily,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val temperatureRows =
                            listOf(
                                TemperatureRowData("Last 31 days", resultCurrent, false),
                                TemperatureRowData(
                                    "This month so far",
                                    resultCurrentMonth,
                                    showCurrentMonthWarningIcon,
                                ),
                                TemperatureRowData(
                                    "Next $forecastDays days (forecast)",
                                    resultForecast,
                                    false,
                                ),
                                TemperatureRowData(
                                    "Historical (30y) for this month",
                                    resultHistoric,
                                    false,
                                ),
                            )
                        temperatureRows.forEachIndexed { index, row ->
                            TemperatureRow(
                                label = row.label,
                                value = row.value,
                                showWarning = row.showWarning,
                                onWarningClick = if (row.showWarning) {
                                    { showCurrentMonthWarning = true }
                                } else null,
                            )
                            if (index != temperatureRows.lastIndex) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.15f
                                                ),
                                            ),
                                )
                            }
                        }
                    }
                }
            }

            WeatherTab.FAVORITE -> {
                if (favoritePlace == null) {
                    EmptySectionCard(
                        title = "Favorite",
                        message = "No favorite yet. Tap the star next to the search bar to save one.",
                        shape = cardShape,
                        colors = cardColors,
                    )
                } else {
                    PlacesCard(
                        title = "Favorite",
                        places = listOf(favoritePlace) as List<GeoPlace>,
                        onPlaceClick = { place ->
                            selectedTab = WeatherTab.RESULTS
                            viewModel.selectPlace(place)
                        },
                        shape = cardShape,
                        colors = cardColors,
                    )
                }
            }

            WeatherTab.PLACES -> {
                if (places.isEmpty()) {
                    EmptySectionCard(
                        title = "Places",
                        message = "Search to see alternate matches here.",
                        shape = cardShape,
                        colors = cardColors,
                    )
                } else {
                    PlacesCard(
                        title = "Places",
                        places = places,
                        onPlaceClick = { place ->
                            selectedTab = WeatherTab.RESULTS
                            viewModel.selectPlace(place)
                        },
                        shape = cardShape,
                        colors = cardColors,
                    )
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
    isFavorite: Boolean,
    canFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        leadingIcon = {
            IconButton(
                onClick = onToggleFavorite,
                enabled = canFavorite,
                modifier = Modifier.testTag("favoriteButton"),
            ) {
                val starRes =
                    if (isFavorite) {
                        R.drawable.star_filled
                    } else {
                        R.drawable.star_outline
                    }
                val tint =
                    if (canFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                Icon(
                    painter = painterResource(id = starRes),
                    contentDescription = "Toggle favorite",
                    tint = tint,
                )
            }
        },
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
                .fillMaxWidth(),
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

private enum class WeatherTab(val label: String) {
    RESULTS("Results"),
    FAVORITE("Favorite"),
    PLACES("Places"),
}

@Composable
private fun StatusDisplay(status: String) {
    val bannerShape = RoundedCornerShape(10.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface, bannerShape)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    bannerShape,
                )
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.background,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun EmptySectionCard(
    title: String,
    message: String,
    shape: RoundedCornerShape,
    colors: CardColors,
) {
    Card(
        shape = shape,
        colors = colors,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    shape,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlacesCard(
    title: String,
    places: List<GeoPlace>,
    onPlaceClick: (GeoPlace) -> Unit,
    shape: RoundedCornerShape,
    colors: CardColors,
) {
    Card(
        shape = shape,
        colors = colors,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    shape,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DividerLine()
            places.forEachIndexed { index, place ->
                PlaceListItem(place = place, onClick = { onPlaceClick(place) })
                if (index != places.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun PlaceListItem(
    place: GeoPlace,
    onClick: () -> Unit,
) {
    val subtitle = place.displaySubtitle()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.displayName(),
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.arrow_right),
            contentDescription = "Select place",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                //.height(140.dp),
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
        modifier = modifier.padding(10.dp),
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
private val WarningIconTint = Color(0xFFE18D10)

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
        icons = IconStack(
            resId = resId,
            count = count,
            tint = tint,
            contentDescription = description
        ),
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
