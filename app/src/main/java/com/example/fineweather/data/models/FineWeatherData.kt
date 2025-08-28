package com.example.fineweather.data.models

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.fineweather.utils.formatDouble

class FineWeatherData(
    val location: String,
    val coordinates: Pair<Double, Double>
) {
    val timestamp: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    var currentMonthAverage: Double? = null
    var currentAverage: Double? = null
    var forecastAverage: Double? = null
    var historicMonthlyAverage: Double? = null
    var historicTimePeriodAverage: Double? = null

    var currentTimePeriod: Int = 31
    var forecastTimePeriod: Int = 14
    var historicTimePeriod: Int = 30

    val tempDifference: Double?
        get() = if (currentMonthAverage == null || historicMonthlyAverage == null) null else currentMonthAverage!! - historicMonthlyAverage!!

}
