package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricAssessmentTest {

    private val absolute = TemperatureComparisonMode.ABSOLUTE_DELTA
    private val anomaly = TemperatureComparisonMode.NORMALIZED_ANOMALY

    private fun sun(
        observed: Double,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = absolute,
    ) = assess(Metric.SUNSHINE, observed, 5.0, stdDev, mode)

    /** Against a normal of 40 mm, a difference of 10 mm is 25 %. */
    private fun rain(
        observed: Double,
        reference: Double = 40.0,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = absolute,
    ) = assess(Metric.PRECIPITATION, observed, reference, stdDev, mode)

    @Test
    fun temperatureOverloadIsTheTemperatureAssessment() {
        val old = assess(12.0, 10.0, 1.0, anomaly)
        val new = assess(Metric.TEMPERATURE, 12.0, 10.0, 1.0, anomaly)

        assertEquals(new, old)
        assertEquals(Metric.TEMPERATURE, old.metric)
        assertNull(old.percent)
        assertFalse(old.lowNormal)
    }

    @Test
    fun scales_liveInOnePlace() {
        val temperature = AssessmentScales.absolute(Metric.TEMPERATURE)
        assertEquals(listOf(1.0, 2.0, 3.0), listOf(temperature.neutral, temperature.slight, temperature.clear))
        val sunshine = AssessmentScales.absolute(Metric.SUNSHINE)
        assertEquals(listOf(0.5, 1.0, 1.5), listOf(sunshine.neutral, sunshine.slight, sunshine.clear))
        val rain = AssessmentScales.absolute(Metric.PRECIPITATION)
        assertEquals(listOf(25.0, 50.0, 75.0), listOf(rain.neutral, rain.slight, rain.clear))
        val z = AssessmentScales.NORMALIZED
        assertEquals(listOf(0.5, 1.5, 2.5), listOf(z.neutral, z.slight, z.clear))
        assertEquals(2.0, AssessmentScales.MIN_NORMAL_RAIN_MM, 0.0)
    }

    // ---- sunshine, absolute ----

    @Test
    fun sunshine_severityFollowsHourBands() {
        assertEquals(0, sun(5.49).severity)
        assertEquals(1, sun(5.5).severity)
        assertEquals(1, sun(5.99).severity)
        assertEquals(2, sun(6.0).severity)
        assertEquals(2, sun(6.49).severity)
        assertEquals(3, sun(6.5).severity)
        assertEquals(3, sun(9.0).severity)
    }

    @Test
    fun sunshine_isSymmetricBelowTheNormal() {
        assertEquals(0, sun(4.51).severity)
        assertEquals(1, sun(4.5).severity)
        assertEquals(2, sun(4.0).severity)
        assertEquals(3, sun(3.5).severity)
        assertEquals(Band.COLD, sun(4.0).band)
    }

    @Test
    fun sunshine_bandTurnsAfterTheNeutralThreshold() {
        // like temperature: at the threshold the class changes, the band only just above it
        assertEquals(Band.NEUTRAL, sun(5.49).band)
        assertEquals(Band.NEUTRAL, sun(5.5).band)
        assertEquals(Band.WARM, sun(5.51).band)
        assertEquals(Band.COLD, sun(4.49).band)
    }

    @Test
    fun sunshine_absoluteModeIgnoresTheStdDev() {
        val assessment = sun(6.0, stdDev = 0.1)

        assertFalse(assessment.normalized)
        assertEquals(1.0, assessment.score, 1e-9)
        assertEquals(0.5, assessment.neutralThreshold, 0.0)
    }

    // ---- rain, absolute ----

    @Test
    fun rain_severityFollowsPercentOfTheNormal() {
        assertEquals(0, rain(49.6).severity) // +24 %
        assertEquals(1, rain(50.0).severity) // +25 %
        assertEquals(1, rain(59.0).severity) // +47.5 %
        assertEquals(2, rain(60.0).severity) // +50 %
        assertEquals(2, rain(69.0).severity) // +72.5 %
        assertEquals(3, rain(70.0).severity) // +75 %
        assertEquals(3, rain(400.0).severity)
    }

    @Test
    fun rain_isSymmetricBelowTheNormal() {
        assertEquals(0, rain(30.4).severity) // -24 %
        assertEquals(1, rain(30.0).severity)
        assertEquals(2, rain(20.0).severity)
        assertEquals(3, rain(10.0).severity)
        assertEquals(3, rain(0.0).severity) // -100 %
        assertEquals(Band.COLD, rain(20.0).band)
        assertEquals(Band.WARM, rain(60.0).band)
    }

    @Test
    fun rain_bandTurnsAfterTheNeutralThreshold() {
        assertEquals(Band.NEUTRAL, rain(50.0).band)
        assertEquals(Band.WARM, rain(50.4).band)
        assertEquals(Band.NEUTRAL, rain(30.0).band)
        assertEquals(Band.COLD, rain(29.6).band)
    }

    @Test
    fun rain_scoreAndPercentAreThePercentOfTheNormal() {
        val assessment = rain(62.0, reference = 33.0)

        assertEquals(29.0, assessment.delta, 1e-9)
        assertEquals(29.0 / 33.0 * 100, assessment.percent!!, 1e-9)
        assertEquals(assessment.percent!!, assessment.score, 1e-9)
        assertEquals(25.0, assessment.neutralThreshold, 0.0)
    }

    @Test
    fun rain_aNormalBelowTwoMillimetresIsAlwaysNeutral() {
        val dry = rain(10.0, reference = 1.9)

        assertTrue(dry.lowNormal)
        assertEquals(Band.NEUTRAL, dry.band)
        assertEquals(0, dry.severity)
        assertNull(dry.percent)
        assertEquals(8.1, dry.delta, 1e-9)
    }

    @Test
    fun rain_aNormalOfTwoMillimetresIsJudgedAgain() {
        val assessment = rain(5.0, reference = 2.0)

        assertFalse(assessment.lowNormal)
        assertEquals(150.0, assessment.percent!!, 1e-9)
        assertEquals(3, assessment.severity)
        assertEquals(Band.WARM, assessment.band)
    }

    @Test
    fun rain_withoutANormalIsNeutral() {
        val assessment = Assessment(delta = 12.0, stdDev = null, mode = absolute, metric = Metric.PRECIPITATION)

        assertTrue(assessment.lowNormal)
        assertEquals(Band.NEUTRAL, assessment.band)
        assertEquals(0, assessment.severity)
        assertNull(assessment.percent)
    }

    // ---- normalized anomaly ----

    @Test
    fun sunshine_normalizedUsesStandardDeviations() {
        assertEquals(0, sun(5.4, stdDev = 1.0, mode = anomaly).severity)
        assertEquals(1, sun(5.5, stdDev = 1.0, mode = anomaly).severity)
        assertEquals(2, sun(6.5, stdDev = 1.0, mode = anomaly).severity)
        assertEquals(3, sun(7.5, stdDev = 1.0, mode = anomaly).severity)
        assertEquals(3, sun(2.0, stdDev = 1.0, mode = anomaly).severity)
        assertTrue(sun(6.0, stdDev = 1.0, mode = anomaly).normalized)
        assertEquals(Band.NEUTRAL, sun(5.5, stdDev = 1.0, mode = anomaly).band)
        assertEquals(Band.WARM, sun(5.6, stdDev = 1.0, mode = anomaly).band)
    }

    @Test
    fun rain_normalizedDividesTheTotalByTheSpreadOfTotals() {
        // 62 vs 33 mm is +29 mm; a spread of 20 mm of totals makes 1.45, one of 10 mm makes 2.9
        val moderate = rain(62.0, reference = 33.0, stdDev = 20.0, mode = anomaly)
        val unusual = rain(62.0, reference = 33.0, stdDev = 10.0, mode = anomaly)

        assertTrue(moderate.normalized)
        assertEquals(1.45, moderate.score, 1e-9)
        assertEquals(1, moderate.severity)
        assertEquals(2.9, unusual.score, 1e-9)
        assertEquals(3, unusual.severity)
        assertEquals(Band.WARM, unusual.band)
        // the percent stays available for the text
        assertEquals(29.0 / 33.0 * 100, unusual.percent!!, 1e-9)
    }

    @Test
    fun normalized_fallsBackToTheAbsoluteScaleWithoutAUsableStdDev() {
        listOf(null, 0.0).forEach { sigma ->
            val sunshine = sun(6.0, stdDev = sigma, mode = anomaly)
            assertFalse(sunshine.normalized)
            assertEquals(1.0, sunshine.score, 1e-9)
            assertEquals(2, sunshine.severity)

            val rainy = rain(60.0, stdDev = sigma, mode = anomaly)
            assertFalse(rainy.normalized)
            assertEquals(50.0, rainy.score, 1e-9)
            assertEquals(2, rainy.severity)
        }
    }

    @Test
    fun rain_normalizedStaysNeutralForATinyNormal() {
        val assessment = rain(10.0, reference = 1.0, stdDev = 0.5, mode = anomaly)

        assertEquals(Band.NEUTRAL, assessment.band)
        assertEquals(0, assessment.severity)
        assertNull(assessment.percent)
    }

    @Test
    fun rain_classBoundariesHoldOnNormalsThatAreNotBinaryFractions() {
        for (reference in listOf(3.3, 7.7, 14.4, 33.3, 61.9)) {
            fun at(percent: Double) = rain(reference * (1 + percent / 100), reference)

            assertEquals(0, at(24.9).severity)
            assertEquals(1, at(25.1).severity)
            assertEquals(1, at(49.9).severity)
            assertEquals(2, at(50.1).severity)
            assertEquals(2, at(74.9).severity)
            assertEquals(3, at(75.1).severity)
            assertEquals(Band.NEUTRAL, at(24.9).band)
            assertEquals(Band.WARM, at(25.1).band)
            assertEquals(Band.COLD, at(-25.1).band)
            assertEquals(1, at(-25.1).severity)
            assertEquals(3, at(-75.1).severity)
        }
    }

    @Test
    fun rain_percentGuardIsTheSameForTheAssessmentAndTheMetric() {
        listOf(0.3, 1.9, 2.0, 2.1).forEach { reference ->
            val assessment = rain(reference + 3.0, reference)

            assertEquals(Metric.PRECIPITATION.percentOfNormal(assessment.delta, reference), assessment.percent)
            assertEquals(assessment.percent == null, assessment.lowNormal)
        }
    }
}
