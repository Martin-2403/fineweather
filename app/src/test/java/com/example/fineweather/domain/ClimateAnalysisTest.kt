package com.example.fineweather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ClimateAnalysisTest {

    private val today = LocalDate.of(2026, 10, 14) // last complete day: Oct 13
    private val climate = syntheticReference()

    private fun yearSeries(valueOf: (LocalDate) -> Double?) =
        seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1), valueOf)

    @Test
    fun monthToDate_usesSameDaysOfReference() {
        val series = yearSeries { 20.0 }

        val result = monthToDate(series, climate, today)!!

        assertEquals(20.0, result.observedMean, 1e-6)
        assertEquals(13, result.daysObserved)
        assertEquals(13, result.toDay)
        // like-for-like: Oct 1..13 of the reference, not the whole of October
        assertEquals(10.07, result.referenceMean!!, 1e-4)
    }

    @Test
    fun monthToDate_isNullOnFirstDayOfMonth() {
        val series = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)) { 15.0 }

        assertNull(monthToDate(series, climate, LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun monthToDate_withoutClimateStillReportsObserved() {
        val result = monthToDate(yearSeries { 12.0 }, null, today)!!

        assertEquals(12.0, result.observedMean, 1e-6)
        assertNull(result.referenceMean)
        assertNull(result.referenceStdDev)
    }

    @Test
    fun yearToDate_listsMonthsUpToYesterdayAndFlagsRunningMonth() {
        val result = yearToDate(yearSeries { 10.0 }, climate, today)

        assertEquals((1..10).toList(), result.map { it.month })
        assertTrue(result.last().partial)
        assertEquals(13, result.last().daysObserved)
        assertFalse(result.first().partial)
        assertEquals(31, result.first().daysObserved)
    }

    @Test
    fun yearToDate_finishedMonthsUseFullMonthReference() {
        val result = yearToDate(yearSeries { 10.0 }, climate, today)

        val march = result.first { it.month == 3 }
        assertEquals(climate.monthMean(3)!!, march.referenceMean!!, 1e-9)
        assertEquals(10.0 - climate.monthMean(3)!!, march.delta!!, 1e-9)
    }

    @Test
    fun yearToDate_onFirstOfMonthTreatsPreviousMonthAsComplete() {
        val first = LocalDate.of(2026, 10, 1)
        val series = seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)) { 10.0 }

        val result = yearToDate(series, climate, first)

        assertEquals(9, result.last().month)
        assertFalse(result.last().partial)
    }
}

class MonthViewDataTest {

    private val today = LocalDate.of(2026, 10, 14) // yesterday: Oct 13
    private val climate = syntheticReference { it.monthValue + it.dayOfMonth / 100.0 }

    private fun snapshot(valueOf: (LocalDate) -> Double? = { 20.0 }) =
        ClimateSnapshot(
            series = seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1), valueOf),
            reference = climate,
            today = today,
        )

    @Test
    fun thisMonth_coversTheWholeMonthButObservesOnlyUntilYesterday() {
        val points = dayPoints(snapshot(), MonthWindow.THIS_MONTH)

        assertEquals(31, points.size)
        assertEquals(LocalDate.of(2026, 10, 1), points.first().date)
        assertEquals(13, points.count { it.observed != null })
        assertNull(points.first { it.date == LocalDate.of(2026, 10, 14) }.observed)
        assertTrue(points.all { it.band != null })
    }

    @Test
    fun last30Days_endsYesterdayAndSpansTheMonthBoundary() {
        val points = dayPoints(snapshot(), MonthWindow.LAST_30_DAYS)

        assertEquals(30, points.size)
        assertEquals(LocalDate.of(2026, 10, 13), points.last().date)
        assertEquals(LocalDate.of(2026, 9, 14), points.first().date)
        assertTrue(points.all { it.observed != null })
    }

    @Test
    fun onTheFirstOfAMonth_thisMonthShowsTheMonthJustFinished() {
        val firstOfOctober = LocalDate.of(2026, 10, 1)
        val snapshot =
            ClimateSnapshot(
                seriesOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)) { 15.0 },
                climate,
                firstOfOctober,
            )

        val points = dayPoints(snapshot, MonthWindow.THIS_MONTH)

        assertEquals(30, points.size)
        assertEquals(LocalDate.of(2026, 9, 1), points.first().date)
        assertTrue(points.all { it.observed != null })
    }

    @Test
    fun summary_matchesTheMonthToDateComparison() {
        val snapshot = snapshot()
        val summary = summarize(dayPoints(snapshot, MonthWindow.THIS_MONTH))
        val monthToDate = monthToDate(snapshot.series, climate, today)!!

        assertEquals(monthToDate.observedMean, summary.observedMean!!, 1e-9)
        assertEquals(monthToDate.referenceMean!!, summary.referenceMean!!, 1e-9)
        assertEquals(monthToDate.daysObserved, summary.observedDays)
    }

    @Test
    fun summary_countsWarmerAndColderDaysAgainstTheNormal() {
        // reference ~10.07; first 6 days colder, the other 7 warmer
        val snapshot = snapshot { date -> if (date.dayOfMonth <= 6) 0.0 else 40.0 }

        val summary = summarize(dayPoints(snapshot, MonthWindow.THIS_MONTH))

        assertEquals(6, summary.colderDays)
        assertEquals(7, summary.warmerDays)
    }

    @Test
    fun summary_isEmptyWithoutObservations() {
        val summary = summarize(emptyList())

        assertEquals(0, summary.observedDays)
        assertNull(summary.observedMean)
        assertNull(summary.delta)
    }

    @Test
    fun yearSummary_weightsMonthsByObservedDays() {
        val months =
            listOf(
                MonthlyAnomaly(1, 3.0, 1.0, 1.0, daysObserved = 30, partial = false), // +2 over 30 days
                MonthlyAnomaly(2, 1.0, 2.0, 1.0, daysObserved = 10, partial = true), // -1 over 10 days
                MonthlyAnomaly(3, null, 2.0, 1.0, daysObserved = 0, partial = false), // not comparable
            )

        val summary = summarizeYear(months)

        assertEquals((2.0 * 30 - 1.0 * 10) / 40, summary.delta!!, 1e-9)
        assertEquals(1, summary.warmerMonths)
        assertEquals(2, summary.comparedMonths)
        assertNull(summarizeYear(emptyList()).delta)
    }
}

