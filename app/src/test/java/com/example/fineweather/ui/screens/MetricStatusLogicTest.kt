package com.example.fineweather.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.fineweather.R
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.viewmodels.FailKind
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.Reason
import com.example.fineweather.viewmodels.SearchStatus
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
        assertEquals(R.drawable.sun, sunnier.icons?.resId)
        assertEquals(LightMetricTints.sun, sunnier.icons?.tint)
        assertEquals(2, sunnier.icons?.count)
        assertEquals("++ sunnier than average", sunnier.icons?.contentDescription)

        val duller = buildStatusCardValue(divergence(Metric.SUNSHINE, -1.6, 5.0))
        assertEquals("-1.6 h/day", duller.text)
        assertEquals(R.drawable.cloud, duller.icons?.resId)
        assertEquals(LightMetricTints.cloud, duller.icons?.tint)
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
        assertEquals(R.drawable.drop, wetter.icons?.resId)
        assertEquals(LightMetricTints.drop, wetter.icons?.tint)
        assertEquals(3, wetter.icons?.count)
        assertEquals("+++ wetter than average", wetter.icons?.contentDescription)

        val drier = buildStatusCardValue(divergence(Metric.PRECIPITATION, -20.0, 40.0))
        assertEquals("-50%", drier.text)
        assertEquals(R.drawable.drop_dry, drier.icons?.resId)
        assertEquals(LightMetricTints.dry, drier.icons?.tint)
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
        assertEquals(WarmIconTint, value.icons?.tint)
        assertEquals("++ warmer than average", value.icons?.contentDescription)
        val colder = buildStatusCardValue(divergence(Metric.TEMPERATURE, -2.0, 10.0))
        assertEquals(R.drawable.frost, colder.icons?.resId)
        assertEquals(CoolIconTint, colder.icons?.tint)
        assertEquals("-- colder than average", colder.icons?.contentDescription)
        assertEquals("0.00°C", buildStatusCardValue(divergence(Metric.TEMPERATURE, 0.0, 10.0)).text)
        assertEquals("-0.30°C", buildStatusCardValue(divergence(Metric.TEMPERATURE, -0.3, 10.0)).text)
    }

    // ---- trend card ----

    @Test
    fun trendCard_sunshineShowsHoursAndArrows() {
        val sunnier = buildTrendCardValue(outlook(Metric.SUNSHINE, 6.2, 5.0))
        assertEquals("+1.2 h/day", sunnier.text)
        assertEquals(R.drawable.up, sunnier.icons?.resId)
        assertEquals(LightMetricTints.sun, sunnier.icons?.tint)
        assertEquals(2, sunnier.icons?.count)
        assertEquals("Forecast sunnier than normal", sunnier.icons?.contentDescription)

        val duller = buildTrendCardValue(outlook(Metric.SUNSHINE, 3.4, 5.0))
        assertEquals("-1.6 h/day", duller.text)
        assertEquals(R.drawable.down, duller.icons?.resId)
        assertEquals(LightMetricTints.cloud, duller.icons?.tint)
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
        assertEquals(LightMetricTints.drop, wetter.icons?.tint)
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
    fun trendCard_temperatureIsUnchanged() {
        val warmer = buildTrendCardValue(outlook(Metric.TEMPERATURE, 12.0, 8.0))
        assertEquals("", warmer.text)
        assertEquals(R.drawable.up, warmer.icons?.resId)
        assertEquals(WarmIconTint, warmer.icons?.tint)
        assertEquals("Forecast warmer than normal", warmer.icons?.contentDescription)

        val cooler = buildTrendCardValue(outlook(Metric.TEMPERATURE, 4.0, 8.0))
        assertEquals(R.drawable.down, cooler.icons?.resId)
        assertEquals(CoolIconTint, cooler.icons?.tint)
        assertEquals("Forecast cooler than normal", cooler.icons?.contentDescription)

        assertEquals(NeutralIconTint, buildTrendCardValue(outlook(Metric.TEMPERATURE, 8.2, 8.0)).icons?.tint)
    }

    @Test
    fun trendCard_neutralIsGreenForEveryMetric() {
        listOf(Metric.SUNSHINE, Metric.PRECIPITATION).forEach {
            val value = buildTrendCardValue(outlook(it, 5.0, 5.0))
            assertEquals(NeutralIconTint, value.icons?.tint)
            assertEquals(R.drawable.arrow_right, value.icons?.resId)
        }
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
        Metric.entries.forEach { metric ->
            assertEquals("-", Loadable.Idle.displayText(metric))
            assertEquals("", Loadable.Loading.displayText(metric))
            assertEquals("N/A", Loadable.Unavailable(Reason.NOT_AVAILABLE).displayText(metric))
            assertEquals("No data yet", Loadable.Unavailable(Reason.NO_DATA_YET).displayText(metric))
            assertEquals("Error", Loadable.Failed(FailKind.NETWORK).displayText(metric))
        }
    }

    // ---- texts of the metric switch ----

    @Test
    fun metricTexts_followTheMetric() {
        assertEquals("Average temperatures", Metric.TEMPERATURE.currentTitle)
        assertEquals("Average sunshine", Metric.SUNSHINE.currentTitle)
        assertEquals("Rain totals", Metric.PRECIPITATION.currentTitle)
        assertEquals(
            listOf("Temperature", "Sunshine", "Rain"),
            Metric.entries.map { it.pillDescription },
        )
        assertEquals("average", Metric.SUNSHINE.summaryNoun)
        assertEquals("total", Metric.PRECIPITATION.summaryNoun)
        assertEquals("Enter a location to see temperature averages", SearchStatus.Idle.displayText())
        assertEquals("Enter a location to see sunshine averages", SearchStatus.Idle.displayText(Metric.SUNSHINE))
        assertEquals("Enter a location to see rain totals", SearchStatus.Idle.displayText(Metric.PRECIPITATION))
    }

    @Test
    fun iconTints_areDistinctPerSideAndMetricInEachTheme() {
        listOf(LightMetricTints, DarkMetricTints).forEach {
            val tints = listOf(WarmIconTint, CoolIconTint, it.sun, it.cloud, it.drop, it.dry)
            assertEquals(tints.size, tints.toSet().size)
        }
    }

    @Test
    fun iconTints_areThemeAware() {
        fun tints(dark: Boolean) =
            listOf(
                buildStatusCardValue(divergence(Metric.SUNSHINE, 1.2, 5.0), dark).icons?.tint,
                buildStatusCardValue(divergence(Metric.SUNSHINE, -1.2, 5.0), dark).icons?.tint,
                buildStatusCardValue(divergence(Metric.PRECIPITATION, 29.0, 33.0), dark).icons?.tint,
                buildStatusCardValue(divergence(Metric.PRECIPITATION, -20.0, 40.0), dark).icons?.tint,
                buildTrendCardValue(outlook(Metric.SUNSHINE, 6.2, 5.0), dark).icons?.tint,
                buildTrendCardValue(outlook(Metric.SUNSHINE, 3.4, 5.0), dark).icons?.tint,
                buildTrendCardValue(outlook(Metric.PRECIPITATION, 25.0, 14.0), dark).icons?.tint,
                buildTrendCardValue(outlook(Metric.PRECIPITATION, 5.0, 14.0), dark).icons?.tint,
            )

        val light = LightMetricTints
        assertEquals(listOf(light.sun, light.cloud, light.drop, light.dry, light.sun, light.cloud, light.drop, light.dry), tints(false))
        val dark = DarkMetricTints
        assertEquals(listOf(dark.sun, dark.cloud, dark.drop, dark.dry, dark.sun, dark.cloud, dark.drop, dark.dry), tints(true))
    }

    @Test
    fun temperatureTints_doNotDependOnTheTheme() {
        listOf(false, true).forEach { dark ->
            assertEquals(WarmIconTint, buildStatusCardValue(divergence(Metric.TEMPERATURE, 2.0, 10.0), dark).icons?.tint)
            assertEquals(CoolIconTint, buildStatusCardValue(divergence(Metric.TEMPERATURE, -2.0, 10.0), dark).icons?.tint)
            assertEquals(WarmIconTint, buildTrendCardValue(outlook(Metric.TEMPERATURE, 12.0, 8.0), dark).icons?.tint)
            assertEquals(CoolIconTint, buildTrendCardValue(outlook(Metric.TEMPERATURE, 4.0, 8.0), dark).icons?.tint)
            assertEquals(WarmIconTint, bandTint(Metric.TEMPERATURE, Band.WARM, dark))
            assertEquals(CoolIconTint, bandTint(Metric.TEMPERATURE, Band.COLD, dark))
        }
    }

    @Test
    fun iconTints_haveAtLeastThreeToOneContrastOnTheirCard() {
        // the surfaceVariant of the light and the dark theme, which is the colour of every card
        val lightCard = Color(0xFFB4D7FF)
        val darkCard = Color(0xFF213448)
        fun contrast(
            a: Color,
            b: Color,
        ): Double {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05) / (lo + 0.05)
        }
        listOf(LightMetricTints to lightCard, DarkMetricTints to darkCard).forEach { (tints, card) ->
            listOf(tints.sun, tints.cloud, tints.drop, tints.dry).forEach {
                assertTrue("$it on $card is ${contrast(it, card)}", contrast(it, card) >= 3.0)
            }
        }
    }

    // ---- headline bar ----

    @Test
    fun bandTint_followsTheIconTintsOfTheMetric() {
        Metric.entries.forEach { metric ->
            listOf(false, true).forEach { dark ->
                assertEquals(NeutralIconTint, bandTint(metric, Band.NEUTRAL, dark))
                assertEquals(Color.Transparent, bandTint(metric, null, dark))
                assertEquals(arrowTint(metric, above = true, dark = dark), bandTint(metric, Band.WARM, dark))
                assertEquals(arrowTint(metric, above = false, dark = dark), bandTint(metric, Band.COLD, dark))
            }
        }
        // a dull month is slate and a wet one blue, not the warm red and cold blue of the temperature
        assertEquals(LightMetricTints.cloud, bandTint(Metric.SUNSHINE, Band.COLD))
        assertEquals(LightMetricTints.sun, bandTint(Metric.SUNSHINE, Band.WARM))
        assertEquals(DarkMetricTints.dry, bandTint(Metric.PRECIPITATION, Band.COLD, dark = true))
        assertEquals(DarkMetricTints.drop, bandTint(Metric.PRECIPITATION, Band.WARM, dark = true))
    }

    // ---- narrow cells ----

    @Test
    fun withoutUnit_dropsTheUnitOfSunshineAndRainOnly() {
        val sun = ValueWithIcons("-3.2 h/day", null)
        assertEquals("-3.2", sun.withoutUnit(Metric.SUNSHINE).text)
        assertEquals("+2.0", ValueWithIcons("+2.0 mm", null).withoutUnit(Metric.PRECIPITATION).text)
        assertEquals("+33%", ValueWithIcons("+33%", null).withoutUnit(Metric.PRECIPITATION).text)
        assertEquals("-", ValueWithIcons("-", null, placeholder = true).withoutUnit(Metric.SUNSHINE).text)
        assertEquals("", ValueWithIcons("", null).withoutUnit(Metric.TEMPERATURE).text)
        assertEquals("+1.43°C", ValueWithIcons("+1.43°C", null).withoutUnit(Metric.TEMPERATURE).text)
    }
}
