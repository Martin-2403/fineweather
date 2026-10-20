package com.example.fineweather.domain

import java.time.LocalDate

/** Everything the Month and Year views are computed from, for one place, day and [metric]. */
data class ClimateSnapshot(
    val series: DailySeries,
    val reference: ReferenceClimate?,
    val today: LocalDate,
    /** Daily forecast starting today (up to 14 days), if it could be loaded. */
    val forecast: DailySeries? = null,
    /** What [series], [reference] and [forecast] measure. */
    val metric: Metric = Metric.TEMPERATURE,
)
