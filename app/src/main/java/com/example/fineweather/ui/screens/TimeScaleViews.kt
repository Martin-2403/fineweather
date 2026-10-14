package com.example.fineweather.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.forecastOutlook
import com.example.fineweather.domain.MonthWindow
import com.example.fineweather.domain.dayPoints
import com.example.fineweather.domain.summarize
import com.example.fineweather.domain.summarizeYear
import com.example.fineweather.domain.yearToDate
import com.example.fineweather.nunitoSansFamily
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

internal enum class TimeScale(val label: String) {
    CURRENT("Current"),
    MONTH("Month"),
    YEAR("Year"),
}

@Composable
internal fun ViewCard(
    shape: RoundedCornerShape,
    colors: CardColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        shape = shape,
        colors = colors,
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f), shape),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        fontFamily = nunitoSansFamily,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Caption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = nunitoSansFamily,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
    )
}

private fun monthName(
    month: Int,
    style: TextStyle = TextStyle.FULL,
): String = Month.of(month).getDisplayName(style, Locale.ENGLISH)

private fun degrees(value: Double?): String = value?.let { String.format(Locale.US, "%.1f°C", it) } ?: "–"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthView(
    snapshot: ClimateSnapshot?,
    forecastDays: Int,
    referenceLabel: String,
    shape: RoundedCornerShape,
    colors: CardColors,
) {
    var window by rememberSaveable { mutableStateOf(MonthWindow.THIS_MONTH) }
    var showLegend by rememberSaveable { mutableStateOf(false) }
    ViewCard(shape, colors, Modifier.testTag("monthView")) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Daily temperatures")
                IconButton(
                    onClick = { showLegend = true },
                    modifier = Modifier.size(32.dp).testTag("monthInfoButton"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = "About this chart",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (snapshot != null) {
                CompactToggle(
                    labels = listOf("Month", "30 days"),
                    selectedIndex = window.ordinal,
                    onSelect = { window = MonthWindow.entries[it] },
                    tagPrefix = "monthWindow",
                )
            }
        }
        if (snapshot == null) {
            Caption("Search for a place to see its daily temperatures.")
            return@ViewCard
        }
        val points = remember(snapshot, window, forecastDays) { dayPoints(snapshot, window, forecastDays) }
        val summary = remember(points) { summarize(points) }
        val outlook =
            remember(snapshot, forecastDays) { forecastOutlook(snapshot.forecast, snapshot.reference, forecastDays) }
        if (summary.observedDays == 0) {
            Caption("No daily data available for this period yet.")
            return@ViewCard
        }
        DailyChart(
            points = points,
            xLabels = remember(points, window) { dayAxisLabels(points.map { it.date }, window) },
            description =
                "Daily mean temperatures, ${summary.observedDays} days, averaging ${degrees(summary.observedMean)}" +
                    if (points.any { it.forecast != null }) ", with the forecast for the coming days." else ".",
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = monthSummaryText(summary.observedDays, summary.observedMean, summary.delta, referenceLabel),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("monthSummary"),
        )
        Caption(
            "${summary.warmerDays} ${if (summary.warmerDays == 1) "day" else "days"} warmer and " +
                "${summary.colderDays} ${if (summary.colderDays == 1) "day" else "days"} colder than the typical day.",
        )
        outlook?.let {
            Text(
                text = forecastSummaryText(it),
                fontFamily = nunitoSansFamily,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("forecastSummary"),
            )
        }
    }
    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            title = { Text("Reading the chart", fontFamily = nunitoSansFamily, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = chartLegendText(referenceLabel),
                    fontFamily = nunitoSansFamily,
                    modifier = Modifier.testTag("chartLegend"),
                )
            },
            confirmButton = {
                TextButton(onClick = { showLegend = false }) { Text("OK", fontFamily = nunitoSansFamily) }
            },
        )
    }
}

internal fun chartLegendText(referenceLabel: String): String =
    "Line: daily mean.\n" +
        "Band: typical range of $referenceLabel (±1 standard deviation); the dashed line in it is the average.\n" +
        "Dots are red on days warmer than typical and blue on days colder than typical.\n" +
        "Dashed line with hollow dots: the forecast. Forecasts become less certain the further " +
        "ahead they look, so treat days beyond about a week as a rough guide."

