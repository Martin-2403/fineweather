package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import kotlin.math.abs

enum class Band { WARM, COLD, NEUTRAL }

/** How far a period's mean is from its reference mean, judged in °C or in standard deviations. */
data class Assessment(
    val delta: Double,
    val stdDev: Double?,
    val mode: TemperatureComparisonMode,
) {
    private val usableStdDev: Double? =
        if (mode == TemperatureComparisonMode.NORMALIZED_ANOMALY && stdDev != null && stdDev > 0.0) {
            stdDev
        } else {
            null
        }

    val normalized: Boolean get() = usableStdDev != null

    /** Signed z-score in normalized mode, otherwise the delta in °C. */
    val score: Double get() = usableStdDev?.let { delta / it } ?: delta

    val neutralThreshold: Double get() = if (normalized) 0.5 else 1.0

    /** Magnitude class 0..3, used for the icon count and the wording. */
    val severity: Int
        get() {
            val magnitude = abs(score)
            return if (normalized) {
                when {
                    magnitude < 0.5 -> 0
                    magnitude < 1.5 -> 1
                    magnitude < 2.5 -> 2
                    else -> 3
                }
            } else {
                when {
                    magnitude < 1.0 -> 0
                    magnitude < 2.0 -> 1
                    magnitude < 3.0 -> 2
                    else -> 3
                }
            }
        }

    val band: Band
        get() =
            when {
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
): Assessment = Assessment(current - reference, stdDev, mode)

/** Direction and strength of the forecast relative to the period observed so far. */
data class TrendAssessment(val delta: Double) {
    val steady: Boolean get() = abs(delta) < STEADY_BELOW
    val warming: Boolean get() = delta > 0

    /** 1..3 */
    val strength: Int
        get() =
            when {
                abs(delta) > 2.25 -> 3
                abs(delta) > 1.25 -> 2
                else -> 1
            }

    private companion object {
        const val STEADY_BELOW = 0.25
    }
}

fun assessTrend(
    forecast: Double,
    observed: Double,
): TrendAssessment = TrendAssessment(forecast - observed)
