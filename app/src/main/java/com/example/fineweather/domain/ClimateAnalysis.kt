package com.example.fineweather.domain

import java.time.LocalDate
import java.time.YearMonth

/** An observed period compared with the same calendar days of the reference years. */
data class PeriodComparison(
    val observedMean: Double,
    val referenceMean: Double?,
    val referenceStdDev: Double?,
    val daysObserved: Int,
    val fromDay: Int,
    val toDay: Int,
)

/**
 * Month-to-date up to yesterday (the last complete day), compared with the same days of the
 * reference. Null on the first day of a month, when no day of the month is complete yet.
 */
fun monthToDate(
    series: DailySeries,
    climate: ReferenceClimate?,
    today: LocalDate,
): PeriodComparison? {
    if (today.dayOfMonth == 1) return null
    val yesterday = today.minusDays(1)
    val first = yesterday.withDayOfMonth(1)
    val observed = series.mean(first, yesterday) ?: return null
    val toDay = yesterday.dayOfMonth
    return PeriodComparison(
        observedMean = observed,
        referenceMean = climate?.windowMean(yesterday.monthValue, 1, toDay),
        referenceStdDev = climate?.windowStdDev(yesterday.monthValue, 1, toDay),
        daysObserved = series.count(first, yesterday),
        fromDay = 1,
        toDay = toDay,
    )
}

/** Mean of the last [days] complete days ending yesterday. */
fun lastDaysMean(
    series: DailySeries,
    today: LocalDate,
    days: Int = 31,
): Double? {
    val yesterday = today.minusDays(1)
    return series.mean(yesterday.minusDays(days - 1L), yesterday)
}

data class MonthlyAnomaly(
    val month: Int,
    val observedMean: Double?,
    val referenceMean: Double?,
    val referenceStdDev: Double?,
    val daysObserved: Int,
    /** True while the month is still running, i.e. only the first days are compared. */
    val partial: Boolean,
) {
    val delta: Double? get() = if (observedMean != null && referenceMean != null) observedMean - referenceMean else null
}

/**
 * January up to the month of yesterday. Finished months use the full-month reference, the
 * running month only the same days of the reference.
 */
fun yearToDate(
    series: DailySeries,
    climate: ReferenceClimate?,
    today: LocalDate,
): List<MonthlyAnomaly> {
    val yesterday = today.minusDays(1)
    val year = yesterday.year
    return (1..yesterday.monthValue).map { month ->
        val yearMonth = YearMonth.of(year, month)
        val running = month == yesterday.monthValue && yesterday.dayOfMonth < yearMonth.lengthOfMonth()
        val toDay = if (running) yesterday.dayOfMonth else yearMonth.lengthOfMonth()
        val to = yearMonth.atDay(toDay)
        MonthlyAnomaly(
            month = month,
            observedMean = series.mean(yearMonth.atDay(1), to),
            referenceMean = climate?.windowMean(month, 1, toDay),
            referenceStdDev = climate?.windowStdDev(month, 1, toDay),
            daysObserved = series.count(yearMonth.atDay(1), to),
            partial = running,
        )
    }
}

enum class MonthWindow { THIS_MONTH, LAST_30_DAYS }

/**
 * One day of the Month view. [band] is the smoothed normal (±3 days) the chart draws; [referenceMean]
 * is the exact same-calendar-day mean, so period averages agree with the Current view.
 */
data class DayPoint(
    val date: LocalDate,
    val observed: Double?,
    val forecast: Double?,
    val band: DailyStats?,
    val referenceMean: Double?,
)

/**
 * The days of the month of yesterday, or the 30 days ending yesterday followed by the forecast days.
 * Forecast values are attached to the days from today on, for at most [forecastDays] days.
 */
