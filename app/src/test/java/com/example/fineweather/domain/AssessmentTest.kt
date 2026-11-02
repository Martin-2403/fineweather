package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssessmentTest {

    private val absolute = TemperatureComparisonMode.ABSOLUTE_DELTA
    private val anomaly = TemperatureComparisonMode.NORMALIZED_ANOMALY

    @Test
    fun absolute_severityFollowsDegreeBands() {
        assertEquals(0, assess(10.9, 10.0, null, absolute).severity)
        assertEquals(1, assess(11.0, 10.0, null, absolute).severity)
        assertEquals(2, assess(12.5, 10.0, null, absolute).severity)
        assertEquals(3, assess(13.0, 10.0, null, absolute).severity)
        assertEquals(3, assess(5.0, 10.0, null, absolute).severity)
    }

    @Test
    fun normalized_severityFollowsStandardDeviationBands() {
        assertEquals(0, assess(10.9, 10.0, 2.0, anomaly).severity)
        assertEquals(1, assess(11.0, 10.0, 2.0, anomaly).severity)
        assertEquals(2, assess(13.0, 10.0, 2.0, anomaly).severity)
        assertEquals(3, assess(15.0, 10.0, 2.0, anomaly).severity)
    }

    @Test
    fun normalized_fallsBackToAbsoluteWhenStdDevMissingOrZero() {
        assertFalse(assess(12.0, 10.0, null, anomaly).normalized)
        assertFalse(assess(12.0, 10.0, 0.0, anomaly).normalized)
        assertEquals(2.0, assess(12.0, 10.0, 0.0, anomaly).score, 1e-9)
        assertTrue(assess(12.0, 10.0, 1.0, anomaly).normalized)
        assertEquals(2.0, assess(12.0, 10.0, 1.0, anomaly).score, 1e-9)
    }

    @Test
    fun absoluteMode_ignoresStdDev() {
        assertFalse(assess(12.0, 10.0, 1.0, absolute).normalized)
    }

    @Test
    fun band_usesNeutralThresholdExclusively() {
        assertEquals(Band.NEUTRAL, assess(11.0, 10.0, null, absolute).band)
        assertEquals(Band.WARM, assess(11.01, 10.0, null, absolute).band)
        assertEquals(Band.COLD, assess(8.99, 10.0, null, absolute).band)
        assertEquals(Band.NEUTRAL, assess(10.5, 10.0, 1.0, anomaly).band)
        assertEquals(Band.WARM, assess(10.6, 10.0, 1.0, anomaly).band)
    }
}
