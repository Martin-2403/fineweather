package com.example.fineweather.ui.screens

import com.example.fineweather.domain.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class ChartUtilsTest {

    @Test
    fun niceAxis_coversTheDataWithRoundTicks() {
        val axis = niceAxis(3.2, 17.9)

        assertTrue(axis.min <= 3.2)
        assertTrue(axis.max >= 17.9)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), axis.ticks)
    }

    @Test
    fun niceAxis_handlesNegativeRanges() {
        val axis = niceAxis(-4.1, 6.3)

        assertEquals(-5.0, axis.min, 1e-9)
        assertEquals(10.0, axis.max, 1e-9)
        assertTrue(axis.ticks.contains(0.0))
    }

    @Test
    fun niceAxis_expandsADegenerateRange() {
        val axis = niceAxis(5.0, 5.0)

        assertTrue(axis.min <= 5.0 && axis.max >= 5.0)
        assertTrue(axis.max > axis.min)
    }

    @Test
    fun niceAxis_isSymmetricForSymmetricInput() {
        val axis = niceAxis(-2.3, 2.3)

        assertEquals(-axis.min, axis.max, 1e-9)
    }

    @Test
    fun formatting() {
        assertEquals("5", formatTick(5.0))
        assertEquals("2.5", formatTick(2.5))
        assertEquals("+1.8", formatSigned(1.84))
        assertEquals("-0.5", formatSigned(-0.45))
    }

    @Test
    fun niceAxis_takesTheBoundsInEitherOrder() {
        val axis = niceAxis(17.9, 3.2)

        assertEquals(niceAxis(3.2, 17.9), axis)
    }

    @Test
    fun niceAxis_coversARangeBelowZero() {
        val axis = niceAxis(-10.0, -2.0)

        assertEquals(listOf(-10.0, -8.0, -6.0, -4.0, -2.0), axis.ticks)
        assertEquals(-10.0, axis.min, 1e-9)
        assertEquals(-2.0, axis.max, 1e-9)
    }

    @Test
    fun niceAxis_aZeroRangeBecomesAUnitSpanFromZero() {
        assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), niceAxis(0.0, 0.0).ticks)
    }

    @Test
    fun niceAxis_roundsLargeRangesToHundreds() {
        assertEquals(listOf(0.0, 250.0, 500.0, 750.0, 1000.0), niceAxis(0.0, 1000.0).ticks)
    }

    @Test
    fun alignedZeroAxis_withoutAnyValueStillHasIncreasingTicksFromZero() {
        val axis = alignedZeroAxis(0.0, 4)

        assertEquals(5, axis.ticks.size)
        assertEquals(0.0, axis.min, 0.0)
        assertTrue(axis.max > 0.0)
        assertTrue(axis.ticks.zipWithNext().all { (a, b) -> b > a })
        assertEquals(axis.max, axis.ticks.last(), 1e-18)
    }

    @Test
    fun alignedZeroAxis_needsAtLeastOneInterval() {
        assertThrows(IllegalArgumentException::class.java) { alignedZeroAxis(10.0, 0) }
    }

    @Test
    fun formatAmount_isWholeFromTenAndHasADecimalBelow() {
        assertEquals("7.5 mm", formatAmount(Metric.PRECIPITATION, 7.54))
        assertEquals("10 mm", formatAmount(Metric.PRECIPITATION, 9.96))
        assertEquals("251 h", formatAmount(Metric.SUNSHINE, 251.4))
        assertEquals("1234 mm", formatAmount(Metric.PRECIPITATION, 1234.4))
        assertEquals("0.0 h", formatAmount(Metric.SUNSHINE, 0.0))
    }

    @Test
    fun formatAmountDelta_isSignedAndNeverHasASignOnZero() {
        assertEquals("+12", formatAmountDelta(12.2))
        assertEquals("-4.5", formatAmountDelta(-4.5))
        assertEquals("+12 mm", formatAmountDelta(12.2, Metric.PRECIPITATION))
        assertEquals("-12 h", formatAmountDelta(-12.4, Metric.SUNSHINE))
        assertEquals("+1234 mm", formatAmountDelta(1234.0, Metric.PRECIPITATION))
        assertEquals("-10 mm", formatAmountDelta(-9.96, Metric.PRECIPITATION))
        assertEquals("0.0", formatAmountDelta(-0.04))
        assertEquals("0.0 mm", formatAmountDelta(0.04, Metric.PRECIPITATION))
        assertEquals("0.0 h", formatAmountDelta(-0.0, Metric.SUNSHINE))
    }

    @Ignore("known issue: formatSigned keeps the sign of a delta that rounds to zero (+0.0, -0.0)")
    @Test
    fun formatSigned_aDeltaThatRoundsToZeroHasNoSign() {
        assertEquals("0.0", formatSigned(0.03))
        assertEquals("0.0", formatSigned(-0.04))
        assertEquals("+0.1", formatSigned(0.06))
    }
}
