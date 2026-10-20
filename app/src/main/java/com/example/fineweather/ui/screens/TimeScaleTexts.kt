package com.example.fineweather.ui.screens

import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import com.example.fineweather.domain.WindowSummary
import com.example.fineweather.domain.YearSummary
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** The words of the Month and Year views, as plain functions so they can be tested without a screen. */

internal fun monthName(
    month: Int,
    style: TextStyle = TextStyle.FULL,
): String = Month.of(month).getDisplayName(style, Locale.ENGLISH)

/** A temperature with one decimal, as the Month and Year views show it. */
internal fun degrees(value: Double?): String = value?.let { String.format(Locale.US, "%.1f°C", it) } ?: "–"

private fun dayCount(days: Int): String = if (days == 1) "1 day" else "$days days"

/** `Oct 8`. */
internal fun shortDate(date: LocalDate): String = "${monthName(date.monthValue, TextStyle.SHORT)} ${date.dayOfMonth}"

/** The noun in the card title and prompt: "temperatures", "sunshine", "rain". */
internal val Metric.dailyNoun: String
    get() =
        when (this) {
            Metric.TEMPERATURE -> "temperatures"
            Metric.SUNSHINE -> "sunshine"
            Metric.PRECIPITATION -> "rain"
        }

internal val Metric.dailyTitle: String get() = "Daily $dailyNoun"

/** Whether [delta] is too small to show a sign: it rounds to zero in the metric's unit. */
private fun Metric.negligible(delta: Double): Boolean = formatDelta(delta, signAtZero = false).none { it in '1'..'9' }

/** How a period compares with its normal, in words after the value: `+0.8 h/day`, `+85% (41 vs 22 mm)`. */
private fun comparisonText(
    metric: Metric,
    observed: Double,
    reference: Double,
    percent: Double?,
): String? {
    val delta = observed - reference
    return when {
        percent != null && Metric.formatPercent(percent) == "0%" -> null
        percent != null -> "${Metric.formatPercent(percent)} (${metric.formatPair(observed, reference)})"
        metric.negligible(delta) -> null
        else -> metric.formatDelta(delta)
    }
}

/** The sentence under the daily chart: what the days came to and how that compares with the normal. */
internal fun monthSummaryText(
    summary: WindowSummary,
    referenceLabel: String,
): String {
    val metric = summary.metric
    val days = dayCount(summary.observedDays)
    val observed = summary.observedMean
    val reference = summary.referenceMean
    if (metric == Metric.SUNSHINE) {
        val total = summary.observedTotal ?: return "No daily data available for this period yet."
        val referenceTotal = summary.referenceTotal ?: return "${dayCount(summary.observedDays)}: ${formatHours(total)} total."
        val delta = total - referenceTotal
        val comparison =
            if (abs(delta) < 0.05) {
                "in line with"
            } else {
                formatSigned(delta) + " h compared with"
            }
        return "${dayCount(summary.observedDays)}: ${formatHours(total)} total, $comparison the " +
            "${formatHours(referenceTotal)} average for the same days of $referenceLabel."
    }
    if (metric == Metric.TEMPERATURE) {
        val base = "$days averaged ${degrees(observed)}"
        val delta = summary.delta ?: return "$base."
        return "$base, ${formatSigned(delta)}°C compared with the same days of $referenceLabel."
    }
    if (observed == null) return "No daily data available for this period yet."
    val base =
        if (metric == Metric.PRECIPITATION) "$days ${metric.format(observed)} in total" else "$days averaged ${metric.format(observed)}"
    if (reference == null) return "$base."
    val comparison = comparisonText(metric, observed, reference, summary.deltaPercent)
    return if (comparison == null) {
        "$base, in line with the same days of $referenceLabel."
    } else {
        "$base, $comparison compared with the same days of $referenceLabel."
    }
}

private fun formatHours(value: Double): String = String.format(Locale.US, "%.1f h", value)

/** Rainy days and the wettest day of a period. */
internal data class RainDays(
    val rainyDays: Int,
    val wettest: Pair<LocalDate, Double>?,
)

/** A day with at least this much rain, in mm, is a rainy day (the caption says ">= 1 mm"). */
internal const val RAINY_DAY_MM = 1.0

/** The rainy days (at least [RAINY_DAY_MM]) among the observed days of [points], and the wettest of them. */
internal fun rainDays(points: List<DayPoint>): RainDays {
    val rainy = points.mapNotNull { point -> point.observed?.takeIf { it >= RAINY_DAY_MM }?.let { point.date to it } }
    return RainDays(rainy.size, rainy.maxByOrNull { it.second })
}

