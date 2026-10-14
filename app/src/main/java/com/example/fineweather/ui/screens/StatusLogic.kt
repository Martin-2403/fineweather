package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.assess
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import java.util.Locale
import kotlin.math.abs

internal val WarmIconTint = Color(0xFFE53935)
internal val CoolIconTint = Color(0xFF1E88E5)
internal val NeutralIconTint = Color(0xFF43A047)

internal fun buildStatusCardValue(
    currentMonth: String,
    historic: String,
    historicMonthlyStdDev: Double?,
    mode: TemperatureComparisonMode,
): ValueWithIcons {
    val currentValue = parseTemperature(currentMonth)
    val historicValue = parseTemperature(historic)
    if (currentValue == null || historicValue == null) {
        return ValueWithIcons("-", null)
    }
    val assessment = assess(currentValue, historicValue, historicMonthlyStdDev, mode)
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
internal fun buildTrendCardValue(outlook: ForecastOutlook?): ValueWithIcons {
    val delta = outlook?.delta ?: return ValueWithIcons("-", null)
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

internal fun parseTemperature(value: String): Double? {
    val match = Regex("[-+]?\\d+(?:\\.\\d+)?").find(value) ?: return null
    return match.value.toDoubleOrNull()
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveStatusImageRes(
    currentMonth: String,
    historic: String,
    historicMonthlyStdDev: Double?,
    mode: TemperatureComparisonMode,
    status: String,
): Int {
    val currentValue = parseTemperature(currentMonth)
    val historicValue = parseTemperature(historic)
    if (currentValue == null || historicValue == null) {
        return R.drawable.initial
    }
    return when (assess(currentValue, historicValue, historicMonthlyStdDev, mode).band) {
        Band.WARM -> R.drawable.warm
        Band.COLD -> R.drawable.cold
        Band.NEUTRAL -> R.drawable.equal
    }
}
