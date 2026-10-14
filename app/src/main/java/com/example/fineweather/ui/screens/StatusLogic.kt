package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.utils.formatDouble
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.Reason
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.StatusPicture
import com.example.fineweather.viewmodels.valueOrNull
import java.util.Locale
import kotlin.math.abs

internal val WarmIconTint = Color(0xFFE53935)
internal val CoolIconTint = Color(0xFF1E88E5)
internal val NeutralIconTint = Color(0xFF43A047)

/** The divergence of this month so far: a signed value in °C with warm/cold icons by severity. */
internal fun buildStatusCardValue(headline: Loadable<Assessment>): ValueWithIcons {
    val assessment =
        when (headline) {
            is Loadable.Ready -> headline.value
            is Loadable.Loading -> return ValueWithIcons("…", null, placeholder = true, description = "Loading")
            else -> return ValueWithIcons("-", null, placeholder = true)
        }
    val delta = assessment.delta
    val sign = if (delta > 0) "+" else if (delta < 0) "-" else ""
    val valueText = "$sign${String.format(Locale.US, "%.2f", abs(delta))}°C"
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
    val (resId, tint, description) =
        if (delta > 0) {
            Triple(R.drawable.fire, WarmIconTint, "$classLabel warmer than average")
        } else {
            Triple(R.drawable.frost, CoolIconTint, "$classLabel colder than average")
        }
    return ValueWithIcons(
        valueText,
        IconStack(resId = resId, count = count, tint = tint, contentDescription = description),
    )
}

/** Whether the next forecast days are warmer or cooler than normal for those same days. */
internal fun buildTrendCardValue(trend: Loadable<ForecastOutlook>): ValueWithIcons {
    if (trend is Loadable.Loading) return ValueWithIcons("…", null, placeholder = true, description = "Loading")
    val delta = trend.valueOrNull()?.delta ?: return ValueWithIcons("-", null, placeholder = true)
    val assessment = Assessment(delta, null, TemperatureComparisonMode.ABSOLUTE_DELTA)
    val (resId, tint, description) =
        when (assessment.band) {
            Band.NEUTRAL ->
                return ValueWithIcons(
                    text = "",
                    icons =
                        IconStack(
                            resId = R.drawable.arrow_right,
                            count = 1,
                            tint = NeutralIconTint,
                            contentDescription = "Forecast near normal",
                        ),
                )

            Band.WARM -> Triple(R.drawable.up, WarmIconTint, "Forecast warmer than normal")
            Band.COLD -> Triple(R.drawable.down, CoolIconTint, "Forecast cooler than normal")
        }
    return ValueWithIcons(
        text = "",
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
        // Phase 2 gives the loading state its own picture.
        StatusPicture.LOADING, StatusPicture.INITIAL -> R.drawable.initial
        StatusPicture.WARM -> R.drawable.warm
        StatusPicture.COLD -> R.drawable.cold
        StatusPicture.EQUAL -> R.drawable.equal
    }

/** A value for a row of the Current view. */
internal fun Loadable<Double>.displayText(): String =
    when (this) {
        is Loadable.Ready -> "${formatDouble(value)}°C"
        Loadable.Idle -> "-"
        Loadable.Loading -> "Loading…"
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
        Loadable.Loading -> listOf(SummaryBlock.Detail("Loading…"))
        else -> emptyList()
    }
