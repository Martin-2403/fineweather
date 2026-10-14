package com.example.fineweather.domain

import java.time.LocalDate

/** Everything the Current, Month and Year views are computed from, for one place and day. */
data class ClimateSnapshot(
    val series: DailySeries,
    val reference: ReferenceClimate?,
    val today: LocalDate,
    /** Daily forecast starting today (up to 14 days), if it could be loaded. */
    val forecast: DailySeries? = null,
)
