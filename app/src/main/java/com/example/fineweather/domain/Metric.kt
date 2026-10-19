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

    /** A value of this metric with its unit: `12.34°C`, `5.3 h/day`, `42 mm` (`7.5 mm` below 10). */
    fun format(value: Double): String = formatNumber(value) + unitSuffix

    /** [format] without the unit. */
    fun formatNumber(value: Double): String =
        when (this) {
            TEMPERATURE -> String.format(Locale.US, "%.2f", value)
            SUNSHINE -> String.format(Locale.US, "%.1f", value)
            PRECIPITATION -> String.format(Locale.US, if (hasDecimal(value)) "%.1f" else "%.0f", value)
        }

    /**
     * "[a] vs [b]" with the unit, both numbers with the same precision: for rain one decimal for both if
     * either is below 10 mm, else whole numbers for both. Other metrics format each number as usual.
     */
    fun formatPair(
        a: Double,
        b: Double,
    ): String {
        val numbers =
            if (this == PRECIPITATION) {
                val pattern = if (hasDecimal(a) || hasDecimal(b)) "%.1f" else "%.0f"
                String.format(Locale.US, pattern, a) to String.format(Locale.US, pattern, b)
            } else {
                formatNumber(a) to formatNumber(b)
            }
        return "${numbers.first}$unitSuffix vs ${numbers.second}$unitSuffix"
    }

    /** Whether a rain amount is shown with a decimal: below 10 mm once rounded to one decimal. */
    private fun hasDecimal(value: Double): Boolean = abs(String.format(Locale.US, "%.1f", value).toDouble()) < 10.0

    /**
     * A signed difference: `+1.30°C`, `-0.8 h/day`, `+19 mm`. With [signAtZero] false an exact zero has
     * no sign. For sunshine and rain a difference that rounds to zero has none either.
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
        val roundsToZero = this != TEMPERATURE && number.toDouble() == 0.0
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
