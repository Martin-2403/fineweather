package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import kotlin.math.abs

sealed interface SummaryBlock {
    val text: String

    /** The one-sentence verdict; [band] drives its tint (null when there is no verdict). */
    data class Headline(override val text: String, val band: Band?) : SummaryBlock

    data class Detail(override val text: String) : SummaryBlock

    data class Caveat(override val text: String) : SummaryBlock
}

/** The data a summary is built from. */
enum class DataSource { OBSERVED, REFERENCE, FORECAST }

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
    /** The sources that could not be loaded, as opposed to having no data. */
    val failed: Set<DataSource> = emptySet(),
)

/**
 * The report of a month so far: the verdict first (it is the block shown beside the picture), then
 * the outlook and whatever the reader should keep in mind. It carries the figures too, so no other view
 * has to repeat them.
 */
fun buildSummary(input: SummaryInput): List<SummaryBlock> {
    val observed = input.monthToDate
    val reference = input.reference
    val month = input.monthName
    if (observed == null && reference == null) {
        return listOf(
            SummaryBlock.Detail(
                if (input.failed.isEmpty()) {
                    "Search for a place to see how its current month compares with the climate average."
                } else {
                    "The weather data could not be loaded."
                },
            ),
        )
    }

    val blocks = mutableListOf<SummaryBlock>()
    var caveat: SummaryBlock.Caveat? = null
    if (observed == null) {
        blocks +=
            SummaryBlock.Detail(
                if (DataSource.OBSERVED in input.failed) {
                    "This year's weather data could not be loaded."
                } else {
                    "No complete day of $month yet, check back tomorrow."
                },
            )
        // Without a complete day the reference is the normal of the whole month.
        reference?.let {
            blocks += SummaryBlock.Detail("The ${input.referenceLabel} average for $month is ${input.metric.format(it)}.")
        }
    } else if (reference == null) {
        blocks += SummaryBlock.Detail("The ${input.referenceLabel} reference is not available yet.")
    } else {
        val assessment = assess(input.metric, observed, reference, input.stdDev, input.mode)
        blocks += SummaryBlock.Headline(headline(input, observed, reference, assessment), assessment.band)
        if (input.daysObserved in 1..6) {
            val days = if (input.daysObserved == 1) "1 day" else "${input.daysObserved} days"
            caveat = SummaryBlock.Caveat("Only $days so far, so this can change quickly.")
        }
    }

    input.outlook?.let { outlook -> blocks += SummaryBlock.Detail(outlookText(outlook, input.mode)) }
    caveat?.let { blocks += it }
    if (DataSource.FORECAST in input.failed) blocks += SummaryBlock.Caveat("The forecast could not be loaded.")
    return blocks
}

private fun headline(
    input: SummaryInput,
    observed: Double,
    reference: Double,
    assessment: Assessment,
): String {
    val metric = input.metric
    val start = "${input.monthName} so far:"
    if (assessment.lowNormal) {
        // A normal this small makes any percent meaningless; say what fell and what is usual.
        return "$start ${metric.format(observed)} of ${metric.wording.quantity}, " +
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
    val change =
        if (metric.aggregation == Aggregation.TOTAL) Metric.formatPercent(assessment.percent!!) else metric.formatDelta(assessment.delta)
    val figures = "$change, ${metric.formatPair(observed, reference)}"
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
    val figures = compactPair(metric, outlook.mean, reference)
    if (assessment.band == Band.NEUTRAL) {
        return "$period: about as ${metric.wording.neutral} as usual ($figures)."
    }
    val above = assessment.band == Band.WARM
    val side = if (above) metric.wording.above else metric.wording.belowOutlook
    return if (total) {
        "$period: $side than usual ($figures)."
    } else {
        "$period: about ${compact(metric, abs(delta))} $side than usual ($figures)."
    }
}

/** A value for the outlook sentences; temperature there has one decimal, not the two of the full [Metric.format]. */
private fun compact(
    metric: Metric,
    value: Double,
): String = metric.format(value, compact = true)

/** [Metric.formatPair] with the temperatures of [compact]. */
private fun compactPair(
    metric: Metric,
    a: Double,
    b: Double,
): String = if (metric == Metric.TEMPERATURE) "${compact(metric, a)} vs ${compact(metric, b)}" else metric.formatPair(a, b)
