package com.example.fineweather.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.StatusPicture
import com.example.fineweather.viewmodels.valueOrNull

internal val WarmIconTint = Color(0xFFE53935)
internal val CoolIconTint = Color(0xFF1E88E5)
internal val NeutralIconTint = Color(0xFF43A047)

/** The tints of sunshine and rain above and below normal, for one card colour. */
internal class MetricTints(
    val sun: Color,
    val cloud: Color,
    val drop: Color,
    val dry: Color,
)

// Each is at least 3:1 against its card: light surfaceVariant 0xFFB4D7FF, dark 0xFF213448.
internal val LightMetricTints =
    MetricTints(sun = Color(0xFF9A5B00), cloud = Color(0xFF4F6275), drop = Color(0xFF1257B5), dry = Color(0xFF7A5A1E))
internal val DarkMetricTints =
    MetricTints(sun = Color(0xFFFFB300), cloud = Color(0xFFA9BCCF), drop = Color(0xFF64B5F6), dry = Color(0xFFE0B060))

/** Whether the cards are dark, which picks the tints of sunshine and rain. */
@Composable
internal fun isDarkCard(): Boolean = MaterialTheme.colorScheme.surfaceVariant.luminance() < 0.5f

/** The icon of [metric] above or below normal, and its tint. */
private fun divergenceIcon(
    metric: Metric,
    above: Boolean,
    dark: Boolean,
): Pair<Int, Color> {
    val tints = if (dark) DarkMetricTints else LightMetricTints
    return when (metric) {
        Metric.TEMPERATURE -> if (above) R.drawable.fire to WarmIconTint else R.drawable.frost to CoolIconTint
        Metric.SUNSHINE -> if (above) R.drawable.sun to tints.sun else R.drawable.cloud to tints.cloud
        Metric.PRECIPITATION -> if (above) R.drawable.drop to tints.drop else R.drawable.drop_dry to tints.dry
    }
}

/** The tint of a trend arrow: the colour of its metric's icon on that side of normal. */
internal fun arrowTint(
    metric: Metric,
    above: Boolean,
    dark: Boolean = false,
): Color = divergenceIcon(metric, above, dark).second

/** The colour of the bar beside the headline: the icon colour of the band, green when near normal. */
internal fun bandTint(
    metric: Metric,
    band: Band?,
    dark: Boolean = false,
): Color =
    when (band) {
        Band.WARM -> arrowTint(metric, above = true, dark = dark)
        Band.COLD -> arrowTint(metric, above = false, dark = dark)
        Band.NEUTRAL -> NeutralIconTint
        null -> Color.Transparent
    }

/** What the app compares, for the prompt before a search. */
private val Metric.overview: String
    get() =
        when (this) {
            Metric.TEMPERATURE -> "temperature averages"
            Metric.SUNSHINE -> "sunshine averages"
            Metric.PRECIPITATION -> "rain totals"
        }

/** The metric switch's label for screen readers. */
internal val Metric.pillDescription: String
    get() =
        when (this) {
            Metric.TEMPERATURE -> "Temperature"
            Metric.SUNSHINE -> "Sunshine"
            Metric.PRECIPITATION -> "Rain"
        }

/** [this] without the unit of [metric] behind its number, for cells too narrow for it; only sunshine's is long. */
internal fun ValueWithIcons.withoutUnit(metric: Metric): ValueWithIcons {
    val suffix = " ${metric.unit}"
    return if (metric != Metric.SUNSHINE || !text.endsWith(suffix)) this else copy(text = text.removeSuffix(suffix))
}

/** A cell that is still waiting for its value; it draws a spinner. */
private val LoadingValue = ValueWithIcons(text = "", icons = null, loading = true)

/**
 * The divergence of this month so far: a signed value (in °C, h/day or mm) with icons by
 * severity: fire/frost for temperature, sun/cloud for sunshine, drop/dry drop for rain.
 */
internal fun buildStatusCardValue(
    headline: Loadable<Assessment>,
    dark: Boolean = false,
): ValueWithIcons {
    val assessment =
        when (headline) {
            is Loadable.Ready -> headline.value
            is Loadable.Loading -> return LoadingValue
            else -> return ValueWithIcons("-", null, placeholder = true)
        }
    val delta = assessment.delta
    val valueText = assessment.metric.formatDelta(delta, signAtZero = false)
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
    val (resId, tint) = divergenceIcon(assessment.metric, above = delta > 0, dark = dark)
    val description = "$classLabel ${if (delta > 0) wording.above else wording.below} than average"
    return ValueWithIcons(
        valueText,
        IconStack(resId = resId, count = count, tint = tint, contentDescription = description),
    )
}

/**
 * Whether the next forecast days are warmer or cooler (sunnier, wetter, ...) than normal for those same days,
 * as the delta from that normal in the unit of the metric (rain in mm, not a percent), and arrows tinted by what
 * the metric is above or below. The cell is narrow, so a temperature has one decimal here.
 */
internal fun buildTrendCardValue(
    trend: Loadable<ForecastOutlook>,
    dark: Boolean = false,
): ValueWithIcons {
    if (trend is Loadable.Loading) return LoadingValue
    val outlook = trend.valueOrNull()
    val delta = outlook?.delta ?: return ValueWithIcons("-", null, placeholder = true)
    val metric = outlook.metric
    val assessment = Assessment(delta, null, TemperatureComparisonMode.ABSOLUTE_DELTA, metric, outlook.referenceMean)
    val text = metric.formatDelta(delta, signAtZero = false, compact = true)
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
                    arrowTint(metric, above = true, dark = dark),
                    "Forecast ${wording.above} than normal",
                )

            Band.COLD ->
                Triple(
                    R.drawable.down,
                    arrowTint(metric, above = false, dark = dark),
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

internal fun SearchStatus.displayText(metric: Metric = Metric.TEMPERATURE): String =
    when (this) {
        SearchStatus.Idle -> "Enter a location to see ${metric.overview}"
        is SearchStatus.Searching -> "Searching for $query..."
        is SearchStatus.Resolved -> "${label.name} (${label.coordinates})"
        is SearchStatus.NotFound -> "City not found"
    }

internal fun Loadable<List<SummaryBlock>>.toBlocks(): List<SummaryBlock> =
    when (this) {
        is Loadable.Ready -> value
        else -> emptyList()
    }
