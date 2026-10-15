package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.Reason
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.StatusPicture
import com.example.fineweather.viewmodels.valueOrNull

internal val WarmIconTint = Color(0xFFE53935)
internal val CoolIconTint = Color(0xFF1E88E5)
internal val NeutralIconTint = Color(0xFF43A047)

/** TODO(sun/rain icons): stand-in tint of the arrows shown for sunshine and rain. */
private val MetricPlaceholderTint = Color(0xFF757575)

/** The signed delta of [assessment] as a status card shows it: a percent of the normal for rain, else the delta. */
private fun deltaText(assessment: Assessment): String {
    val metric = assessment.metric
    return assessment.percent?.let { Metric.formatPercent(it) } ?: metric.formatDelta(assessment.delta, signAtZero = false)
}

/** A cell that is still waiting for its value; it draws a spinner. */
private val LoadingValue = ValueWithIcons(text = "", icons = null, loading = true)

/**
 * The divergence of this month so far: a signed value (in °C, h/day, or % of the normal for rain) with icons by
 * severity. Temperature has its warm/cold icons; sunshine and rain get plain arrows.
 * TODO(sun/rain icons): replace the placeholder arrows and their grey tint.
 */
internal fun buildStatusCardValue(headline: Loadable<Assessment>): ValueWithIcons {
    val assessment =
        when (headline) {
            is Loadable.Ready -> headline.value
            is Loadable.Loading -> return LoadingValue
            else -> return ValueWithIcons("-", null, placeholder = true)
        }
    val delta = assessment.delta
    val valueText = deltaText(assessment)
    val count = assessment.severity
    if (count == 0) {
        return ValueWithIcons(valueText, null)
    }
    val classLabel =
        when (count) {
            1 -> if (delta > 0) "+" else "-"
            2 -> if (delta > 0) "++" else "--"
            else -> if (delta > 0) "+++" else "---"
        }
    val wording = assessment.metric.wording
    val (resId, tint, description) =
        when {
            assessment.metric != Metric.TEMPERATURE ->
                Triple(
                    if (delta > 0) R.drawable.up else R.drawable.down,
                    MetricPlaceholderTint,
                    "$classLabel ${if (delta > 0) wording.above else wording.below} than average",
                )

            delta > 0 -> Triple(R.drawable.fire, WarmIconTint, "$classLabel warmer than average")
            else -> Triple(R.drawable.frost, CoolIconTint, "$classLabel colder than average")
        }
    return ValueWithIcons(
        valueText,
        IconStack(resId = resId, count = count, tint = tint, contentDescription = description),
    )
}

/**
 * Whether the next forecast days are warmer or cooler (sunnier, wetter, ...) than normal for those same days.
 * Sunshine and rain also show the delta as text, with placeholder arrows.
 * TODO(sun/rain icons): replace the placeholder arrows and their grey tint.
 */
internal fun buildTrendCardValue(trend: Loadable<ForecastOutlook>): ValueWithIcons {
    if (trend is Loadable.Loading) return LoadingValue
    val outlook = trend.valueOrNull()
    val delta = outlook?.delta ?: return ValueWithIcons("-", null, placeholder = true)
    val metric = outlook.metric
    val assessment = Assessment(delta, null, TemperatureComparisonMode.ABSOLUTE_DELTA, metric, outlook.referenceMean)
    val text = if (metric == Metric.TEMPERATURE) "" else deltaText(assessment)
    val wording = metric.wording
    val (resId, tint, description) =
        when (assessment.band) {
            Band.NEUTRAL ->
                return ValueWithIcons(
                    text = text,
                    icons =
                        IconStack(
                            resId = R.drawable.arrow_right,
                            count = 1,
                            tint = NeutralIconTint,
                            contentDescription = "Forecast near normal",
                        ),
                )

            Band.WARM ->
                Triple(
                    R.drawable.up,
                    if (metric == Metric.TEMPERATURE) WarmIconTint else MetricPlaceholderTint,
                    "Forecast ${wording.above} than normal",
                )

            Band.COLD ->
                Triple(
                    R.drawable.down,
                    if (metric == Metric.TEMPERATURE) CoolIconTint else MetricPlaceholderTint,
                    "Forecast ${wording.belowOutlook} than normal",
                )
        }
    return ValueWithIcons(
        text = text,
        icons =
            IconStack(
                resId = resId,
                count = assessment.severity.coerceAtLeast(1),
                tint = tint,
                contentDescription = description,
            ),
    )
}

internal fun resolveStatusImageRes(picture: StatusPicture): Int =
    when (picture) {
        // While loading, the header shows a spinner instead of this picture.
        StatusPicture.LOADING, StatusPicture.INITIAL -> R.drawable.initial
        StatusPicture.WARM -> R.drawable.warm
        StatusPicture.COLD -> R.drawable.cold
        StatusPicture.EQUAL -> R.drawable.equal
    }

/** A temperature value for a row of the Current view. */
internal fun Loadable<Double>.displayText(): String = displayText(Metric.TEMPERATURE)

/** A value of [metric] for a row of the Current view. */
internal fun Loadable<Double>.displayText(metric: Metric): String =
    when (this) {
        is Loadable.Ready -> metric.format(value)
        Loadable.Idle -> "-"
        // Drawn as a skeleton bar, so this text is never shown.
        Loadable.Loading -> ""
        is Loadable.Unavailable -> reason.displayText()
        is Loadable.Failed -> "Error"
    }

internal fun Reason.displayText(): String =
    when (this) {
        Reason.NO_DATA_YET -> "No data yet"
        Reason.NOT_AVAILABLE -> "N/A"
    }

internal fun Loadable<*>.isPlaceholder(): Boolean = this !is Loadable.Ready

internal fun SearchStatus.displayText(): String =
    when (this) {
        SearchStatus.Idle -> "Enter a location to see temperature averages"
        is SearchStatus.Searching -> "Searching for $query..."
        is SearchStatus.Resolved -> "${label.name} (${label.coordinates})"
        is SearchStatus.NotFound -> "City not found"
    }

internal fun Loadable<List<SummaryBlock>>.toBlocks(): List<SummaryBlock> =
    when (this) {
        is Loadable.Ready -> value
        else -> emptyList()
    }
