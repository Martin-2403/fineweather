package com.example.fineweather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricTest {

    @Test
    fun metricsDescribeThemselvesAndTheirApiVariable() {
        assertEquals(listOf("Temp", "Sun", "Rain"), Metric.entries.map { it.label })
        assertEquals(listOf("°C", "h/day", "mm"), Metric.entries.map { it.unit })
        assertEquals(
            listOf("temperature_2m_mean", "sunshine_duration", "precipitation_sum"),
            Metric.entries.map { it.apiName },
        )
    }

    @Test
    fun onlySunshineIsConvertedFromSecondsToHours() {
        assertEquals(1.0, Metric.TEMPERATURE.apiDivisor, 0.0)
        assertEquals(1.0, Metric.PRECIPITATION.apiDivisor, 0.0)
        assertEquals(3600.0, Metric.SUNSHINE.apiDivisor, 0.0)
        // dividing keeps whole hours exact
        assertEquals(2.0, 7200.0 / Metric.SUNSHINE.apiDivisor, 0.0)
    }

    @Test
    fun onlyRainAddsUp() {
        assertEquals(
            listOf(Aggregation.MEAN, Aggregation.MEAN, Aggregation.TOTAL),
            Metric.entries.map { it.aggregation },
        )
        assertEquals(listOf(null, 0.0, 0.0), Metric.entries.map { it.lowerBound })
    }

    @Test
    fun periodValue_isTheMeanOrTheMeanTimesTheDays() {
        assertEquals(12.5, Metric.TEMPERATURE.periodValue(12.5, 7), 0.0)
        assertEquals(5.5, Metric.SUNSHINE.periodValue(5.5, 31), 0.0)
        assertEquals(14.0, Metric.PRECIPITATION.periodValue(2.0, 7), 1e-9)
        assertEquals(0.0, Metric.PRECIPITATION.periodValue(0.0, 31), 0.0)
        // a spread across years scales like the values
        assertEquals(1.5 * 28, Metric.PRECIPITATION.periodValue(1.5, 28), 1e-9)
    }

    @Test
    fun format_temperatureKeepsTwoDecimalsAndTheGluedUnit() {
        assertEquals("12.34°C", Metric.TEMPERATURE.format(12.34))
        assertEquals("-0.50°C", Metric.TEMPERATURE.format(-0.5))
        assertEquals("12.34", Metric.TEMPERATURE.formatNumber(12.34))
    }

    @Test
    fun format_sunshineHasOneDecimal() {
        assertEquals("5.3 h/day", Metric.SUNSHINE.format(5.26))
        assertEquals("0.0 h/day", Metric.SUNSHINE.format(0.0))
    }

    @Test
    fun format_rainHasOneDecimalBelowTenMillimetres() {
        assertEquals("0.0 mm", Metric.PRECIPITATION.format(0.0))
        assertEquals("7.5 mm", Metric.PRECIPITATION.format(7.54))
        assertEquals("9.9 mm", Metric.PRECIPITATION.format(9.94))
        // 9.96 would round to 10.0, which is shown whole
        assertEquals("10 mm", Metric.PRECIPITATION.format(9.96))
        assertEquals("10 mm", Metric.PRECIPITATION.format(10.0))
        assertEquals("42 mm", Metric.PRECIPITATION.format(42.4))
        assertEquals("7.5", Metric.PRECIPITATION.formatNumber(7.54))
    }

    @Test
    fun formatDelta_isSigned() {
        assertEquals("+1.30°C", Metric.TEMPERATURE.formatDelta(1.3))
        assertEquals("-1.30°C", Metric.TEMPERATURE.formatDelta(-1.3))
        assertEquals("+0.8 h/day", Metric.SUNSHINE.formatDelta(0.84))
        assertEquals("-1.6 h/day", Metric.SUNSHINE.formatDelta(-1.6))
        assertEquals("+19 mm", Metric.PRECIPITATION.formatDelta(19.2))
        assertEquals("-7.5 mm", Metric.PRECIPITATION.formatDelta(-7.54))
    }

    @Test
    fun formatDelta_zeroHasASignOnlyIfAsked() {
        assertEquals("+0.00°C", Metric.TEMPERATURE.formatDelta(0.0))
        assertEquals("0.00°C", Metric.TEMPERATURE.formatDelta(0.0, signAtZero = false))
        // the sign of a temperature stays as it always was, even when it rounds to zero
        assertEquals("-0.00°C", Metric.TEMPERATURE.formatDelta(-0.001))
        // sunshine and rain drop the sign of a difference that rounds to zero
        assertEquals("0.0 h/day", Metric.SUNSHINE.formatDelta(-0.04))
        assertEquals("0.0 mm", Metric.PRECIPITATION.formatDelta(0.03))
    }

    @Test
    fun formatPercent_isSignedAndWhole() {
        assertEquals("+85%", Metric.formatPercent(84.8))
        assertEquals("-30%", Metric.formatPercent(-30.2))
        assertEquals("0%", Metric.formatPercent(0.3))
        assertEquals("0%", Metric.formatPercent(-0.3))
        assertEquals("+100%", Metric.formatPercent(100.0))
    }

    @Test
    fun percentOfNormal_existsOnlyForTotalsWithANormal() {
        assertEquals(50.0, Metric.PRECIPITATION.percentOfNormal(10.0, 20.0)!!, 1e-9)
        assertEquals(-25.0, Metric.PRECIPITATION.percentOfNormal(-5.0, 20.0)!!, 1e-9)
        assertNull(Metric.PRECIPITATION.percentOfNormal(10.0, 0.0))
        assertNull(Metric.TEMPERATURE.percentOfNormal(1.0, 10.0))
        assertNull(Metric.SUNSHINE.percentOfNormal(1.0, 5.0))
    }

    @Test
    fun wording_namesTheSidesOfEveryMetric() {
        assertEquals(listOf("warmer", "sunnier", "wetter"), Metric.entries.map { it.wording.above })
        assertEquals(listOf("colder", "duller", "drier"), Metric.entries.map { it.wording.below })
        assertEquals(listOf("cooler", "duller", "drier"), Metric.entries.map { it.wording.belowOutlook })
        assertEquals(listOf("warm", "sunny", "wet"), Metric.entries.map { it.wording.neutral })
        assertEquals(listOf("temperature", "sunshine", "rain"), Metric.entries.map { it.wording.quantity })
    }

    @Test
    fun formatPair_rainSharesOnePrecision() {
        assertEquals("62 vs 33 mm", Metric.PRECIPITATION.formatPair(62.0, 33.0))
        assertEquals("61.0 vs 4.2 mm", Metric.PRECIPITATION.formatPair(61.0, 4.2))
        assertEquals("5.0 vs 33.0 mm", Metric.PRECIPITATION.formatPair(5.0, 33.0))
        // 9.96 rounds to 10, so both are whole
        assertEquals("10 vs 33 mm", Metric.PRECIPITATION.formatPair(9.96, 33.0))
        assertEquals("0.0 vs 0.0 mm", Metric.PRECIPITATION.formatPair(0.0, 0.0))
    }

    @Test
    fun formatPair_otherMetricsFormatEachNumberAsUsual() {
        assertEquals("6.0 vs 5.0 h/day", Metric.SUNSHINE.formatPair(6.0, 5.0))
        assertEquals("12.34 vs 11.00°C", Metric.TEMPERATURE.formatPair(12.34, 11.0))
    }

    @Test
    fun percentOfNormal_needsAtLeastTwoMillimetresOfNormal() {
        assertNull(Metric.PRECIPITATION.percentOfNormal(3.0, 0.3))
        assertNull(Metric.PRECIPITATION.percentOfNormal(3.0, 1.9))
        assertEquals(150.0, Metric.PRECIPITATION.percentOfNormal(3.0, 2.0)!!, 1e-9)
    }
}
