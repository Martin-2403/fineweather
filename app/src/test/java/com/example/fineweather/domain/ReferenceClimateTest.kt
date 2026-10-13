package com.example.fineweather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReferenceClimateTest {

    @Test
    fun windowMean_comparesOnlyTheRequestedDays() {
        val climate = syntheticReference()

        // October 1..13: values 10.01 .. 10.13, mean 10.07; full October mean is 10.16
        assertEquals(10.07, climate.windowMean(10, 1, 13)!!, 1e-4)
        assertEquals(10.16, climate.monthMean(10)!!, 1e-4)
    }

    @Test
    fun windowMean_isTheMeanOfYearlyMeans() {
        val climate =
            syntheticReference { date -> if (date.year % 2 == 0) 10.0 else 20.0 }

        assertEquals(15.0, climate.monthMean(6)!!, 1e-4)
    }

    @Test
    fun windowStdDev_isSampleStdDevAcrossYears() {
        // values alternate 10/20 over 30 years -> 15 each: mean 15, sd = 5.0855
        val climate = syntheticReference { date -> if (date.year % 2 == 0) 10.0 else 20.0 }

        assertEquals(5.0855, climate.windowStdDev(6, 1, 30)!!, 1e-3)
    }

    @Test
    fun windowStdDev_isNullWithFewerThanTwoYears() {
        val climate = syntheticReference(firstYear = 1990, lastYear = 1990)

        assertNull(climate.windowStdDev(5, 1, 10))
        assertNotNull(climate.windowMean(5, 1, 10))
    }

    @Test
    fun windowEnd_isClampedToMonthLength() {
        val climate = syntheticReference()

        assertEquals(climate.monthMean(2)!!, climate.windowMean(2, 1, 31)!!, 1e-9)
    }

    @Test
    fun missingDays_areSkipped() {
        val climate =
            syntheticReference { date -> if (date.dayOfMonth <= 10) null else 5.0 }

        assertEquals(5.0, climate.windowMean(3, 1, 31)!!, 1e-4)
        assertNull(climate.windowMean(3, 1, 10))
    }

    @Test
    fun dailyStats_poolsAroundTheCalendarDate() {
        val climate = syntheticReference { date -> date.dayOfMonth.toDouble() }

        val stats = climate.dailyStats(6, 15, halfWindow = 3)!!

        assertEquals(15.0, stats.mean, 1e-4)
        assertTrue(stats.stdDev > 0.0)
    }

    @Test
    fun dailyStats_handlesYearBoundaryAndLeapDay() {
        val climate = syntheticReference()

        assertNotNull(climate.dailyStats(1, 1, 3))
        assertNotNull(climate.dailyStats(12, 31, 3))
        assertNotNull(climate.dailyStats(2, 29, 3))
    }

    @Test
    fun series_roundTripsThroughBytes() {
        val series = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)) { it.dayOfMonth * 1.1 }
        val restored = DailySeries.fromBytes(series.start, series.toBytes())

        assertEquals(series.size, restored.size)
        assertEquals(series.valueOrNull(LocalDate.of(2026, 1, 3)), restored.valueOrNull(LocalDate.of(2026, 1, 3)))
        assertEquals(
            series.mean(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
            restored.mean(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
        )
    }

    @Test
    fun series_ignoresNullsAndOutOfRangeDates() {
        val series =
            DailySeries.fromApi(
                start = LocalDate.of(2026, 1, 1),
                end = LocalDate.of(2026, 1, 3),
                times = listOf("2025-12-31", "2026-01-01", "2026-01-02", "2026-01-03", "bad"),
                temperatures = listOf(1.0, 2.0, null, 4.0, 5.0),
            )

        assertNull(series.valueOrNull(LocalDate.of(2026, 1, 2)))
        assertEquals(3.0, series.mean(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))!!, 1e-6)
        assertEquals(2, series.count(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1)))
    }
}
