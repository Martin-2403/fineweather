package com.example.fineweather.domain

import java.time.LocalDate

/** Builds a series where each day's value is produced by [valueOf]. */
fun seriesOf(
    start: LocalDate,
    end: LocalDate,
    valueOf: (LocalDate) -> Double?,
): DailySeries {
    val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    return DailySeries.fromApi(start, end, dates.map { it.toString() }, dates.map(valueOf))
}

/** 30 reference years where every day of month m has the value m + day/100, in all years. */
fun syntheticReference(
    firstYear: Int = 1961,
    lastYear: Int = 1990,
    valueOf: (LocalDate) -> Double? = { it.monthValue + it.dayOfMonth / 100.0 },
): ReferenceClimate =
    ReferenceClimate(
        series = seriesOf(LocalDate.of(firstYear, 1, 1), LocalDate.of(lastYear, 12, 31), valueOf),
        firstYear = firstYear,
        lastYear = lastYear,
    )
