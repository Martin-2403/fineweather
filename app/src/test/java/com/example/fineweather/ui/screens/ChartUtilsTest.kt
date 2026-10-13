package com.example.fineweather.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
