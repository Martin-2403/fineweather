package com.example.fineweather.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.fineweather.viewmodels.PlaceLabel
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.example.fineweather.R
import com.example.fineweather.nunitoSansFamily
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.ui.theme.HazyBlue
import com.example.fineweather.ui.theme.LightTeal
import com.example.fineweather.ui.theme.SoftMist
import com.example.fineweather.ui.theme.TealInk
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.ReportRow
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.WeatherViewModel
import com.example.fineweather.viewmodels.valueOrNull

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    showInfo: Boolean,
    onDismissInfo: () -> Unit,
    showFavorites: Boolean,
    onDismissFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var location by rememberSaveable { mutableStateOf("") }
    var showCurrentMonthWarning by rememberSaveable { mutableStateOf(false) }
    var showAlternatives by rememberSaveable { mutableStateOf(false) }
    var timeScale by rememberSaveable { mutableStateOf(TimeScale.CURRENT) }
    val state by viewModel.state.collectAsState()
    val report by viewModel.report.collectAsState()
    val forecastDays by viewModel.forecastDays.collectAsState(
        initial = WeatherViewModel.FORECAST_DAYS_SHORT,
    )
    val apiCallCount by viewModel.apiCallCount.collectAsState(initial = 0)
    val cacheHitCount by viewModel.cacheHitCount.collectAsState(initial = 0)
    val favorites by viewModel.favorites.collectAsState(initial = emptyList())
    val selectedPlace = state.selectedPlace
    val placeLabel = (state.search as? SearchStatus.Resolved)?.label
    val resolvedLocation = selectedPlace?.name.orEmpty()
    val isFavoriteSelected = favorites.any { it.id == selectedPlace?.id }
    // Other matches of the last search; only meaningful while the shown place came from that search.
    val alternatives =
        if (state.places.any { it.id == selectedPlace?.id }) {
            state.places.filter { it.id != selectedPlace?.id }
        } else {
            emptyList()
        }
    val statusCardValue = buildStatusCardValue(report.headline)
    val trendCardValue = buildTrendCardValue(report.trend)
    val cardShape = RoundedCornerShape(12.dp)
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current


    LaunchedEffect(resolvedLocation) {
        if (resolvedLocation.isNotBlank() && resolvedLocation != location) {
            location = resolvedLocation
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            SearchBar(
                location = location,
                onLocationChange = { location = it },
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.fetchWeather(location)
                },
                onClear = {
                    keyboardController?.hide()
                    location = ""
                    viewModel.fetchWeather("")
                },
                isFavorite = isFavoriteSelected,
                canFavorite = selectedPlace != null,
                onToggleFavorite = { viewModel.toggleFavorite() },
            )

            Spacer(modifier = Modifier.height(4.dp))

            LocationStatus(
                status = state.search.displayText(),
                label = placeLabel,
                onShowAlternatives = if (alternatives.isNotEmpty()) ({
                    showAlternatives = true
                }) else null,
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatusHeaderCard(
                imageRes = resolveStatusImageRes(report.picture),
                summary = report.summary.toBlocks(),
                statusCardValue = statusCardValue,
                trendCardValue = trendCardValue,
                shape = cardShape,
            )

            Spacer(modifier = Modifier.height(8.dp))

            UnderlineTabs(
                labels = TimeScale.entries.map { it.label },
                selectedIndex = timeScale.ordinal,
                onSelect = { timeScale = TimeScale.entries[it] },
                tagPrefix = "timeScale",
            )

            // The tab bar sits flush on the card, so the card's top edge doubles as its baseline.
            val attachedShape =
                cardShape.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))

            when (timeScale) {
                TimeScale.CURRENT ->
                    CurrentView(
                        rows = report.rows,
                        shape = attachedShape,
                        colors = cardColors,
                        onWarningClick = { showCurrentMonthWarning = true },
                    )

                TimeScale.MONTH ->
                    MonthView(
                        snapshot = report.snapshot.valueOrNull(),
                        forecastDays = forecastDays,
                        referenceLabel = report.referenceLabel,
                        shape = attachedShape,
                        colors = cardColors,
                        loading = report.snapshot is Loadable.Loading,
                        failed = report.snapshot is Loadable.Failed,
                    )

                TimeScale.YEAR ->
                    YearView(
                        snapshot = report.snapshot.valueOrNull(),
                        referenceLabel = report.referenceLabel,
                        shape = attachedShape,
                        colors = cardColors,
                        loading = report.snapshot is Loadable.Loading,
                        failed = report.snapshot is Loadable.Failed,
                    )
            }


            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "API calls: $apiCallCount • Cache hits: $cacheHitCount",
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
    if (showFavorites) {
        PlacesSheet(
            title = "Favorites",
            places = favorites,
            emptyMessage = "No favorites yet. Tap the star next to the search bar to save a place.",
            onSelect = { place ->
                onDismissFavorites()
                viewModel.selectPlace(place)
            },
            onDismiss = onDismissFavorites,
            onRemove = { place -> viewModel.removeFavorite(place.id) },
        )
    }
    if (showAlternatives) {
        PlacesSheet(
            title = "Other matches",
            places = alternatives,
            emptyMessage = "No other matches for this search.",
            onSelect = { place ->
                showAlternatives = false
                viewModel.selectPlace(place)
            },
            onDismiss = { showAlternatives = false },
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
                        if (report.dayCount == 0) {
                            "This month has no data yet. Check back soon for a more stable average."
                        } else {
                            "This month's average is based on ${report.dayCount} days of data. " +
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

    if (showInfo) {
        InfoOverlay(
            onDismiss = onDismissInfo,
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
        placeholder = {
            Text(
                "City or place",
                fontFamily = nunitoSansFamily,
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
        trailingIcon = { SearchActions(onClear = onClear, onSearch = onSearch) },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("searchField"),
    )
}

/** One pill inside the search field: delete on the left half, search on the right half. */
@Composable
private fun SearchActions(
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier =
            Modifier
                .padding(end = 10.dp)
                .height(38.dp)
                .clip(shape)
                .border(1.dp, outline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = onClear)
                    .testTag("clearButton"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.delete_24),
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(outline),
        )
        Box(
            modifier =
                Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onSearch)
                    .testTag("searchButton"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CurrentView(
    rows: List<ReportRow>,
    shape: RoundedCornerShape,
    colors: CardColors,
    onWarningClick: () -> Unit,
) {
    ViewCard(shape, colors, Modifier.testTag("currentView")) {
        Text(
            "Average temperatures",
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        rows.forEachIndexed { index, row ->
            TemperatureRow(
                label = row.label,
                value = row.value,
                showWarning = row.showWarning,
                onWarningClick = if (row.showWarning) onWarningClick else null,
            )
            if (index != rows.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                )
            }
        }
    }
}

@Composable
private fun TemperatureRow(
    label: String,
    value: Loadable<Double>,
    showWarning: Boolean = false,
    onWarningClick: (() -> Unit)? = null,
) {
    val isPlaceholder = value.isPlaceholder()
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
            text = value.displayText(),
            fontFamily = nunitoSansFamily,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.SemiBold,
            color = valueColor(isPlaceholder),
        )
    }
}

@Composable
private fun valueColor(placeholder: Boolean): Color {
    return if (placeholder) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
}

/**
 * Place name on the first line (shortened when too long), the coordinates on the second.
 * [onShowAlternatives] adds a "Not your city?" link after the coordinates when the search had
 * other matches.
 */
@Composable
internal fun LocationStatus(
    status: String,
    label: PlaceLabel?,
    onShowAlternatives: (() -> Unit)? = null,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyMedium
    if (label == null) {
        Text(
            text = status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("locationStatus"),
            fontFamily = nunitoSansFamily,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("locationStatus"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontFamily = nunitoSansFamily,
                        style = style,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "(${label.coordinates})",
                        fontFamily = nunitoSansFamily,
                        style = style,
                        color = color.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (onShowAlternatives != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Not your city?",
                            modifier =
                                Modifier
                                    .clickable(role = Role.Button, onClick = onShowAlternatives)
                                    .padding(vertical = 6.dp)
                                    .testTag("alternativesLink"),
                            fontFamily = nunitoSansFamily,
                            style = style,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

            }
        }
    }
}

@Composable
internal fun InfoOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(0.8f)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .padding(25.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .verticalScrollbar(scrollState, MaterialTheme.colorScheme.onSurfaceVariant)
                        .padding(16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Welcome to FineWeather",
                            fontFamily = nunitoSansFamily,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("infoCloseButton"),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = "Close",
                            )
                        }
                    }
                    EarthStatusStack()
                    Text(
                        text = "FineWeather compares recent temperature averages with historic averages and the forecast average so you can watch climate changes in real time. \n\nFeatures:",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.padding(start = 0.dp)) {
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Search a city or place to see the temperature averages of the last 31 days, this month so far (up to yesterday), and the forecast average temperature.",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "The divergence compares this month so far with exactly the same days of the 30 year baseline, " +
                                        "and Trend shows whether the coming days are forecast to be warmer or cooler than normal for those days. " +
                                        "A short text next to the picture sums it up.",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Below the report, switch between Current (the averages), Month (a daily chart against the typical range, " +
                                        "for this month or the last 30 days, with the forecast as a dashed line) and Year (every month of the year so far compared with the climate average).",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "In settings you can switch the divergence method between absolute delta and normalized anomaly. The value stays in °C, while icon strength can be classified by local variability.",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Tap the star next to the search bar to save a place, and open your saved places with the list icon at the top. " +
                                        "If a search finds several places with the same name, tap \"Not your city?\" to switch.",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row {
                            Text(text = "\u2022")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Settings lets you change the forecast range and historic reference. Furthermore the search language can be changed there, and the cache can be cleared.",
                                fontFamily = nunitoSansFamily,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    val uriHandler = LocalUriHandler.current
                    Text(
                        text = "Weather and climate data by Open-Meteo.com (CC BY 4.0)",
                        fontFamily = nunitoSansFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier =
                            Modifier
                                .testTag("openMeteoCredit")
                                .clickable { uriHandler.openUri("https://open-meteo.com/") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        ElevatedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, SoftMist)
                        ) {
                            Text(
                                text = "Got it",
                                fontFamily = nunitoSansFamily,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun EarthStatusStack() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        EarthStatusPhotoCard(
            imageRes = R.drawable.cold,
            label = "Cold",
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 12.dp, y = 8.dp)
                    .graphicsLayer(rotationZ = -8f),
        )
        EarthStatusPhotoCard(
            imageRes = R.drawable.equal,
            label = "Normal",
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-6).dp)
                    .graphicsLayer(rotationZ = 0f),
        )
        EarthStatusPhotoCard(
            imageRes = R.drawable.warm,
            label = "Warm",
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-12).dp, y = 10.dp)
                    .graphicsLayer(rotationZ = 7f),
        )
    }
}

@Composable
private fun EarthStatusPhotoCard(
    imageRes: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier =
            modifier
                .width(122.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp),
            )
            Text(
                text = label,
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
internal fun StatusTrendCell(
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
                    modifier =
                        Modifier.semantics {
                            value.description?.let { contentDescription = it }
                        },
                    fontFamily = nunitoSansFamily,
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor(value.placeholder),
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
internal fun DividerLine() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    )
}

@Composable
internal fun VerticalDividerLine() {
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
    )
}

internal val WarningIconTint = Color(0xFFE18D10)
