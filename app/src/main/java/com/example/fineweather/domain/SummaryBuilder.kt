package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import java.util.Locale
import kotlin.math.abs

sealed interface SummaryBlock {
    val text: String

    /** The one-sentence verdict; [band] drives its tint (null when there is no verdict). */
    data class Headline(override val text: String, val band: Band?) : SummaryBlock

    data class Detail(override val text: String) : SummaryBlock

    data class Caveat(override val text: String) : SummaryBlock
}

data class SummaryInput(
    val monthName: String,
    val monthToDate: Double?,
    val reference: Double?,
    val referenceLabel: String,
    val stdDev: Double?,
    val mode: TemperatureComparisonMode,
    val outlook: ForecastOutlook?,
    val daysObserved: Int,
    /** What the values are of; [monthToDate], [reference] and [stdDev] are period values (rain in total mm). */
    val metric: Metric = Metric.TEMPERATURE,
)

fun buildSummary(input: SummaryInput): List<SummaryBlock> {
    val observed = input.monthToDate
    val reference = input.reference
    val month = input.monthName
    if (observed == null && reference == null) {
        return listOf(
            SummaryBlock.Detail("Search for a place to see how its current month compares with the climate average."),
        )
    }
    if (observed == null) {
        return listOf(SummaryBlock.Detail("No complete day of $month yet, check back tomorrow."))
    }
    if (reference == null) {
        return listOf(SummaryBlock.Detail("The ${input.referenceLabel} reference is not available yet."))
    }

    val assessment = assess(input.metric, observed, reference, input.stdDev, input.mode)
    val blocks = mutableListOf<SummaryBlock>()
    blocks += SummaryBlock.Headline(headline(input, assessment), assessment.band)

    input.outlook?.let { outlook -> blocks += SummaryBlock.Detail(outlookText(outlook, input.mode)) }

    if (input.daysObserved in 1..6) {
        val days = if (input.daysObserved == 1) "1 day" else "${input.daysObserved} days"
        blocks += SummaryBlock.Caveat("Only $days so far, so this can change quickly.")
    }
    return blocks
}

private fun headline(
    input: SummaryInput,
    assessment: Assessment,
): String {
    val metric = input.metric
    val start = "${input.monthName} so far:"
    if (assessment.lowNormal) {
        // A normal this small makes any percent meaningless; say what fell and what is usual.
        return "$start ${metric.format(input.monthToDate!!)} of ${metric.wording.quantity}, " +
            "normal for these days is under ${AssessmentScales.MIN_NORMAL_RAIN_MM.toInt()} ${metric.unit}."
    }
    val phrase =
        if (assessment.band == Band.NEUTRAL) {
            "close to"
        } else {
            val side = if (assessment.band == Band.WARM) metric.wording.above else metric.wording.below
            when (assessment.severity) {
                3 -> "much $side than"
                2 -> "clearly $side than"
                else -> "a little $side than"
            }
        }
    val figures =
        if (metric.aggregation == Aggregation.TOTAL) {
            "${Metric.formatPercent(assessment.percent!!)}, ${metric.formatPair(input.monthToDate!!, input.reference!!)}"
        } else {
            metric.formatDelta(assessment.delta)
        }
    return "$start $phrase the ${input.referenceLabel} average ($figures)."
}

private fun outlookText(
    outlook: ForecastOutlook,
    mode: TemperatureComparisonMode,
): String {
    val metric = outlook.metric
    val period = "Next ${outlook.days} days"
    val reference = outlook.referenceMean
        ?: return "$period: forecast to ${if (metric.aggregation == Aggregation.TOTAL) "bring" else "average"} " +
            "${compact(metric, outlook.mean)}."
    // The same reference the status card judges, not one rebuilt from the delta.
    val assessment = assess(metric, outlook.mean, reference, null, mode)
    val delta = assessment.delta
    if (assessment.lowNormal) {
        return "$period: ${metric.format(outlook.mean)} of ${metric.wording.quantity} forecast, " +
            "normal for those days is under ${AssessmentScales.MIN_NORMAL_RAIN_MM.toInt()} ${metric.unit}."
    }
    val total = metric.aggregation == Aggregation.TOTAL
    if (assessment.band == Band.NEUTRAL) {
        val usual = "about as ${metric.wording.neutral} as usual"
        return if (total) "$period: $usual (${metric.format(outlook.mean)})." else "$period: $usual."
    }
    val above = assessment.band == Band.WARM
    val side = if (above) metric.wording.above else metric.wording.belowOutlook
    return if (total) {
        "$period: $side than usual (${metric.formatPair(outlook.mean, reference)})."
    } else {
        "$period: about ${compact(metric, abs(delta))} $side than usual."
    }
}

/** A value for the outlook sentences; temperature there has one decimal, not the two of [Metric.format]. */
private fun compact(
    metric: Metric,
    value: Double,
): String = if (metric == Metric.TEMPERATURE) "${format1(value)}°C" else metric.format(value)

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
