package com.example.fineweather.ui.screens

import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.viewmodels.FailKind
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricStatusLogicTest {

    private fun divergence(
        metric: Metric,
        delta: Double,
        reference: Double,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
    ) = Loadable.Ready(Assessment(delta, stdDev, mode, metric, reference))

    private fun outlook(
        metric: Metric,
        mean: Double,
        reference: Double?,
    ) = Loadable.Ready(ForecastOutlook(days = 7, mean = mean, referenceMean = reference, metric = metric))

    // ---- divergence card ----

    @Test
    fun statusCard_sunshineShowsSignedHoursAndSeverityArrows() {
        val sunnier = buildStatusCardValue(divergence(Metric.SUNSHINE, 1.2, 5.0))
        assertEquals("+1.2 h/day", sunnier.text)
        assertEquals(R.drawable.up, sunnier.icons?.resId)
        assertEquals(2, sunnier.icons?.count)
        assertEquals("++ sunnier than average", sunnier.icons?.contentDescription)

        val duller = buildStatusCardValue(divergence(Metric.SUNSHINE, -1.6, 5.0))
        assertEquals("-1.6 h/day", duller.text)
        assertEquals(R.drawable.down, duller.icons?.resId)
        assertEquals(3, duller.icons?.count)
        assertEquals("--- duller than average", duller.icons?.contentDescription)

        val near = buildStatusCardValue(divergence(Metric.SUNSHINE, 0.3, 5.0))
        assertEquals("+0.3 h/day", near.text)
        assertNull(near.icons)
    }

    @Test
    fun statusCard_rainShowsThePercentOfTheNormal() {
        val wetter = buildStatusCardValue(divergence(Metric.PRECIPITATION, 29.0, 33.0))
        assertEquals("+88%", wetter.text)
        assertEquals(R.drawable.up, wetter.icons?.resId)
        assertEquals(3, wetter.icons?.count)
        assertEquals("+++ wetter than average", wetter.icons?.contentDescription)

        val drier = buildStatusCardValue(divergence(Metric.PRECIPITATION, -20.0, 40.0))
        assertEquals("-50%", drier.text)
        assertEquals(R.drawable.down, drier.icons?.resId)
        assertEquals(2, drier.icons?.count)
        assertEquals("-- drier than average", drier.icons?.contentDescription)

        val near = buildStatusCardValue(divergence(Metric.PRECIPITATION, 2.0, 33.0))
        assertEquals("+6%", near.text)
        assertNull(near.icons)
    }

    @Test
    fun statusCard_rainWithATinyNormalShowsTheDeltaInMillimetresAndNoIcons() {
        val value = buildStatusCardValue(divergence(Metric.PRECIPITATION, 3.0, 1.0))

        assertEquals("+3.0 mm", value.text)
        assertNull(value.icons)
        assertFalse(value.placeholder)
    }

    @Test
    fun statusCard_rainUsesTheStandardDeviationsInAnomalyMode() {
        val value =
            buildStatusCardValue(
                divergence(Metric.PRECIPITATION, 29.0, 33.0, stdDev = 20.0, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY),
            )

        assertEquals("+88%", value.text)
        assertEquals(1, value.icons?.count) // z = 1.45
    }

    @Test
    fun statusCard_temperatureIsUnchanged() {
        val value = buildStatusCardValue(divergence(Metric.TEMPERATURE, 2.0, 10.0))

        assertEquals("+2.00°C", value.text)
        assertEquals(R.drawable.fire, value.icons?.resId)
        assertEquals("++ warmer than average", value.icons?.contentDescription)
        assertEquals("0.00°C", buildStatusCardValue(divergence(Metric.TEMPERATURE, 0.0, 10.0)).text)
        assertEquals("-0.30°C", buildStatusCardValue(divergence(Metric.TEMPERATURE, -0.3, 10.0)).text)
    }

    // ---- trend card ----

    @Test
    fun trendCard_sunshineShowsHoursAndArrows() {
        val sunnier = buildTrendCardValue(outlook(Metric.SUNSHINE, 6.2, 5.0))
        assertEquals("+1.2 h/day", sunnier.text)
        assertEquals(R.drawable.up, sunnier.icons?.resId)
        assertEquals(2, sunnier.icons?.count)
        assertEquals("Forecast sunnier than normal", sunnier.icons?.contentDescription)

        val duller = buildTrendCardValue(outlook(Metric.SUNSHINE, 3.4, 5.0))
        assertEquals("-1.6 h/day", duller.text)
        assertEquals(R.drawable.down, duller.icons?.resId)
        assertEquals(3, duller.icons?.count)
        assertEquals("Forecast duller than normal", duller.icons?.contentDescription)

        val steady = buildTrendCardValue(outlook(Metric.SUNSHINE, 5.2, 5.0))
        assertEquals("+0.2 h/day", steady.text)
        assertEquals(R.drawable.arrow_right, steady.icons?.resId)
        assertEquals("Forecast near normal", steady.icons?.contentDescription)
    }

    @Test
    fun trendCard_rainShowsThePercentOfTheNormalTotal() {
        val wetter = buildTrendCardValue(outlook(Metric.PRECIPITATION, 25.0, 14.0))
        assertEquals("+79%", wetter.text)
        assertEquals(R.drawable.up, wetter.icons?.resId)
        assertEquals(3, wetter.icons?.count)
        assertEquals("Forecast wetter than normal", wetter.icons?.contentDescription)

        val drier = buildTrendCardValue(outlook(Metric.PRECIPITATION, 5.0, 14.0))
        assertEquals("-64%", drier.text)
        assertEquals(2, drier.icons?.count)
        assertEquals("Forecast drier than normal", drier.icons?.contentDescription)

        val steady = buildTrendCardValue(outlook(Metric.PRECIPITATION, 15.0, 14.0))
        assertEquals("+7%", steady.text)
        assertEquals(R.drawable.arrow_right, steady.icons?.resId)
    }

    @Test
    fun trendCard_rainWithATinyNormalShowsMillimetresAndStaysNeutral() {
        val value = buildTrendCardValue(outlook(Metric.PRECIPITATION, 3.0, 1.0))

        assertEquals("+2.0 mm", value.text)
        assertEquals(R.drawable.arrow_right, value.icons?.resId)
    }

    @Test
    fun trendCard_isADashWithoutANormal() {
        listOf(Metric.SUNSHINE, Metric.PRECIPITATION).forEach {
            val value = buildTrendCardValue(outlook(it, 5.0, null))
            assertEquals("-", value.text)
            assertTrue(value.placeholder)
        }
    }

    // ---- row text ----

    @Test
    fun displayText_formatsRowsPerMetric() {
        assertEquals("5.3 h/day", Loadable.Ready(5.26).displayText(Metric.SUNSHINE))
        assertEquals("42 mm", Loadable.Ready(42.4).displayText(Metric.PRECIPITATION))
        assertEquals("7.5 mm", Loadable.Ready(7.54).displayText(Metric.PRECIPITATION))
        assertEquals("12.30°C", Loadable.Ready(12.3).displayText(Metric.TEMPERATURE))
        assertEquals("12.30°C", Loadable.Ready(12.3).displayText())
        Metric.entries.forEach { metric ->
            assertEquals("-", Loadable.Idle.displayText(metric))
            assertEquals("", Loadable.Loading.displayText(metric))
            assertEquals("N/A", Loadable.Unavailable(Reason.NOT_AVAILABLE).displayText(metric))
            assertEquals("No data yet", Loadable.Unavailable(Reason.NO_DATA_YET).displayText(metric))
            assertEquals("Error", Loadable.Failed(FailKind.NETWORK).displayText(metric))
        }
    }
}
