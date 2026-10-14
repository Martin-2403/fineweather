package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.ForecastOutlook
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

    private fun outlook(delta: Double?) =
        ForecastOutlook(days = 7, mean = 10.0 + (delta ?: 0.0), referenceMean = delta?.let { 10.0 })

    @Test
    fun buildTrendCardValue_returnsWarmerCoolerOrSteadyAgainstTheNormal() {
        val warmer = buildTrendCardValue(outlook(1.5))
        assertEquals(R.drawable.up, warmer.icons?.resId)
        assertEquals(1, warmer.icons?.count)
        assertEquals(Color(0xFFE53935), warmer.icons?.tint)
        assertEquals("Forecast warmer than normal", warmer.icons?.contentDescription)

        val cooler = buildTrendCardValue(outlook(-2.4))
        assertEquals(R.drawable.down, cooler.icons?.resId)
        assertEquals(2, cooler.icons?.count)
        assertEquals(Color(0xFF1E88E5), cooler.icons?.tint)
        assertEquals("Forecast cooler than normal", cooler.icons?.contentDescription)

        val much = buildTrendCardValue(outlook(3.5))
        assertEquals(3, much.icons?.count)

        val steady = buildTrendCardValue(outlook(0.6))
        assertEquals("", steady.text)
        assertEquals(R.drawable.arrow_right, steady.icons?.resId)
        assertEquals(1, steady.icons?.count)
        assertEquals(Color(0xFF43A047), steady.icons?.tint)
        assertEquals("Forecast near normal", steady.icons?.contentDescription)
    }

    @Test
    fun buildTrendCardValue_returnsDashWithoutAnOutlookOrReference() {
        assertEquals("-", buildTrendCardValue(null).text)
        assertEquals("-", buildTrendCardValue(outlook(null)).text)
        assertEquals(null, buildTrendCardValue(null).icons)
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
