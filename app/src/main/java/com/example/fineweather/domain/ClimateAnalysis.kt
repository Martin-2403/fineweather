package com.example.fineweather.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * An observed period compared with the same calendar days of the reference years. The three values are
 * in the period unit of [metric]: a daily mean, or for rain the total over the days [fromDay]..[toDay].
 */
data class PeriodComparison(
    val observedMean: Double,
    val referenceMean: Double?,
    val referenceStdDev: Double?,
    val daysObserved: Int,
    val fromDay: Int,
    val toDay: Int,
    val metric: Metric = Metric.TEMPERATURE,
)

/** The percent of [reference] that [delta] is, if metric adds up and there is a reference. */
private fun Metric.percent(
    delta: Double?,
    reference: Double?,
): Double? = if (delta == null || reference == null) null else percentOfNormal(delta, reference)

/**
 * Month-to-date up to yesterday (the last complete day), compared with the same days of the
 * reference. Null on the first day of a month, when no day of the month is complete yet.
 */
fun monthToDate(
    series: DailySeries,
    climate: ReferenceClimate?,
    today: LocalDate,
    metric: Metric = Metric.TEMPERATURE,
): PeriodComparison? {
    if (today.dayOfMonth == 1) return null
    val yesterday = today.minusDays(1)
    val first = yesterday.withDayOfMonth(1)
    val observed = series.mean(first, yesterday) ?: return null
    val toDay = yesterday.dayOfMonth
    return PeriodComparison(
        observedMean = metric.periodValue(observed, toDay),
        referenceMean = climate?.windowMean(yesterday.monthValue, 1, toDay)?.let { metric.periodValue(it, toDay) },
        referenceStdDev = climate?.windowStdDev(yesterday.monthValue, 1, toDay)?.let { metric.periodValue(it, toDay) },
        daysObserved = series.count(first, yesterday),
        fromDay = 1,
        toDay = toDay,
        metric = metric,
    )
}

/** The last [days] complete days ending yesterday: their mean, or for rain their total (mean times [days]). */
fun lastDaysMean(
    series: DailySeries,
    today: LocalDate,
    days: Int = 31,
    metric: Metric = Metric.TEMPERATURE,
): Double? {
    val yesterday = today.minusDays(1)
    return series.mean(yesterday.minusDays(days - 1L), yesterday)?.let { metric.periodValue(it, days) }
}

/**
 * One month of the Year view. The three values are in the period unit of [metric]: a daily mean, or for
 * rain the total over the days compared (the month so far while it is [partial], else the whole month).
 */
data class MonthlyAnomaly(
    val month: Int,
    val observedMean: Double?,
    val referenceMean: Double?,
    val referenceStdDev: Double?,
    val daysObserved: Int,
    /** True while the month is still running, i.e. only the first days are compared. */
    val partial: Boolean,
    val metric: Metric = Metric.TEMPERATURE,
) {
    val delta: Double? get() = if (observedMean != null && referenceMean != null) observedMean - referenceMean else null

    /** [delta] as a percent of the normal; only for metrics that add up. */
    val deltaPercent: Double? get() = metric.percent(delta, referenceMean)
}

/**
 * January up to the month of yesterday. Finished months use the full-month reference, the
 * running month only the same days of the reference.
 */
fun yearToDate(
    series: DailySeries,
    climate: ReferenceClimate?,
    today: LocalDate,
    metric: Metric = Metric.TEMPERATURE,
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
            observedMean = series.mean(yearMonth.atDay(1), to)?.let { metric.periodValue(it, toDay) },
            referenceMean = climate?.windowMean(month, 1, toDay)?.let { metric.periodValue(it, toDay) },
            referenceStdDev = climate?.windowStdDev(month, 1, toDay)?.let { metric.periodValue(it, toDay) },
            daysObserved = series.count(yearMonth.atDay(1), to),
            partial = running,
            metric = metric,
        )
    }
}

enum class MonthWindow { THIS_MONTH, LAST_30_DAYS }

/**
 * One day of the Month view, in the daily unit of the metric. [band] is the smoothed normal (±3 days) the
 * chart draws, never below the metric's lower bound; [referenceMean] is the exact same-calendar-day mean,
 * so period averages agree with the Current view (as long as no day is missing).
 */
data class DayPoint(
    val date: LocalDate,
    val observed: Double?,
    val forecast: Double?,
    val band: DailyStats?,
    val referenceMean: Double?,
    /** Whether the day is over (up to yesterday), with or without a value. */
    val elapsed: Boolean = observed != null,
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
            forecast = if (inForecast) forecast.valueOrNull(date) else null,
            band = snapshot.reference?.dailyStats(date.monthValue, date.dayOfMonth)?.copy(floor = snapshot.metric.lowerBound),
            referenceMean = snapshot.reference?.dailyStats(date.monthValue, date.dayOfMonth, halfWindow = 0)?.mean,
            elapsed = !date.isAfter(yesterday),
        )
    }
}

/**
 * The next [days] of the forecast compared with the normal of exactly those calendar days. [mean] and
 * [referenceMean] are in the period unit of [metric]: a daily mean, or for rain the total over [days].
 */
