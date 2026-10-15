package com.example.fineweather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailySeriesTest {

    private val start = LocalDate.of(2026, 3, 30)

    private fun fromApi(
        values: List<Double?>,
        divisor: Double = 1.0,
        days: Int = values.size,
    ) = DailySeries.fromApi(
        start,
        start.plusDays(days - 1L),
        (0 until values.size).map { start.plusDays(it.toLong()).toString() },
        values,
        divisor,
    )

    @Test
    fun fromApi_placesValuesByDateFromTheStartDate() {
        val series = fromApi(listOf(1.0, 2.0, 3.0))

        assertEquals(start, series.start)
        assertEquals(LocalDate.of(2026, 4, 1), series.end)
        assertEquals(2.0, series.valueOrNull(LocalDate.of(2026, 3, 31))!!, 0.0)
    }

    @Test
    fun fromApi_keepsNullsAndDaysWithoutAnEntryMissing() {
        val series = fromApi(listOf(1.0, null), days = 4)

        assertEquals(4, series.size)
        assertEquals(1.0, series.valueOrNull(start)!!, 0.0)
        assertNull(series.valueOrNull(start.plusDays(1)))
        assertNull(series.valueOrNull(start.plusDays(3)))
        assertEquals(1, series.count(start, series.end))
    }

    @Test
    fun fromApi_dividesEveryValueAndNeverTouchesNulls() {
        val series = fromApi(listOf(7200.0, null, 3600.0), divisor = Metric.SUNSHINE.apiDivisor)

        assertEquals(2.0, series.valueOrNull(start)!!, 1e-6)
        assertNull(series.valueOrNull(start.plusDays(1)))
        assertEquals(1.0, series.valueOrNull(start.plusDays(2))!!, 1e-6)
    }

    @Test
    fun fromApi_defaultsToNoConversion() {
        assertEquals(0.4, fromApi(listOf(0.4)).valueOrNull(start)!!, 1e-6)
    }

    @Test
    fun scaledValuesSurviveTheBlobRoundTrip() {
        val series = fromApi(listOf(5400.0, 123.0, null), divisor = Metric.SUNSHINE.apiDivisor)

        val restored = DailySeries.fromBytes(start, series.toBytes())

        assertEquals(1.5, restored.valueOrNull(start)!!, 1e-6)
        assertEquals(series.valueOrNull(start.plusDays(1)), restored.valueOrNull(start.plusDays(1)))
        assertNull(restored.valueOrNull(start.plusDays(2)))
    }

    @Test
    fun fromApi_aValueListShorterThanTheTimesLeavesTheRestMissing() {
        val series =
            DailySeries.fromApi(start, start.plusDays(3), (0..3).map { start.plusDays(it.toLong()).toString() }, listOf(1.0, 2.0))

        assertEquals(4, series.size)
        assertEquals(2.0, series.valueOrNull(start.plusDays(1))!!, 0.0)
        assertNull(series.valueOrNull(start.plusDays(2)))
        assertNull(series.valueOrNull(start.plusDays(3)))
    }

    @Test
    fun fromApi_aTimeListShorterThanTheValuesIgnoresTheSurplusValues() {
        val series = DailySeries.fromApi(start, start.plusDays(3), listOf(start.toString()), listOf(1.0, 2.0, 3.0))

        assertEquals(1.0, series.valueOrNull(start)!!, 0.0)
        assertEquals(1, series.count(start, series.end))
    }

    @Test
    fun fromApi_entriesOutsideTheRequestedRangeAreIgnored() {
        val dates = (-2..5).map { start.plusDays(it.toLong()).toString() }

        val series = DailySeries.fromApi(start, start.plusDays(2), dates, dates.indices.map { it.toDouble() })

        // the range, not the response, decides the length: days -2 and -1 and 3..5 are dropped
        assertEquals(3, series.size)
        assertEquals(start.plusDays(2), series.end)
        assertEquals(2.0, series.valueOrNull(start)!!, 0.0)
        assertEquals(4.0, series.valueOrNull(start.plusDays(2))!!, 0.0)
        assertEquals(3, series.count(start, series.end))
    }

    @Test
    fun missing_isASeriesWithoutValues() {
        val series = DailySeries.missing(start, 5)

        assertEquals(5, series.size)
        assertEquals(start.plusDays(4), series.end)
        assertFalse(series.hasValues())
    }

    @Test
    fun hasValues_isFalseOnlyWhenEveryDayIsMissing() {
        assertTrue(fromApi(listOf(null, 0.0)).hasValues())
        assertFalse(fromApi(listOf(null, null)).hasValues())
        assertFalse(fromApi(emptyList(), days = 3).hasValues())
    }
}