fun dayPoints(
    snapshot: ClimateSnapshot,
    window: MonthWindow,
    forecastDays: Int = 0,
): List<DayPoint> {
    val yesterday = snapshot.today.minusDays(1)
    val forecast = snapshot.forecast
    val forecastEnd = forecast?.start?.plusDays((forecastDays - 1).toLong())
    val dates =
        when (window) {
            MonthWindow.THIS_MONTH -> {
                val first = yesterday.withDayOfMonth(1)
                (0 until yesterday.lengthOfMonth()).map { first.plusDays(it.toLong()) }
            }

            MonthWindow.LAST_30_DAYS -> {
                val observed = (29 downTo 0).map { yesterday.minusDays(it.toLong()) }
                val ahead =
                    if (forecastEnd == null || !forecastEnd.isAfter(yesterday)) {
                        emptyList()
                    } else {
                        generateSequence(yesterday.plusDays(1)) { it.plusDays(1) }
                            .takeWhile { !it.isAfter(forecastEnd) }
                            .toList()
                    }
                observed + ahead
            }
        }
    return dates.map { date ->
        val inForecast =
            forecast != null && forecastEnd != null && date.isAfter(yesterday) &&
                !date.isBefore(forecast.start) && !date.isAfter(forecastEnd)
        DayPoint(
            date = date,
            observed = if (date.isAfter(yesterday)) null else snapshot.series.valueOrNull(date),
            forecast = if (inForecast) forecast!!.valueOrNull(date) else null,
            band = snapshot.reference?.dailyStats(date.monthValue, date.dayOfMonth),
            referenceMean = snapshot.reference?.dailyStats(date.monthValue, date.dayOfMonth, halfWindow = 0)?.mean,
        )
    }
}

/** The next [days] of the forecast compared with the normal of exactly those calendar days. */
data class ForecastOutlook(
    val days: Int,
    val mean: Double,
    val referenceMean: Double?,
) {
    val delta: Double? get() = referenceMean?.let { mean - it }
}

fun forecastOutlook(
    forecast: DailySeries?,
    reference: ReferenceClimate?,
    days: Int,
): ForecastOutlook? {
    if (forecast == null || days <= 0) return null
    val values = forecast.days(forecast.start, forecast.start.plusDays(days - 1L))
    if (values.isEmpty()) return null
    val referenceMeans =
        reference?.let { climate ->
            values.mapNotNull { (date, _) -> climate.dailyStats(date.monthValue, date.dayOfMonth, halfWindow = 0)?.mean }
        }
    return ForecastOutlook(
        days = values.size,
        mean = values.map { it.second }.average(),
        referenceMean = referenceMeans?.takeIf { it.isNotEmpty() }?.average(),
    )
}

data class WindowSummary(
    val observedDays: Int,
    val observedMean: Double?,
    val referenceMean: Double?,
    val warmerDays: Int,
    val colderDays: Int,
) {
    val delta: Double? get() = if (observedMean != null && referenceMean != null) observedMean - referenceMean else null
}

/** Compares the observed days with the reference for exactly the same days. */
fun summarize(points: List<DayPoint>): WindowSummary {
    val observed = points.filter { it.observed != null }
    val comparable = observed.filter { it.referenceMean != null }
    return WindowSummary(
        observedDays = observed.size,
        observedMean = observed.map { it.observed!! }.takeIf { it.isNotEmpty() }?.average(),
        referenceMean = comparable.map { it.referenceMean!! }.takeIf { it.isNotEmpty() }?.average(),
        warmerDays = observed.count { it.band != null && it.observed!! > it.band.mean },
        colderDays = observed.count { it.band != null && it.observed!! < it.band.mean },
    )
}

data class YearSummary(
    /** Day-weighted mean of the monthly deviations, or null if nothing is comparable. */
    val delta: Double?,
    val warmerMonths: Int,
    val comparedMonths: Int,
)

fun summarizeYear(months: List<MonthlyAnomaly>): YearSummary {
    val comparable = months.filter { it.delta != null && it.daysObserved > 0 }
    val totalDays = comparable.sumOf { it.daysObserved }
    return YearSummary(
        delta =
            if (totalDays == 0) {
                null
            } else {
                comparable.sumOf { it.delta!! * it.daysObserved } / totalDays
            },
        warmerMonths = comparable.count { it.delta!! > 0 },
        comparedMonths = comparable.size,
    )
}
