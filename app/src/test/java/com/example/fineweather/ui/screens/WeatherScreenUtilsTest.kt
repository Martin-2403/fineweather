package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherScreenUtilsTest {

    @Test
    fun buildStatusCardValue_returnsFireForPositiveDelta() {
        val value = buildStatusCardValue(
            "12.00°C",
            "10.00°C",
            historicMonthlyStdDev = null,
            mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        )
        assertEquals("+2.00°C", value.text)
        assertEquals(R.drawable.fire, value.icons?.resId)
        assertEquals(2, value.icons?.count)
        assertEquals(Color(0xFFE53935), value.icons?.tint)
    }

    @Test
    fun buildStatusCardValue_returnsSnowForNegativeDelta() {
        val value = buildStatusCardValue(
            "8.00°C",
            "10.00°C",
            historicMonthlyStdDev = null,
            mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        )
        assertEquals("-2.00°C", value.text)
        assertEquals(R.drawable.frost, value.icons?.resId)
        assertEquals(2, value.icons?.count)
        assertEquals(Color(0xFF1E88E5), value.icons?.tint)
    }

    @Test
    fun buildStatusCardValue_returnsApproxZeroWhenClose() {
        val value = buildStatusCardValue(
            "10.30°C",
            "10.00°C",
            historicMonthlyStdDev = null,
            mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        )
        assertEquals("+0.30°C", value.text)
        assertEquals(null, value.icons)
    }

    @Test
    fun buildStatusCardValue_usesAnomalyBandsForIcons() {
        val value = buildStatusCardValue(
            "11.36°C",
            "10.00°C",
            historicMonthlyStdDev = 0.8,
            mode = TemperatureComparisonMode.NORMALIZED_ANOMALY,
        )
        assertEquals("+1.36°C", value.text)
        assertEquals(R.drawable.fire, value.icons?.resId)
        assertEquals(2, value.icons?.count)
    }

    @Test
    fun buildTrendCardValue_returnsUpDownOrDash() {
        val up = buildTrendCardValue("11.00°C", "10.00°C")
        assertEquals(R.drawable.up, up.icons?.resId)
        assertEquals(1, up.icons?.count)
        assertEquals(Color(0xFFE53935), up.icons?.tint)

        val down = buildTrendCardValue("9.00°C", "10.00°C")
        assertEquals(R.drawable.down, down.icons?.resId)
        assertEquals(1, down.icons?.count)
        assertEquals(Color(0xFF1E88E5), down.icons?.tint)

        val flat = buildTrendCardValue("10.10°C", "10.00°C")
        assertEquals("", flat.text)
        assertEquals(R.drawable.arrow_right, flat.icons?.resId)
        assertEquals(1, flat.icons?.count)
        assertEquals(Color(0xFF43A047), flat.icons?.tint)
    }

    @Test
    fun resolveStatusImageRes_returnsInitialWhenMissingData() {
        val res =
            resolveStatusImageRes(
                "-",
                "-",
                historicMonthlyStdDev = null,
                mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
                status = "Enter a city to see temperature averages",
            )
        assertEquals(R.drawable.initial, res)
    }

    @Test
    fun resolveStatusImageRes_returnsWarmColdOrEqual() {
        assertEquals(
            R.drawable.warm,
            resolveStatusImageRes(
                "12.00°C",
                "10.00°C",
                historicMonthlyStdDev = null,
                mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
                status = "Searching...",
            ),
        )
        assertEquals(
            R.drawable.cold,
            resolveStatusImageRes(
                "8.00°C",
                "10.00°C",
                historicMonthlyStdDev = null,
                mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
                status = "Searching...",
            ),
        )
        assertEquals(
            R.drawable.equal,
            resolveStatusImageRes(
                "10.00°C",
                "10.00°C",
                historicMonthlyStdDev = null,
                mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
                status = "Searching...",
            ),
        )
    }
}
