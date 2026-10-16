package com.example.fineweather.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthWindow
import com.example.fineweather.domain.MonthlyAnomaly
import com.example.fineweather.domain.dayPoints
import com.example.fineweather.domain.months
import com.example.fineweather.domain.outlook
import com.example.fineweather.domain.summarize
import com.example.fineweather.domain.summarizeYear
import com.example.fineweather.nunitoSansFamily
import java.time.LocalDate
import java.time.format.TextStyle

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

/** What a view says while it has no snapshot to draw and is not loading one. */
private fun emptyText(
    failed: Boolean,
    prompt: String,
): String = if (failed) "Error" else prompt

@Composable
internal fun MonthView(
    snapshot: ClimateSnapshot?,
    forecastDays: Int,
    referenceLabel: String,
    shape: RoundedCornerShape,
    colors: CardColors,
    loading: Boolean = false,
    failed: Boolean = false,
    metric: Metric = Metric.TEMPERATURE,
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
                CardTitle(metric.dailyTitle)
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
            if (snapshot != null || loading) {
                CompactToggle(
                    labels = listOf("Month", "30 days"),
                    selectedIndex = window.ordinal,
                    onSelect = { window = MonthWindow.entries[it] },
                    tagPrefix = "monthWindow",
                )
            }
        }
        if (snapshot == null) {
            if (loading) {
                LoadingArea(height = DailyChartHeight)
            } else {
                Caption(emptyText(failed, "Search for a place to see its daily ${metric.dailyNoun}."))
            }
            return@ViewCard
        }
        val points = remember(snapshot, window, forecastDays) { dayPoints(snapshot, window, forecastDays) }
        val summary = remember(snapshot, points) { snapshot.summarize(points) }
        val outlook = remember(snapshot, forecastDays) { snapshot.outlook(forecastDays) }
        val dailyAverageExtension =
            remember(snapshot, points, window, metric) {
                val lastPoint = points.lastOrNull()
                if (metric == Metric.TEMPERATURE || window != MonthWindow.THIS_MONTH ||
                    snapshot.reference == null || lastPoint == null
                ) {
                    emptyList()
                } else {
                    val lastDate = lastPoint.date
                    ((lastDate.dayOfMonth + 1)..lastDate.lengthOfMonth()).map { day ->
                        snapshot.reference.dailyStats(lastDate.monthValue, day, halfWindow = 0)?.mean
                    }
                }
            }
        if (summary.observedDays == 0) {
            Caption("No daily data available for this period yet.")
            return@ViewCard
        }
        DailyChart(
            points = points,
            xLabels = remember(points, window) { dayAxisLabels(points.map { it.date }, window) },
            description = dailyChartDescription(summary, withForecast = points.any { it.forecast != null }),
            metric = metric,
            dailyAverageExtension = dailyAverageExtension,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = monthSummaryText(summary, referenceLabel),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("monthSummary"),
        )
        monthCaptionText(summary, points)?.let { Caption(it, Modifier.testTag("monthCaption")) }
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
            text = { ChartLegend(chartLegendEntries(referenceLabel, metric), metric) },
            confirmButton = {
                TextButton(onClick = { showLegend = false }) { Text("OK", fontFamily = nunitoSansFamily) }
            },
        )
    }
}

@Composable
private fun ChartLegend(
    entries: List<LegendEntry>,
    metric: Metric,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .semantics(mergeDescendants = true) {}
                .testTag("chartLegend"),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.symbol != null) {
                    LegendSwatch(entry.symbol, metric)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = entry.text,
                    fontFamily = nunitoSansFamily,
                    style = if (entry.symbol == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = if (entry.symbol == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
            }
        }
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
    loading: Boolean = false,
    failed: Boolean = false,
    metric: Metric = Metric.TEMPERATURE,
) {
    ViewCard(shape, colors, Modifier.testTag("yearView")) {
        if (snapshot == null) {
            CardTitle("Months of the year")
            if (loading) {
                LoadingArea(height = AnomalyChartHeight)
            } else {
                Caption(emptyText(failed, "Search for a place to compare its months with the climate average."))
            }
            return@ViewCard
        }
        val year = snapshot.today.minusDays(1).year
        CardTitle("$year compared with $referenceLabel")
        val months = remember(snapshot) { snapshot.months() }
        val summary = remember(months) { summarizeYear(months) }
        if (snapshot.reference == null || summary.comparedMonths == 0) {
            Caption("No monthly comparison is available yet.")
            return@ViewCard
        }
        AnomalyChart(
            months = months,
            monthInitials = (1..12).map { monthName(it, TextStyle.NARROW) },
            description = yearChartDescription(summary, referenceLabel, metric),
            metric = metric,
        )
        Text(
            text = yearSummaryText(year, summary, referenceLabel, metric),
            fontFamily = nunitoSansFamily,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("yearSummary"),
        )
        YearTable(months, metric)
        Caption(yearChartCaption(metric))
    }
}

@Composable
private fun YearTable(
    months: List<MonthlyAnomaly>,
    metric: Metric,
) {
    // sunshine and rain carry their units and an extra percent column
    val weights = if (metric == Metric.TEMPERATURE) listOf(1.1f, 1.2f, 1.2f, 0.8f) else listOf(0.8f, 1.0f, 1.1f, 1.0f, 0.8f)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TableRow(yearTableHeader(metric), weights, header = true)
        months.forEach { month ->
            TableRow(
                cells = listOf(monthName(month.month, TextStyle.SHORT) + if (month.partial) "*" else "") + yearTableCells(metric, month),
                weights = weights,
                header = false,
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    weights: List<Float>,
    header: Boolean,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.zip(weights).forEachIndexed { index, (text, w) ->
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