/** The line below the summary: days above and below the typical day, or for rain the rainy days. Null if there is nothing to say. */
internal fun monthCaptionText(
    summary: WindowSummary,
    points: List<DayPoint>,
): String? {
    val metric = summary.metric
    val wording = metric.wording
    return when (metric) {
        Metric.TEMPERATURE ->
            "${dayCount(summary.warmerDays)} ${wording.above} and ${dayCount(summary.colderDays)} ${wording.below} than the typical day."
        Metric.PRECIPITATION -> rainCaption(rainDays(points))
        else -> null
    }
}

internal fun rainCaption(rain: RainDays): String {
    val (date, amount) = rain.wettest ?: return "No rainy days (>= 1 mm)."
    val days = if (rain.rainyDays == 1) "1 rainy day" else "${rain.rainyDays} rainy days"
    return "$days (>= 1 mm), wettest: ${Metric.PRECIPITATION.format(amount)} on ${shortDate(date)}."
}

/** The sentence about the coming days: how the forecast compares with the normal of exactly those days. */
internal fun forecastSummaryText(outlook: ForecastOutlook): String {
    val metric = outlook.metric
    val period = "next ${outlook.days} days"
    val reference = outlook.referenceMean
    when (metric) {
        Metric.TEMPERATURE -> {
            val delta = outlook.delta ?: return "Forecast: the $period average ${degrees(outlook.mean)}."
            return "Forecast: the $period average ${degrees(outlook.mean)}, ${formatSigned(delta)}°C compared with normal for those days."
        }

        Metric.SUNSHINE -> {
            val base = "Forecast: the $period average ${metric.format(outlook.mean)}"
            val delta = outlook.delta ?: return "$base."
            return if (metric.negligible(delta)) {
                "$base, in line with normal for those days."
            } else {
                "$base, ${metric.formatDelta(delta)} compared with normal for those days."
            }
        }

        Metric.PRECIPITATION -> {
            val amount = if (outlook.mean < DRY_MM) "no rain" else "about ${metric.format(outlook.mean)} of rain"
            val base = "Forecast: $amount in the $period"
            return if (reference == null) "$base." else "$base, vs ${metric.format(reference)} normal for those days."
        }
    }
}

/** Below this much rain, in mm, a forecast total counts as dry. */
private const val DRY_MM = 0.05

/** The sample drawn next to a legend entry, mirroring how the daily chart draws that element. */
internal enum class LegendSymbol {
    DAILY_LINE,
    WARM_COLD_DOTS,
    TYPICAL_BAND,
    TEMPERATURE_FORECAST,
    DAILY_BARS,
    ACCUMULATED_LINE,
    AVERAGE_LINE,
    BAR_FORECAST,
}

/** One line of the chart legend; entries without a [symbol] are notes. */
internal data class LegendEntry(
    val symbol: LegendSymbol?,
    val text: String,
)

/** What the info button of the daily chart explains. */
internal fun chartLegendEntries(
    referenceLabel: String,
    metric: Metric = Metric.TEMPERATURE,
): List<LegendEntry> {
    if (metric == Metric.TEMPERATURE) {
        return listOf(
            LegendEntry(LegendSymbol.DAILY_LINE, "Daily mean."),
            LegendEntry(LegendSymbol.WARM_COLD_DOTS, "Red on days warmer than typical, blue on days colder than typical."),
            LegendEntry(
                LegendSymbol.TYPICAL_BAND,
                "Typical range of $referenceLabel (±1 standard deviation); the dashed line in it is the average.",
            ),
            LegendEntry(LegendSymbol.TEMPERATURE_FORECAST, "The forecast."),
            LegendEntry(
                null,
                "Forecasts become less certain the further ahead they look, so treat days beyond about a week as a rough guide.",
            ),
        )
    }
    val bars =
        when (metric) {
            Metric.SUNSHINE -> "Hours of sunshine each day."
            else -> "Rain per day in mm. A thin stub is a dry day; a missing day has no bar."
        }
    val accumulated =
        when (metric) {
            Metric.SUNSHINE -> "Accumulated sunshine hours (right axis)."
            else -> "Accumulated rain in this period (right axis)."
        }
    val entries =
        listOf(
            LegendEntry(LegendSymbol.DAILY_BARS, bars),
            LegendEntry(LegendSymbol.ACCUMULATED_LINE, accumulated),
            LegendEntry(
                LegendSymbol.AVERAGE_LINE,
                "Historical average for this period, accumulated at an even daily pace (right axis).",
            ),
            LegendEntry(LegendSymbol.BAR_FORECAST, "The forecast: lighter outlined bars, the accumulated line continues dashed."),
            LegendEntry(
                null,
                "Forecasts get less certain the further ahead they look, rain especially: treat days beyond about a " +
                    "week as a rough guide.",
            ),
        )
    if (metric != Metric.SUNSHINE) return entries
    return entries +
        LegendEntry(
            null,
            "Sunshine hours are modelled from reanalysis data (ERA5), not measured at a station, so absolute " +
                "values can differ from local weather-station figures.",
        )
}

