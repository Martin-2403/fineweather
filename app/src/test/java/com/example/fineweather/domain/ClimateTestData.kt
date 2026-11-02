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

/** A [MetricSeries] of [temperature] plus sunshine (5 h) and rain (1 mm) on every day. */
fun metricSeriesOf(temperature: DailySeries): MetricSeries =
    MetricSeries(
        mapOf(
            Metric.TEMPERATURE to temperature,
            Metric.SUNSHINE to seriesOf(temperature.start, temperature.end) { 5.0 },
            Metric.PRECIPITATION to seriesOf(temperature.start, temperature.end) { 1.0 },
        ),
    )

/** Like [seriesOf], as a [MetricSeries]. */
fun metricSeriesOf(
    start: LocalDate,
    end: LocalDate,
    valueOf: (LocalDate) -> Double?,
): MetricSeries = metricSeriesOf(seriesOf(start, end, valueOf))
