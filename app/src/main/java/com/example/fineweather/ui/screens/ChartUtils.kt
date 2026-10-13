package com.example.fineweather.ui.screens

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