/** The legend as plain text. */
internal fun chartLegendText(
    referenceLabel: String,
    metric: Metric = Metric.TEMPERATURE,
): String = chartLegendEntries(referenceLabel, metric).joinToString("\n") { it.text }

/** The a11y description of the daily chart. */
internal fun dailyChartDescription(
    summary: WindowSummary,
    withForecast: Boolean,
): String {
    val metric = summary.metric
    val observed = summary.observedMean
    val head =
        when (metric) {
            Metric.TEMPERATURE -> "Daily mean temperatures, ${summary.observedDays} days, averaging ${degrees(observed)}"
            Metric.SUNSHINE -> {
                val average = summary.referenceTotal?.let { ", historical average ${formatHours(it)}" }.orEmpty()
                "Daily sunshine, ${summary.observedDays} days, ${summary.observedTotal?.let(::formatHours) ?: "–"} total$average"
            }
            Metric.PRECIPITATION -> {
                val average = summary.referenceMean?.let { ", historical average ${metric.format(it)}" }.orEmpty()
                "Daily rain, ${summary.observedDays} days, ${observed?.let(metric::format) ?: "–"} in total$average"
            }
        }
    return head + if (withForecast) ", with the forecast for the coming days." else "."
}

/** The sentence under the monthly chart. */
internal fun yearSummaryText(
    year: Int,
    summary: YearSummary,
    referenceLabel: String,
    metric: Metric = Metric.TEMPERATURE,
): String {
    val delta = summary.delta ?: return "No monthly comparison is available yet."
    val months = "${summary.warmerMonths} of ${summary.comparedMonths} months"
    if (metric == Metric.TEMPERATURE) {
        return "So far $year is ${formatSigned(delta)}°C compared with $referenceLabel, with $months warmer than average."
    }
    val total = summary.totalDelta ?: return "No monthly comparison is available yet."
    val amount = formatAmountDelta(total, metric)
    val what = if (metric == Metric.PRECIPITATION) "rainfall" else "sunshine"
    val lead = if (amount.none { it in '1'..'9' }) "$what in line with" else "$amount $what compared with"
    return "So far this year: $lead the $referenceLabel average, $months ${metric.wording.above}."
}

/** The description of the monthly chart for screen readers. */
internal fun yearChartDescription(
    summary: YearSummary,
    referenceLabel: String,
    metric: Metric = Metric.TEMPERATURE,
): String =
    when (metric) {
        Metric.TEMPERATURE ->
            "Monthly deviation from the $referenceLabel average. ${summary.warmerMonths} of ${summary.comparedMonths} months were warmer."

        else ->
            "Monthly ${metric.dailyNoun} compared with the $referenceLabel average. " +
                "${summary.warmerMonths} of ${summary.comparedMonths} months were ${metric.wording.above}."
    }

/** The note under the monthly table. */
internal fun yearChartCaption(metric: Metric): String {
    val partial =
        if (metric == Metric.TEMPERATURE) {
            "A lighter bar is a month that is still running, compared with the same days only."
        } else {
            "A lighter bar is a month that is still running; the table compares it with the same days only."
        }
    val bars =
        when (metric) {
            Metric.TEMPERATURE -> "Bars show the deviation in °C."
            Metric.SUNSHINE -> "Bars show each month's hours of sunshine; the wider grey bar behind is the historical average for the whole month."
            Metric.PRECIPITATION -> "Bars show each month's rain in mm; the wider grey bar behind is the historical average for the whole month."
        }
    return "$bars $partial"
}

/** The header texts of the monthly table. */
internal fun yearTableHeader(metric: Metric): List<String> =
    if (metric == Metric.TEMPERATURE) {
        listOf("Month", "Observed", "Reference", "Δ")
    } else {
        listOf("Month", "Total", "Reference", "Δ", "Δ%")
    }

/**
 * The cells of one row of the monthly table after the month: observed, reference and delta, for sunshine
 * and rain as monthly totals followed by the delta in percent.
 */
internal fun yearTableCells(
    metric: Metric,
    month: MonthlyAnomaly,
): List<String> {
    if (metric == Metric.TEMPERATURE) {
        return listOf(degrees(month.observedMean), degrees(month.referenceMean), month.delta?.let { formatSigned(it) } ?: "–")
    }
    return listOf(
        month.observedTotal?.let { formatAmount(metric, it) } ?: "–",
        month.referenceTotal?.let { formatAmount(metric, it) } ?: "–",
        month.totalDelta?.let { formatAmountDelta(it, metric) } ?: "–",
        month.totalPercent?.let { Metric.formatPercent(it) } ?: "–",
    )
}
