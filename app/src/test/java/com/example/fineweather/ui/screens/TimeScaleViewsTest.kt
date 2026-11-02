package com.example.fineweather.ui.screens

import com.example.fineweather.domain.MonthWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeScaleViewsTest {

    private fun days(
        first: LocalDate,
        count: Int,
    ) = (0 until count).map { first.plusDays(it.toLong()) }

    @Test
    fun dayAxisLabels_thisMonthLabelsTheFirstAndEveryFifthDay() {
        val october = days(LocalDate.of(2026, 10, 1), 31)

        assertEquals(
            listOf(0 to "1", 4 to "5", 9 to "10", 14 to "15", 19 to "20", 24 to "25"),
            dayAxisLabels(october, MonthWindow.THIS_MONTH),
        )
    }

    @Test
    fun dayAxisLabels_thisMonthLeavesOutALabelInTheLastTwoDays() {
        // the 30th of a 31-day month is the second last day, and the 30th of a 30-day month the last
        assertTrue(dayAxisLabels(days(LocalDate.of(2026, 10, 1), 31), MonthWindow.THIS_MONTH).none { it.second == "30" })
        assertTrue(dayAxisLabels(days(LocalDate.of(2026, 9, 1), 30), MonthWindow.THIS_MONTH).none { it.second == "30" })
        // the 25th of February is index 24 of 28 days and stays
        assertEquals("25", dayAxisLabels(days(LocalDate.of(2026, 2, 1), 28), MonthWindow.THIS_MONTH).last().second)
    }

    @Test
    fun dayAxisLabels_thisMonthWithoutDaysHasNoLabels() {
        assertTrue(dayAxisLabels(emptyList(), MonthWindow.THIS_MONTH).isEmpty())
        assertTrue(dayAxisLabels(emptyList(), MonthWindow.LAST_30_DAYS).isEmpty())
    }

    @Test
    fun dayAxisLabels_last30DaysLabelsWeeklyAndTheLastObservedDay() {
        val dates = days(LocalDate.of(2026, 9, 14), 30)

        assertEquals(
            listOf(0 to "Sep 14", 7 to "Sep 21", 14 to "Sep 28", 21 to "Oct 5", 29 to "Oct 13"),
            dayAxisLabels(dates, MonthWindow.LAST_30_DAYS),
        )
    }

    @Test
    fun dayAxisLabels_last30DaysAddsTheLastForecastDayFromFiveDaysAhead() {
        val withSeven = dayAxisLabels(days(LocalDate.of(2026, 9, 14), 37), MonthWindow.LAST_30_DAYS)
        val withFourteen = dayAxisLabels(days(LocalDate.of(2026, 9, 14), 44), MonthWindow.LAST_30_DAYS)

        assertEquals(listOf(0, 7, 14, 21, 29, 36), withSeven.map { it.first })
        assertEquals("Oct 20", withSeven.last().second)
        assertEquals(listOf(0, 7, 14, 21, 29, 43), withFourteen.map { it.first })
        assertEquals("Oct 27", withFourteen.last().second)
    }

    @Test
    fun dayAxisLabels_last30DaysAddsNoExtraLabelCloseToTheLastObservedOne() {
        assertEquals(5, dayAxisLabels(days(LocalDate.of(2026, 9, 14), 34), MonthWindow.LAST_30_DAYS).size)
        assertEquals(6, dayAxisLabels(days(LocalDate.of(2026, 9, 14), 35), MonthWindow.LAST_30_DAYS).size)
    }

    @Test
    fun dayAxisLabels_last30DaysOfAShortListOnlyLabelsWhatExists() {
        val labels = dayAxisLabels(days(LocalDate.of(2026, 9, 14), 10), MonthWindow.LAST_30_DAYS)

        assertEquals(listOf(0 to "Sep 14", 7 to "Sep 21"), labels)
    }

    @Test
    fun emptyText_isErrorWhenLoadingFailedAndTheHintOtherwise() {
        assertEquals("Error", emptyText(failed = true, prompt = "Search for a place."))
        assertEquals("Search for a place.", emptyText(failed = false, prompt = "Search for a place."))
    }
}
