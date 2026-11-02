package com.example.fineweather.domain

import java.util.Locale
import kotlin.math.abs

/** How the daily values of a period are summarised: their mean, or their sum. */
enum class Aggregation { MEAN, TOTAL }

/** The words a [Metric] uses in sentences. */
data class MetricWording(
    /** Adjective for above normal ("warmer"). */
    val above: String,
    /** Adjective for below normal ("colder"). */
    val below: String,
    /** [below] as used for the forecast ("cooler"). */
    val belowOutlook: String,
    /** Base adjective for "about as ... as usual". */
    val neutral: String,
    /** The quantity itself ("temperature"). */
    val quantity: String,
)

/** A daily weather quantity. Values are held in the domain unit; dividing the API value by [apiDivisor] gives it. */
enum class Metric(
    val label: String,
    val unit: String,
    val apiName: String,
    val apiDivisor: Double = 1.0,
    val aggregation: Aggregation = Aggregation.MEAN,
    /** The least a daily value can be, if there is one. */
    val lowerBound: Double? = null,
    val wording: MetricWording,
) {
    TEMPERATURE(
        "Temp",
        "°C",
        "temperature_2m_mean",
        wording = MetricWording("warmer", "colder", "cooler", "warm", "temperature"),
    ),

    /** The API reports seconds per day. */
    SUNSHINE(
        "Sun",
        "h/day",
        "sunshine_duration",
        3600.0,
        lowerBound = 0.0,
        wording = MetricWording("sunnier", "duller", "duller", "sunny", "sunshine"),
    ),
    PRECIPITATION(
        "Rain",
        "mm",
        "precipitation_sum",
        aggregation = Aggregation.TOTAL,
        lowerBound = 0.0,
        wording = MetricWording("wetter", "drier", "drier", "wet", "rain"),
    ),
    ;

    /**
     * The value of a period of [days] days whose daily mean is [dailyMean]: the mean itself, or for
     * a [Aggregation.TOTAL] metric the mean times the days. The analysis only ever averages, so totals
     * are this conversion at the edge. Mean times days keeps a leap day or a missing day from skewing a total.
     *
     * It also converts a spread across years, on the assumption that a year's total is that year's window
     * mean times the days: the standard deviation of totals is then `windowStdDev` times the days.
     */
    fun periodValue(
        dailyMean: Double,
        days: Int,
    ): Double =
        when (aggregation) {
            Aggregation.MEAN -> dailyMean
            Aggregation.TOTAL -> dailyMean * days
        }

    /** A value of this metric with its unit, always with one decimal: `12.3°C`, `5.3 h/day`, `42.0 mm`. */
    fun format(value: Double): String = formatNumber(value) + unitSuffix

    /** [format] without the unit. */
    fun formatNumber(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** "[a] vs [b]" with the unit, both numbers with one decimal (`10.0°C vs 4.0°C`, `41.0 mm vs 26.0 mm`). */
    fun formatPair(
        a: Double,
        b: Double,
    ): String = "${formatNumber(a)}$unitSuffix vs ${formatNumber(b)}$unitSuffix"

    /**
     * A signed difference: `+1.3°C`, `-0.8 h/day`, `+19.0 mm`. A difference that rounds to zero has no sign
     * (`0.0°C`, never `+0.0°C`), exact zero included, so [signAtZero] currently changes nothing.
     */
    fun formatDelta(
        delta: Double,
        signAtZero: Boolean = true,
    ): String {
        val number = formatNumber(abs(delta))
        val sign =
            when {
                delta < 0 -> "-"
                delta > 0 || signAtZero -> "+"
                else -> ""
            }
        val roundsToZero = number.toDouble() == 0.0
        return (if (roundsToZero) "" else sign) + number + unitSuffix
    }

    /**
     * The difference as a percent of the normal. Null unless this metric adds up and [reference] is at least
     * [AssessmentScales.MIN_NORMAL_RAIN_MM]: below it a percent says nothing (3 mm on a 0.3 mm normal is +900 %).
     * The guard is rain's for now; it is the only metric that adds up.
     */
    fun percentOfNormal(
        delta: Double,
        reference: Double,
    ): Double? =
        if (aggregation == Aggregation.TOTAL && reference >= AssessmentScales.MIN_NORMAL_RAIN_MM) {
            delta / reference * 100.0
        } else {
            null
        }

    private val unitSuffix: String get() = if (this == TEMPERATURE) unit else " $unit"

    companion object {
        /** A percent with its sign: `+85%`, `-30%`, `0%`. */
        fun formatPercent(percent: Double): String {
            val rounded = String.format(Locale.US, "%.0f", abs(percent))
            val sign = if (rounded == "0") "" else if (percent < 0) "-" else "+"
            return "$sign$rounded%"
        }
    }
}
