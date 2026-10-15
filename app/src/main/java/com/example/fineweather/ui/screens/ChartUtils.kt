package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class Axis(
    val min: Double,
    val max: Double,
    val ticks: List<Double>,
)

/** A y axis with round tick values that covers [dataMin]..[dataMax] in about [targetTicks] steps. */
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
    if (value == Math.rint(value)) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }

internal fun formatSigned(value: Double): String = String.format(Locale.US, "%+.1f", value)


// ---- sunshine and rain ----

/** The y axis of the daily bars of [metric]: from the metric's lowest value (0) up to [maxValue], at least a small span. */
internal fun dailyBarAxis(
    metric: Metric,
    maxValue: Double,
): Axis {
    val floor = metric.lowerBound ?: 0.0
    val minTop = if (metric == Metric.PRECIPITATION) MIN_RAIN_AXIS_MM else MIN_SUNSHINE_AXIS_HOURS
    return niceAxis(floor, maxOf(maxValue, floor + minTop))
}

/**
 * The y axis of the monthly deviations of [metric] (see [anomalyValue]). Temperature keeps its axis, which
 * reaches at least +1 and 0; sunshine and rain always span at least ±1 h/day and ±50 %.
 */
internal fun anomalyAxis(
    metric: Metric,
    values: List<Double>,
): Axis {
    val lowest = values.minOrNull()
    val highest = values.maxOrNull()
    return when (metric) {
        Metric.TEMPERATURE -> niceAxis(minOf(0.0, lowest ?: 0.0), maxOf(1.0, highest ?: 0.0))
        Metric.SUNSHINE -> niceAxis(minOf(-MIN_SUNSHINE_SPAN_HOURS, lowest ?: 0.0), maxOf(MIN_SUNSHINE_SPAN_HOURS, highest ?: 0.0))
        Metric.PRECIPITATION -> niceAxis(minOf(-MIN_RAIN_SPAN_PERCENT, lowest ?: 0.0), maxOf(MIN_RAIN_SPAN_PERCENT, highest ?: 0.0))
    }
}

/** What a month's bar shows: the deviation in the unit of the metric, for rain its percent of the normal (null: no bar). */
internal fun anomalyValue(
    metric: Metric,
    month: MonthlyAnomaly,
): Double? = if (metric == Metric.PRECIPITATION) month.deltaPercent else month.delta

/** A tick of the daily bars' axis: the number and a short unit (`6 h`, `10 mm`); temperature has the bare number. */
internal fun dailyTickLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> formatTick(value)
        Metric.SUNSHINE -> "${formatTick(value)} h"
        Metric.PRECIPITATION -> "${formatTick(value)} mm"
    }

/** A tick of the monthly deviations' axis: `-5` for temperature, `-1 h` for sunshine, `50%` for rain. */
internal fun anomalyTickLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> formatTick(value)
        Metric.SUNSHINE -> "${formatTick(value)} h"
        Metric.PRECIPITATION -> "${formatTick(value)}%"
    }

/** The label above or below a month's bar: `+1.8`, `-0.4` (hours for sunshine), `+35%` for rain. */
internal fun anomalyBarLabel(
    metric: Metric,
    value: Double,
): String =
    when (metric) {
        Metric.TEMPERATURE -> formatSigned(value)
        Metric.SUNSHINE -> metric.formatDelta(value, signAtZero = false).removeSuffix(" ${metric.unit}")
        Metric.PRECIPITATION -> Metric.formatPercent(value)
    }

/** The colour of a day's bar: rain is always blue; sunshine is amber at or above the typical day, slate below it. */
internal fun dailyBarColor(
    metric: Metric,
    value: Double,
    typical: Double?,
    dark: Boolean,
): Color =
    when (metric) {
        Metric.PRECIPITATION -> arrowTint(metric, above = true, dark = dark)
        else -> arrowTint(metric, above = typical == null || value >= typical, dark = dark)
    }

/** The colour of a month's bar: the colour of its side of the normal (a deviation of 0 counts as above). */
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

/** The height of a bar of [value] on an axis up to [axisMax]; a bar is never lower than [minStub], so a zero day shows. */
internal fun barHeight(
    value: Double,
    axisMax: Double,
    chartHeight: Float,
    minStub: Float,
): Float = maxOf((value / axisMax).toFloat() * chartHeight, minStub)

private const val MIN_SUNSHINE_AXIS_HOURS = 4.0
private const val MIN_RAIN_AXIS_MM = 4.0
private const val MIN_SUNSHINE_SPAN_HOURS = 1.0
private const val MIN_RAIN_SPAN_PERCENT = 50.0
