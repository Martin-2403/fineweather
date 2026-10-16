package com.example.fineweather.ui.screens

import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The pure helpers behind the sunshine and rain charts, and the pins that keep the temperature chart as it was. */
class MetricChartUtilsTest {

    private fun month(
        metric: Metric,
        observed: Double?,
        reference: Double?,
        partial: Boolean = false,
    ) = MonthlyAnomaly(3, observed, reference, null, 20, partial, metric)

    // ---- daily axis ----

    @Test
    fun dailyBarAxis_startsAtZeroWithRoundTicks() {
        val sun = dailyBarAxis(Metric.SUNSHINE, 13.2)
        assertEquals(0.0, sun.min, 1e-9)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0), sun.ticks)

        val rain = dailyBarAxis(Metric.PRECIPITATION, 19.4)
        assertEquals(0.0, rain.min, 1e-9)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), rain.ticks)
    }

    @Test
    fun dailyBarAxis_hasAMinimumSpanForDryOrDullPeriods() {
        assertEquals(4.0, dailyBarAxis(Metric.SUNSHINE, 0.3).max, 1e-9)
        assertEquals(4.0, dailyBarAxis(Metric.PRECIPITATION, 0.0).max, 1e-9)
        assertEquals(4.0, dailyBarAxis(Metric.PRECIPITATION, 1.2).max, 1e-9)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), dailyBarAxis(Metric.PRECIPITATION, 1.2).ticks)
    }

    @Test
    fun dailyBarAxis_coversTheLargestValue() {
        val axis = dailyBarAxis(Metric.PRECIPITATION, 47.0)
        assertTrue(axis.max >= 47.0)
        assertEquals(0.0, axis.ticks.first(), 1e-9)
    }

    @Test
    fun cumulativeDailyValues_accumulatesObservedAndForecastAcrossMissingDays() {
        val points =
            listOf(
                DayPoint(LocalDate.of(2026, 10, 1), 2.0, null, null, null),
                DayPoint(LocalDate.of(2026, 10, 2), 3.0, null, null, null),
                DayPoint(LocalDate.of(2026, 10, 3), null, null, null, null),
                DayPoint(LocalDate.of(2026, 10, 4), null, 4.0, null, null),
            )

        val accumulated = cumulativeDailyValues(points)

        assertEquals(2.0, accumulated[0]!!, 1e-9)
        assertEquals(5.0, accumulated[1]!!, 1e-9)
        assertNull(accumulated[2])
        assertEquals(9.0, accumulated[3]!!, 1e-9)
    }

    @Test
    fun cumulativeAverageDailyValues_accumulatesHistoricalDailyAverages() {
        val points =
            listOf(
                DayPoint(LocalDate.of(2026, 10, 1), 2.0, null, null, 3.0),
                DayPoint(LocalDate.of(2026, 10, 2), 3.0, null, null, 4.0),
                DayPoint(LocalDate.of(2026, 10, 3), null, null, null, null),
                DayPoint(LocalDate.of(2026, 10, 4), null, 4.0, null, 5.0),
            )

        val accumulated = cumulativeAverageDailyValues(points)

        assertEquals(3.0, accumulated[0]!!, 1e-9)
        assertEquals(7.0, accumulated[1]!!, 1e-9)
        assertNull(accumulated[2])
        assertEquals(12.0, accumulated[3]!!, 1e-9)
    }

    @Test
    fun cumulativeAverageDailyValues_extendsTheLineWithFutureDailyNormals() {
        val points =
            listOf(
                DayPoint(LocalDate.of(2026, 10, 30), 2.0, null, null, 3.0),
            )

        val accumulated = cumulativeAverageDailyValues(points, listOf(4.0, 5.0))

        assertEquals(3.0, accumulated[0]!!, 1e-9)
        assertEquals(7.0, accumulated[1]!!, 1e-9)
        assertEquals(12.0, accumulated[2]!!, 1e-9)
    }

    // ---- anomaly axis ----

    @Test
    fun anomalyAxis_temperatureIsTheOldOne() {
        assertEquals(niceAxis(0.0, 1.0), anomalyAxis(Metric.TEMPERATURE, emptyList()))
        assertEquals(niceAxis(0.0, 1.0), anomalyAxis(Metric.TEMPERATURE, listOf(0.2, 0.4)))
        assertEquals(niceAxis(-1.2, 5.3), anomalyAxis(Metric.TEMPERATURE, listOf(-1.2, 5.3, 2.0)))
    }

    @Test
    fun anomalyAxis_sunshineSpansAtLeastOnePlusMinusOneHour() {
        val axis = anomalyAxis(Metric.SUNSHINE, listOf(0.2, -0.1))
        assertEquals(-1.0, axis.min, 1e-9)
        assertEquals(1.0, axis.max, 1e-9)
        assertTrue(axis.ticks.contains(0.0))

        val wide = anomalyAxis(Metric.SUNSHINE, listOf(-1.1, 3.0))
        assertTrue(wide.min <= -1.1 && wide.max >= 3.0)
    }

    @Test
    fun anomalyAxis_rainSpansAtLeastFiftyPercentEitherWay() {
        val axis = anomalyAxis(Metric.PRECIPITATION, listOf(10.0))
        assertEquals(-50.0, axis.min, 1e-9)
        assertEquals(50.0, axis.max, 1e-9)

        val wide = anomalyAxis(Metric.PRECIPITATION, listOf(-77.0, 63.0))
        assertEquals(-100.0, wide.min, 1e-9)
        assertTrue(wide.max >= 63.0)
        assertEquals(listOf(-50.0, -25.0, 0.0, 25.0, 50.0), anomalyAxis(Metric.PRECIPITATION, emptyList()).ticks)
    }

    // ---- what a month's bar shows ----

    @Test
    fun anomalyValue_isTheDeltaExceptForRainWhichIsAPercent() {
        assertEquals(1.5, anomalyValue(Metric.TEMPERATURE, month(Metric.TEMPERATURE, 11.5, 10.0))!!, 1e-9)
        assertEquals(-0.8, anomalyValue(Metric.SUNSHINE, month(Metric.SUNSHINE, 4.2, 5.0))!!, 1e-9)
        assertEquals(35.0, anomalyValue(Metric.PRECIPITATION, month(Metric.PRECIPITATION, 81.0, 60.0))!!, 1e-9)
    }

    @Test
    fun anomalyValue_rainWithATinyNormalHasNoBar() {
        val dry = month(Metric.PRECIPITATION, 3.0, 1.5)

        assertNull(anomalyValue(Metric.PRECIPITATION, dry))
        assertEquals(1.5, dry.delta!!, 1e-9)
        assertNull(anomalyValue(Metric.PRECIPITATION, month(Metric.PRECIPITATION, null, 60.0)))
    }

    // ---- labels ----

    @Test
    fun dailyTickLabel_carriesTheUnit() {
        assertEquals("10", dailyTickLabel(Metric.TEMPERATURE, 10.0))
        assertEquals("5 h", dailyTickLabel(Metric.SUNSHINE, 5.0))
        assertEquals("2.5 h", dailyTickLabel(Metric.SUNSHINE, 2.5))
        assertEquals("20 mm", dailyTickLabel(Metric.PRECIPITATION, 20.0))
        assertEquals("0 mm", dailyTickLabel(Metric.PRECIPITATION, 0.0))
    }

    @Test
    fun anomalyTickLabel_isInHoursOrPercent() {
        assertEquals("-5", anomalyTickLabel(Metric.TEMPERATURE, -5.0))
        assertEquals("-1 h", anomalyTickLabel(Metric.SUNSHINE, -1.0))
        assertEquals("0.5 h", anomalyTickLabel(Metric.SUNSHINE, 0.5))
        assertEquals("50%", anomalyTickLabel(Metric.PRECIPITATION, 50.0))
        assertEquals("-100%", anomalyTickLabel(Metric.PRECIPITATION, -100.0))
    }

    @Test
    fun anomalyBarLabel_temperatureIsUnchangedAndTheOthersAreCompact() {
        assertEquals("+1.8", anomalyBarLabel(Metric.TEMPERATURE, 1.84))
        assertEquals("-0.5", anomalyBarLabel(Metric.TEMPERATURE, -0.45))
        assertEquals("+0.8", anomalyBarLabel(Metric.SUNSHINE, 0.84))
        assertEquals("-1.1", anomalyBarLabel(Metric.SUNSHINE, -1.14))
        assertEquals("0.0", anomalyBarLabel(Metric.SUNSHINE, 0.02))
        assertEquals("+35%", anomalyBarLabel(Metric.PRECIPITATION, 34.6))
        assertEquals("-77%", anomalyBarLabel(Metric.PRECIPITATION, -77.2))
        assertEquals("0%", anomalyBarLabel(Metric.PRECIPITATION, 0.2))
    }

    // ---- colours ----

    @Test
    fun dailyBarColor_sunshineIsAmber() {
        listOf(false, true).forEach { dark ->
            val tints = if (dark) DarkMetricTints else LightMetricTints
            assertEquals(tints.sun, dailyBarColor(Metric.SUNSHINE, dark))
        }
    }

    @Test
    fun dailyBarColor_rainIsOneBlueWhateverTheTypicalDay() {
        assertEquals(LightMetricTints.drop, dailyBarColor(Metric.PRECIPITATION, dark = false))
        assertEquals(DarkMetricTints.drop, dailyBarColor(Metric.PRECIPITATION, dark = true))
    }

    @Test
    fun anomalyBarColor_followsTheSideOfTheNormalForEveryMetric() {
        assertEquals(WarmIconTint, anomalyBarColor(Metric.TEMPERATURE, 0.0, dark = false))
        assertEquals(CoolIconTint, anomalyBarColor(Metric.TEMPERATURE, -0.1, dark = false))
        assertEquals(DarkMetricTints.sun, anomalyBarColor(Metric.SUNSHINE, 0.4, dark = true))
        assertEquals(DarkMetricTints.cloud, anomalyBarColor(Metric.SUNSHINE, -0.4, dark = true))
        assertEquals(LightMetricTints.drop, anomalyBarColor(Metric.PRECIPITATION, 12.0, dark = false))
        assertEquals(LightMetricTints.dry, anomalyBarColor(Metric.PRECIPITATION, -12.0, dark = false))
        assertNotEquals(LightMetricTints.sun, DarkMetricTints.sun)
    }

    // ---- geometry ----

    @Test
    fun barSlots_dividesTheWidthAndCentresTheBars() {
        val slots = barSlots(count = 10, width = 200f, fill = 0.7f)

        assertEquals(20f, slots.slot, 1e-6f)
        assertEquals(14f, slots.barWidth, 1e-6f)
        assertEquals(40f + 10f, slots.center(left = 40f, index = 0), 1e-6f)
        assertEquals(40f + 190f, slots.center(left = 40f, index = 9), 1e-6f)
    }

    @Test
    fun barSlots_aFewBarsAreCappedInWidth() {
        val slots = barSlots(count = 2, width = 400f, maxBarWidth = 30f)

        assertEquals(200f, slots.slot, 1e-6f)
        assertEquals(30f, slots.barWidth, 1e-6f)
    }

    @Test
    fun barSlots_noPointsDoNotDivideByZero() {
        assertEquals(100f, barSlots(0, 100f).slot, 1e-6f)
    }

    @Test
    fun barHeight_scalesWithTheAxisAndZeroKeepsAStub() {
        assertEquals(50f, barHeight(5.0, 10.0, 100f, 2f), 1e-6f)
        assertEquals(100f, barHeight(10.0, 10.0, 100f, 2f), 1e-6f)
        // a dry day and a sliver of rain both show
        assertEquals(2f, barHeight(0.0, 10.0, 100f, 2f), 1e-6f)
        assertEquals(2f, barHeight(0.05, 10.0, 100f, 2f), 1e-6f)
    }
}
