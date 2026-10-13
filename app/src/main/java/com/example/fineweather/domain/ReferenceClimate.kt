package com.example.fineweather.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.sqrt

data class DailyStats(
    val mean: Double,
    val stdDev: Double,
)

/**
 * Climate normals for one place from [firstYear]..[lastYear] of daily means. Every comparison is
 * computed for the same calendar days, so a partial month is never compared with a full month.
 */
class ReferenceClimate(
    val series: DailySeries,
    val firstYear: Int,
    val lastYear: Int,
) {
    fun monthMean(month: Int): Double? = windowMean(month, 1, 31)

    fun monthStdDev(month: Int): Double? = windowStdDev(month, 1, 31)

    /** Mean over the years of the mean of days [fromDay]..[toDay] of [month]. */
    fun windowMean(
        month: Int,
        fromDay: Int,
        toDay: Int,
    ): Double? = yearlyWindowMeans(month, fromDay, toDay).takeIf { it.isNotEmpty() }?.average()

    /** Sample standard deviation across years of the mean of days [fromDay]..[toDay] of [month]. */
    fun windowStdDev(
        month: Int,
        fromDay: Int,
        toDay: Int,
    ): Double? = sampleStdDev(yearlyWindowMeans(month, fromDay, toDay))

    /** Mean and spread of all daily values within ±[halfWindow] days of the calendar date, across years. */
    fun dailyStats(
        month: Int,
        day: Int,
        halfWindow: Int = 3,
    ): DailyStats? {
        val pooled = mutableListOf<Double>()
        for (year in firstYear..lastYear) {
            val center = calendarDate(year, month, day)
            series.days(center.minusDays(halfWindow.toLong()), center.plusDays(halfWindow.toLong()))
                .forEach { pooled += it.second }
        }
        if (pooled.isEmpty()) return null
        return DailyStats(pooled.average(), sampleStdDev(pooled) ?: 0.0)
    }

    private fun yearlyWindowMeans(
        month: Int,
        fromDay: Int,
        toDay: Int,
    ): List<Double> =
        (firstYear..lastYear).mapNotNull { year ->
            val yearMonth = YearMonth.of(year, month)
            val last = yearMonth.lengthOfMonth()
            series.mean(yearMonth.atDay(minOf(fromDay, last)), yearMonth.atDay(minOf(toDay, last)))
        }

    private fun calendarDate(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate = YearMonth.of(year, month).let { it.atDay(minOf(day, it.lengthOfMonth())) }

    private fun sampleStdDev(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }
}