class ForecastDataTest {

    private val today = LocalDate.of(2026, 10, 14) // yesterday: Oct 13
    private val climate = syntheticReference { it.monthValue + it.dayOfMonth / 100.0 }

    private fun forecast(
        start: LocalDate = today,
        days: Int = 14,
        valueOf: (LocalDate) -> Double? = { 8.0 },
    ) = seriesOf(start, start.plusDays(days - 1L), valueOf)

    private fun snapshot(forecast: DailySeries? = forecast()) =
        ClimateSnapshot(
            series = seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1)) { 12.0 },
            reference = climate,
            today = today,
            forecast = forecast,
        )

    @Test
    fun outlook_averagesTheRequestedDaysAndComparesThemWithTheSameDaysOfTheReference() {
        val outlook = forecastOutlook(forecast(), climate, days = 7)!!

        assertEquals(7, outlook.days)
        assertEquals(8.0, outlook.mean, 1e-6)
        // Oct 14..20 -> 10.14 .. 10.20, mean 10.17
        assertEquals(10.17, outlook.referenceMean!!, 1e-6)
        assertEquals(8.0 - 10.17, outlook.delta!!, 1e-6)
    }

    @Test
    fun outlook_fourteenDaysUsesTheLongerPeriod() {
        val outlook = forecastOutlook(forecast(), climate, days = 14)!!

        assertEquals(14, outlook.days)
        assertEquals(10.205, outlook.referenceMean!!, 1e-6)
    }

    @Test
    fun outlook_spanningTheMonthBoundaryUsesEachDaysOwnMonth() {
        val start = LocalDate.of(2026, 10, 29)
        val outlook = forecastOutlook(forecast(start = start, days = 4), climate, days = 4)!!

        // Oct 29, 30, 31 and Nov 1 -> 10.29 10.30 10.31 11.01
        assertEquals((10.29 + 10.30 + 10.31 + 11.01) / 4, outlook.referenceMean!!, 1e-6)
    }

    @Test
    fun outlook_withoutReferenceStillHasTheMean() {
        val outlook = forecastOutlook(forecast(), reference = null, days = 7)!!

        assertEquals(8.0, outlook.mean, 1e-6)
        assertNull(outlook.referenceMean)
        assertNull(outlook.delta)
    }

    @Test
    fun outlook_isNullWithoutForecastOrDays() {
        assertNull(forecastOutlook(null, climate, 7))
        assertNull(forecastOutlook(forecast(), climate, 0))
        assertNull(forecastOutlook(forecast(valueOf = { null }), climate, 7))
    }

    @Test
    fun thisMonth_attachesTheForecastFromTodayOnAndClipsAtTheMonthEnd() {
        val points = dayPoints(snapshot(), MonthWindow.THIS_MONTH, forecastDays = 7)

        assertEquals(31, points.size)
        assertNull(points.first { it.date == LocalDate.of(2026, 10, 13) }.forecast)
        assertEquals(8.0, points.first { it.date == LocalDate.of(2026, 10, 14) }.forecast!!, 1e-6)
        assertEquals(8.0, points.first { it.date == LocalDate.of(2026, 10, 20) }.forecast!!, 1e-6)
        assertNull(points.first { it.date == LocalDate.of(2026, 10, 21) }.forecast)
        assertEquals(13, points.count { it.observed != null })
    }

    @Test
    fun thisMonth_forecastDaysSettingLimitsTheForecastDrawn() {
        val short = dayPoints(snapshot(), MonthWindow.THIS_MONTH, forecastDays = 7).count { it.forecast != null }
        val long = dayPoints(snapshot(), MonthWindow.THIS_MONTH, forecastDays = 14).count { it.forecast != null }

        assertEquals(7, short)
        assertEquals(14, long)
    }

    @Test
    fun last30Days_isExtendedByTheForecastDays() {
        val points = dayPoints(snapshot(), MonthWindow.LAST_30_DAYS, forecastDays = 7)

        assertEquals(37, points.size)
        assertEquals(LocalDate.of(2026, 10, 20), points.last().date)
        assertEquals(30, points.count { it.observed != null })
        assertEquals(7, points.count { it.forecast != null })
        assertTrue(points.all { it.band != null })
    }

    @Test
    fun withoutForecast_nothingIsAdded() {
        val points = dayPoints(snapshot(forecast = null), MonthWindow.LAST_30_DAYS, forecastDays = 7)

        assertEquals(30, points.size)
        assertTrue(points.none { it.forecast != null })
    }

    @Test
    fun forecastDoesNotChangeTheObservedSummary() {
        val withForecast = summarize(dayPoints(snapshot(), MonthWindow.THIS_MONTH, forecastDays = 14))
        val without = summarize(dayPoints(snapshot(forecast = null), MonthWindow.THIS_MONTH))

        assertEquals(without, withForecast)
    }
}
