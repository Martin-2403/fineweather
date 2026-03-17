package com.example.fineweather.ui.screens

import com.example.fineweather.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherScreenUtilsTest {

    @Test
    fun buildStatusCardText_returnsFireForPositiveDelta() {
        val text = buildStatusCardText("12.00°C", "10.00°C")
        assertEquals("+2.00°C 🔥🔥", text)
    }

    @Test
    fun buildStatusCardText_returnsSnowForNegativeDelta() {
        val text = buildStatusCardText("8.00°C", "10.00°C")
        assertEquals("-2.00°C ❄️❄️", text)
    }

    @Test
    fun buildStatusCardText_returnsApproxZeroWhenClose() {
        val text = buildStatusCardText("10.30°C", "10.00°C")
        assertEquals("Normal (+/- 1°C)", text)
    }

    @Test
    fun buildTrendCardText_returnsUpDownOrDash() {
        assertEquals("🔺", buildTrendCardText("11.00°C", "10.00°C"))
        assertEquals("\uD83D\uDD3B", buildTrendCardText("9.00°C", "10.00°C"))
        assertEquals("-", buildTrendCardText("10.10°C", "10.00°C"))
    }

    @Test
    fun resolveStatusImageRes_returnsInitialWhenMissingData() {
        val res = resolveStatusImageRes("-", "-", "Enter a city to see temperature averages")
        assertEquals(R.drawable.initial, res)
    }

    @Test
    fun resolveStatusImageRes_returnsWarmColdOrEqual() {
        assertEquals(
            R.drawable.warm,
            resolveStatusImageRes("12.00°C", "10.00°C", "Searching..."),
        )
        assertEquals(
            R.drawable.cold,
            resolveStatusImageRes("8.00°C", "10.00°C", "Searching..."),
        )
        assertEquals(
            R.drawable.equal,
            resolveStatusImageRes("10.00°C", "10.00°C", "Searching..."),
        )
    }
}
