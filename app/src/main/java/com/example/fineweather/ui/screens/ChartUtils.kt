package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

internal data class Axis(
    val min: Double,
    val max: Double,
    val ticks: List<Double>,
)

/** A y-axis with round tick values that covers [dataMin]..[dataMax] in about [targetTicks] steps. */
internal fun niceAxis(
    dataMin: Double,
    dataMax: Double,
    targetTicks: Int = 4,
): Axis {
    val low = minOf(dataMin, dataMax)
    var high = maxOf(dataMin, dataMax)
    if (high - low < 1e-9) high = low + 1.0
    val rawStep = (high - low) / targetTicks
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep - 1e-12 }
    val min = floor(low / step) * step
    val max = ceil(high / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step * 1e-6 }.toList()
    return Axis(min, max, ticks)
}

internal fun formatTick(value: Double): String =
    if (value == round(value)) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }

internal fun formatSigned(value: Double): String = String.format(Locale.US, "%+.1f", value)


// ---- sunshine and rain ----

/** The y-axis of the daily bars of [metric]: from the metric's lowest value (0) up to [maxValue], at least a small span. */
internal fun dailyBarAxis(
    metric: Metric,
    maxValue: Double,
    intervals: Int = 4,
): Axis {
    val minimumMax =
        when (metric) {
            Metric.SUNSHINE -> MIN_SUNSHINE_AXIS_HOURS
            Metric.PRECIPITATION -> MIN_RAIN_AXIS_MM
            Metric.TEMPERATURE -> 1.0
        }
    return alignedZeroAxis(maxOf(maxValue, minimumMax), intervals)
}

/** A rounded zero-based scale with [intervals] grid intervals for aligning two chart axes. */
internal fun alignedZeroAxis(
    maxValue: Double,
    intervals: Int,
): Axis {
    require(intervals > 0) { "Axis must have at least one interval" }
    val rawStep = maxValue.coerceAtLeast(1e-9) / intervals
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rawStep - 1e-12 }
    val ticks = (0..intervals).map { it * step }
    return Axis(0.0, intervals * step, ticks)
}

/**
 * The y-axis of the monthly bars of [metric] (see [anomalyValue]). Temperature keeps its axis of deviations,
 * which reaches at least +1 and 0; sunshine and rain start at zero and reach at least 10 h or 10 mm.
 */
internal fun anomalyAxis(
    metric: Metric,
    values: List<Double>,
): Axis {
    val lowest = values.minOrNull()
    val highest = values.maxOrNull()
    return when (metric) {
        Metric.TEMPERATURE -> niceAxis(minOf(0.0, lowest ?: 0.0), maxOf(1.0, highest ?: 0.0))
        Metric.SUNSHINE -> niceAxis(0.0, maxOf(MIN_SUNSHINE_TOTAL_HOURS, highest ?: 0.0))
        Metric.PRECIPITATION -> niceAxis(0.0, maxOf(MIN_RAIN_TOTAL_MM, highest ?: 0.0))
    }
}

/** What a month's bar shows: the deviation in °C, or the month's total in hours of sunshine or mm of rain (null: no bar). */
internal fun anomalyValue(
    metric: Metric,
    month: MonthlyAnomaly,
): Double? = if (metric == Metric.TEMPERATURE) month.delta else month.observedTotal

/** The total the grey bar behind a month's bar shows: the historical average of the whole month; none for temperature. */
internal fun anomalyReferenceValue(
    metric: Metric,
    month: MonthlyAnomaly,
): Double? = if (metric == Metric.TEMPERATURE) null else month.referenceMonthTotal

/** A tick of the daily bars' axis with the metric unit. */
internal fun dailyTickLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> "${formatTick(value)}°C"
        Metric.SUNSHINE -> "${formatTick(value)} h"
        Metric.PRECIPITATION -> "${formatTick(value)} mm"
    }

/** A tick of the monthly deviations' axis with the metric unit. */
internal fun anomalyTickLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> "${formatTick(value)}°C"
        Metric.SUNSHINE -> "${formatTick(value)} h"
        Metric.PRECIPITATION -> "${formatTick(value)} mm"
    }