internal fun forecastSummaryText(outlook: ForecastOutlook): String {
    val period = "next ${outlook.days} days"
    val delta = outlook.delta ?: return "Forecast: the $period average ${degrees(outlook.mean)}."
    return "Forecast: the $period average ${degrees(outlook.mean)}, ${formatSigned(delta)}°C compared with " +
        "normal for those days."
}

internal fun monthSummaryText(
    days: Int,
    mean: Double?,
    delta: Double?,
    referenceLabel: String,
): String {
    val dayText = if (days == 1) "1 day" else "$days days"
    val base = "$dayText averaged ${degrees(mean)}"
    return if (delta == null) {
        "$base."
    } else {
        "$base, ${formatSigned(delta)}°C compared with the same days of $referenceLabel."
    }
}

private fun dayAxisLabels(
    dates: List<LocalDate>,
    window: MonthWindow,
): List<Pair<Int, String>> =
    when (window) {
        MonthWindow.THIS_MONTH ->
            dates.indices.filter { it == 0 || (dates[it].dayOfMonth % 5 == 0 && dates.size - it > 2) }
                .map { it to dates[it].dayOfMonth.toString() }

        MonthWindow.LAST_30_DAYS ->
            (listOf(0, 7, 14, 21, 29) + listOfNotNull((dates.size - 1).takeIf { it >= 34 }))
                .filter { it < dates.size }.map {
                it to "${monthName(dates[it].monthValue, TextStyle.SHORT)} ${dates[it].dayOfMonth}"
            }
    }

@Composable
internal fun YearView(
    snapshot: ClimateSnapshot?,
    referenceLabel: String,
    shape: RoundedCornerShape,
    colors: CardColors,
) {
    ViewCard(shape, colors, Modifier.testTag("yearView")) {
        if (snapshot == null) {
            CardTitle("Months of the year")
            Caption("Search for a place to compare its months with the climate average.")
            return@ViewCard
        }
        val year = snapshot.today.minusDays(1).year
        CardTitle("$year compared with $referenceLabel")
        val months = remember(snapshot) { yearToDate(snapshot.series, snapshot.reference, snapshot.today) }
        val summary = remember(months) { summarizeYear(months) }
        if (snapshot.reference == null || summary.comparedMonths == 0) {
            Caption("No monthly comparison is available yet.")
            return@ViewCard
        }
        AnomalyChart(
            months = months,
            monthInitials = (1..12).map { monthName(it, TextStyle.NARROW) },
            description =
                "Monthly deviation from the $referenceLabel average. " +
                    "${summary.warmerMonths} of ${summary.comparedMonths} months were warmer.",
        )
        Text(
            text = yearSummaryText(year, summary.delta, summary.warmerMonths, summary.comparedMonths, referenceLabel),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("yearSummary"),
        )
        YearTable(months.map { it })
        Caption("Bars show the deviation in °C. A lighter bar is a month that is still running, compared with the same days only.")
    }
}

internal fun yearSummaryText(
    year: Int,
    delta: Double?,
    warmerMonths: Int,
    comparedMonths: Int,
    referenceLabel: String,
): String {
    if (delta == null) return "No monthly comparison is available yet."
    return "So far $year is ${formatSigned(delta)}°C compared with $referenceLabel, " +
        "with $warmerMonths of $comparedMonths months warmer than average."
}

@Composable
private fun YearTable(months: List<com.example.fineweather.domain.MonthlyAnomaly>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TableRow("Month", "Observed", "Reference", "Δ", header = true)
        months.forEach { month ->
            TableRow(
                label = monthName(month.month, TextStyle.SHORT) + if (month.partial) "*" else "",
                observed = degrees(month.observedMean),
                reference = degrees(month.referenceMean),
                delta = month.delta?.let { formatSigned(it) } ?: "–",
                header = false,
            )
        }
    }
}

@Composable
private fun TableRow(
    label: String,
    observed: String,
    reference: String,
    delta: String,
    header: Boolean,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(label to 1.1f, observed to 1.2f, reference to 1.2f, delta to 0.8f).forEachIndexed { index, (text, w) ->
            Text(
                text = text,
                modifier = Modifier.weight(w),
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                fontFamily = nunitoSansFamily,
                style = style,
                fontWeight = weight,
            )
        }
    }
}
