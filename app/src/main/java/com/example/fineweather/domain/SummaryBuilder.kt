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

    val assessment = assess(observed, reference, input.stdDev, input.mode)
    val blocks = mutableListOf<SummaryBlock>()
    blocks += SummaryBlock.Headline(headline(input, assessment), assessment.band)

    if (assessment.normalized && assessment.band != Band.NEUTRAL) {
        val side = if (assessment.score > 0) "above" else "below"
        blocks +=
            SummaryBlock.Detail(
                "That is ${format1(abs(assessment.score))} standard deviations $side what is typical for $month.",
            )
    }

    input.outlook?.let { outlook -> blocks += SummaryBlock.Detail(outlookText(outlook, input.mode)) }

    if (input.daysObserved in 1..6) {
        val days = if (input.daysObserved == 1) "1 day" else "${input.daysObserved} days"
        blocks += SummaryBlock.Caveat("Only $days of $month so far, so this can change quickly.")
    }
    return blocks
}

private fun headline(
    input: SummaryInput,
    assessment: Assessment,
): String {
    val signedDelta = String.format(Locale.US, "%+.2f", assessment.delta)
    val phrase =
        if (assessment.band == Band.NEUTRAL) {
            "close to"
        } else {
            val side = if (assessment.band == Band.WARM) "warmer" else "colder"
            when (assessment.severity) {
                3 -> "much $side than"
                2 -> "clearly $side than"
                else -> "a little $side than"
            }
        }
    return "${input.monthName}: This month so far is $phrase the ${input.referenceLabel} average ($signedDelta°C)."
}

private fun outlookText(
    outlook: ForecastOutlook,
    mode: TemperatureComparisonMode,
): String {
    val period = "next ${outlook.days} days"
    val delta = outlook.delta
        ?: return "The $period are forecast to average ${format1(outlook.mean)}°C."
    val assessment = assess(outlook.mean, outlook.mean - delta, null, mode)
    if (assessment.band == Band.NEUTRAL) {
        return "The $period look about as warm as usual for those days."
    }
    val side = if (assessment.band == Band.WARM) "warmer" else "cooler"
    return "The $period are forecast to be about ${format1(abs(delta))}°C $side than usual for those days."
}

private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
