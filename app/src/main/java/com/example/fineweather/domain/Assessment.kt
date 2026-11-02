package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import kotlin.math.abs

/**
 * Whether a period is above, below or near its normal. WARM and COLD are the polarity for every
 * [Metric]: WARM is "above normal" (warmer, sunnier, wetter), COLD is "below normal".
 */
enum class Band { WARM, COLD, NEUTRAL }

/**
 * Where the magnitude classes of an [Assessment] switch. These are the only thresholds; change them here.
 */
object AssessmentScales {
    /**
     * Class 0 (near normal) is below [neutral], class 1 up to [slight], class 2 up to [clear], class 3 above.
     * Class 1 starts AT [neutral], while [Assessment.band] only leaves NEUTRAL above it (as temperature always did).
     */
    class Scale(val neutral: Double, val slight: Double, val clear: Double) {
        /** Magnitude class 0..3 of an absolute [magnitude] on this scale. */
        fun severity(magnitude: Double): Int =
            when {
                magnitude < neutral -> 0
                magnitude < slight -> 1
                magnitude < clear -> 2
                else -> 3
            }
    }

    /** In standard deviations; used for every metric in normalized-anomaly mode. */
    val NORMALIZED = Scale(0.5, 1.5, 2.5)

    /** Smallest normal total, in mm, that rain is compared with; below it there is no percent and the band is neutral. */
    const val MIN_NORMAL_RAIN_MM = 2.0

    private val ABSOLUTE =
        mapOf(
            Metric.TEMPERATURE to Scale(1.0, 2.0, 3.0),
            Metric.SUNSHINE to Scale(0.5, 1.0, 1.5),
            Metric.PRECIPITATION to Scale(25.0, 50.0, 75.0),
        )

    /** The scale of the absolute mode: °C and h/day of difference, percent of the normal for rain. */
    fun absolute(metric: Metric): Scale = ABSOLUTE.getValue(metric)
}

/**
 * How far a period is from its reference, judged in the metric's own unit or in standard deviations.
 * [delta], [stdDev] and [reference] are in the period unit of [metric] (a rain total in mm, a mean
 * otherwise). Rain is judged by percent of the normal, so it needs [reference]; without one, or with a normal
 * below [AssessmentScales.MIN_NORMAL_RAIN_MM], it is judged neutral. In normalized mode the z-score of rain is
 * an approximation: totals over 30 years are skewed, so a standard deviation fits them only roughly.
 */
data class Assessment(
    val delta: Double,
    val stdDev: Double?,
    val mode: TemperatureComparisonMode,
    val metric: Metric = Metric.TEMPERATURE,
    val reference: Double? = null,
) {
    private val usableStdDev: Double? =
        if (mode == TemperatureComparisonMode.NORMALIZED_ANOMALY && stdDev != null && stdDev > 0.0) {
            stdDev
        } else {
            null
        }

    /** The delta as a percent of the normal, for metrics that add up and have a normal to speak of (see [Metric.percentOfNormal]). */
    val percent: Double? get() = reference?.let { metric.percentOfNormal(delta, it) }

    /** Whether a total has no normal worth comparing with, so it stays neutral and shows no percent. */
    val lowNormal: Boolean get() = metric.aggregation == Aggregation.TOTAL && percent == null

    val normalized: Boolean get() = usableStdDev != null

    /** Signed z-score in normalized mode, otherwise the delta (in the unit of the metric's absolute scale). */
    val score: Double
        get() =
            usableStdDev?.let { delta / it }
                ?: if (metric.aggregation == Aggregation.TOTAL) percent ?: 0.0 else delta

    private val scale: AssessmentScales.Scale
        get() = if (normalized) AssessmentScales.NORMALIZED else AssessmentScales.absolute(metric)

    val neutralThreshold: Double get() = scale.neutral

    /** Magnitude class 0..3, used for the icon count and the wording. */
    val severity: Int get() = if (lowNormal) 0 else scale.severity(abs(score))

    val band: Band
        get() =
            when {
                lowNormal -> Band.NEUTRAL
                score > neutralThreshold -> Band.WARM
                score < -neutralThreshold -> Band.COLD
                else -> Band.NEUTRAL
            }
}

fun assess(
    current: Double,
    reference: Double,
    stdDev: Double?,
    mode: TemperatureComparisonMode,
): Assessment = assess(Metric.TEMPERATURE, current, reference, stdDev, mode)

/** Like the temperature overload, for the period values of [metric]. */
fun assess(
    metric: Metric,
    observed: Double,
    reference: Double,
    stdDev: Double?,
    mode: TemperatureComparisonMode,
): Assessment = Assessment(observed - reference, stdDev, mode, metric, reference)