/** The label above a month's bar: `+1.8` (°C deviation), `186` (hours of sunshine), `7.5` (mm of rain). */
internal fun anomalyBarLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> formatSigned(value)
        Metric.SUNSHINE, Metric.PRECIPITATION -> Metric.PRECIPITATION.formatNumber(value)
    }

/** A monthly amount of sunshine or rain with its unit: whole numbers from 10 on, else one decimal (`251 h`, `7.5 mm`). */
internal fun formatAmount(
    metric: Metric,
    value: Double,
): String = "${Metric.PRECIPITATION.formatNumber(value)} ${amountUnit(metric)}"

/** A signed monthly amount, see [formatAmount]; without [metric] just the number (`+12`, `-4.5`). */
internal fun formatAmountDelta(
    value: Double,
    metric: Metric? = null,
): String {
    val number = Metric.PRECIPITATION.formatNumber(abs(value))
    val sign = if (number.toDouble() == 0.0) "" else if (value < 0) "-" else "+"
    return sign + number + (metric?.let { " ${amountUnit(it)}" } ?: "")
}

private fun amountUnit(metric: Metric): String = if (metric == Metric.SUNSHINE) "h" else metric.unit

/** The color of a day's bar: rain is blue and sunshine is amber. */
internal fun dailyBarColor(
    metric: Metric,
    dark: Boolean,
): Color =
    when (metric) {
        Metric.PRECIPITATION -> arrowTint(metric, above = true, dark = dark)
        else -> arrowTint(metric, above = true, dark = dark)
    }

/** The color of a month's bar: the color of its side of the normal (a deviation of 0 counts as above). */
internal fun anomalyBarColor(
    metric: Metric,
    value: Double,
    dark: Boolean,
): Color = arrowTint(metric, above = value >= 0, dark = dark)

/** The width of one bar's slot and of the bar in it. */
internal data class BarSlots(
    val slot: Float,
    val barWidth: Float,
) {
    /** The x of the middle of the bar at [index], for a chart whose area starts at [left]. */
    fun center(
        left: Float,
        index: Int,
    ): Float = left + slot * (index + 0.5f)
}

/** [count] slots across [width]; the bar fills [fill] of its slot, but is never wider than [maxBarWidth]. */
internal fun barSlots(
    count: Int,
    width: Float,
    fill: Float = 0.7f,
    maxBarWidth: Float = Float.MAX_VALUE,
): BarSlots {
    val slot = width / count.coerceAtLeast(1)
    return BarSlots(slot, minOf(slot * fill, maxBarWidth))
}

/** The height of a bar of [value] on an axis up to [axisMax]; a bar is never lower than [minStub], so a zero-day shows. */
internal fun barHeight(
    value: Double,
    axisMax: Double,
    chartHeight: Float,
    minStub: Float,
): Float = maxOf((value / axisMax).toFloat() * chartHeight, minStub)

/** Running total across observed and forecast daily values; missing days have no point. */
internal fun cumulativeDailyValues(points: List<DayPoint>): List<Double?> {
    var total = 0.0
    return points.map { point ->
        val value = point.observed ?: point.forecast
        if (value == null) {
            null
        } else {
            total += value
            total
        }
    }
}

/**
 * Historical normal for the plotted dates and any extension, accumulated at an even daily pace.
 * Single calendar-day normals are too noisy to be informative, so the line rises linearly to the
 * period's normal total; days without a reference count at the average pace.
 */
internal fun cumulativeAverageDailyValues(
    points: List<DayPoint>,
    extension: List<Double?> = emptyList(),
): List<Double?> {
    val normals = points.map { it.referenceMean } + extension
    val pace = normals.filterNotNull().takeIf { it.isNotEmpty() }?.average() ?: return normals.map { null }
    return normals.indices.map { index -> pace * (index + 1) }
}

private const val MIN_SUNSHINE_AXIS_HOURS = 4.0
private const val MIN_RAIN_AXIS_MM = 4.0
private const val MIN_SUNSHINE_TOTAL_HOURS = 10.0
private const val MIN_RAIN_TOTAL_MM = 10.0