data class ForecastOutlook(
    val days: Int,
    val mean: Double,
    val referenceMean: Double?,
    val metric: Metric = Metric.TEMPERATURE,
) {
    val delta: Double? get() = referenceMean?.let { mean - it }

    /** [delta] as a percent of the normal; only for metrics that add up. */
    val deltaPercent: Double? get() = metric.percent(delta, referenceMean)
}

fun forecastOutlook(
    forecast: DailySeries?,
    reference: ReferenceClimate?,
    days: Int,
    metric: Metric = Metric.TEMPERATURE,
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
        mean = metric.periodValue(values.map { it.second }.average(), values.size),
        referenceMean = referenceMeans?.takeIf { it.isNotEmpty() }?.average()?.let { metric.periodValue(it, values.size) },
        metric = metric,
    )
}

/**
 * [observedMean] and [referenceMean] are in the period unit of [metric]; for rain the totals over the days
 * of the window that are over. "Warmer" and "colder" days mean above and below the normal for any metric.
 */
data class WindowSummary(
    val observedDays: Int,
    val observedMean: Double?,
    val referenceMean: Double?,
    val warmerDays: Int,
    val colderDays: Int,
    val metric: Metric = Metric.TEMPERATURE,
    /** Sunshine totals over the observed and comparable days, in hours. */
    val observedTotal: Double? = null,
    val referenceTotal: Double? = null,
) {
    val delta: Double? get() = if (observedMean != null && referenceMean != null) observedMean - referenceMean else null

    /** [delta] as a percent of the normal; only for metrics that add up. */
    val deltaPercent: Double? get() = metric.percent(delta, referenceMean)
}

/** Compares the observed days with the reference for exactly the same days. */
fun summarize(
    points: List<DayPoint>,
    metric: Metric = Metric.TEMPERATURE,
): WindowSummary {
    val observed = points.filter { it.observed != null }
    val comparable = observed.filter { it.referenceMean != null }
    val days = points.count { it.elapsed }
    val sunshineTotals =
        if (metric == Metric.SUNSHINE) {
            observed.sumOf { it.observed!! } to comparable.takeIf { it.isNotEmpty() }?.sumOf { it.referenceMean!! }
        } else {
            null to null
        }
    return WindowSummary(
        observedDays = observed.size,
        observedMean = observed.map { it.observed!! }.takeIf { it.isNotEmpty() }?.average()?.let { metric.periodValue(it, days) },
        referenceMean = comparable.map { it.referenceMean!! }.takeIf { it.isNotEmpty() }?.average()?.let { metric.periodValue(it, days) },
        warmerDays = observed.count { it.band != null && it.observed!! > it.band.mean },
        colderDays = observed.count { it.band != null && it.observed!! < it.band.mean },
        metric = metric,
        observedTotal = sunshineTotals.first,
        referenceTotal = sunshineTotals.second,
    )
}

data class YearSummary(
    /**
     * Day-weighted mean of the monthly deviations, or for rain their sum (the year's surplus in mm);
     * null if nothing is comparable.
     */
    val delta: Double?,
    /** Months above their normal; for sunshine and rain these are the sunnier and wetter ones. */
    val warmerMonths: Int,
    val comparedMonths: Int,
    /** For rain, [delta] as a percent of the normal of the compared months. */
    val deltaPercent: Double? = null,
)

fun summarizeYear(months: List<MonthlyAnomaly>): YearSummary {
    val comparable = months.filter { it.delta != null && it.daysObserved > 0 }
    val totalDays = comparable.sumOf { it.daysObserved }
    val adds = comparable.any { it.metric.aggregation == Aggregation.TOTAL }
    val delta =
        when {
            totalDays == 0 -> null
            adds -> comparable.sumOf { it.delta!! }
            else -> comparable.sumOf { it.delta!! * it.daysObserved } / totalDays
        }
    return YearSummary(
        delta = delta,
        warmerMonths = comparable.count { it.delta!! > 0 },
        comparedMonths = comparable.size,
        deltaPercent =
            if (adds && delta != null) {
                comparable.first().metric.percent(delta, comparable.sumOf { it.referenceMean!! })
            } else {
                null
            },
    )
}

/*
 * The analyses of a snapshot, always in its own metric. Prefer these over the functions above wherever a
 * snapshot is at hand, so the metric cannot be forgotten (their default is temperature).
 */

fun ClimateSnapshot.monthToDate(): PeriodComparison? = monthToDate(series, reference, today, metric)

fun ClimateSnapshot.lastDays(days: Int = 31): Double? = lastDaysMean(series, today, days, metric)

/** The months of the year up to yesterday, see [yearToDate]. */
fun ClimateSnapshot.months(): List<MonthlyAnomaly> = yearToDate(series, reference, today, metric)

/** The next [days] of the forecast, see [forecastOutlook]. */
fun ClimateSnapshot.outlook(days: Int): ForecastOutlook? = forecastOutlook(forecast, reference, days, metric)

fun ClimateSnapshot.summarize(points: List<DayPoint>): WindowSummary = summarize(points, metric)
