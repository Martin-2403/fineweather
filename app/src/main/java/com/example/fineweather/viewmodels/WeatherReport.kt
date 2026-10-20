package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.DataSource
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.domain.SummaryInput
import com.example.fineweather.domain.assess
import com.example.fineweather.domain.buildSummary
import com.example.fineweather.domain.forecastOutlook
import com.example.fineweather.domain.PeriodComparison
import com.example.fineweather.domain.monthToDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Everything loaded for the searched place; [today] is the day the data was requested for. */
data class WeatherUiState(
    val today: LocalDate,
    val search: SearchStatus = SearchStatus.Idle,
    val selectedPlace: GeoPlace? = null,
    val places: List<GeoPlace> = emptyList(),
    /** The 14-day forecast, whatever the 7/14-day setting says. */
    val forecast: Loadable<MetricSeries> = Loadable.Idle,
    /** Daily values of the current year (at least the last 30 days) up to yesterday. */
    val year: Loadable<MetricSeries> = Loadable.Idle,
    /** Daily temperature of the 30-year reference. */
    val reference: Loadable<ReferenceClimate> = Loadable.Idle,
    /**
     * Sunshine and precipitation of the reference, fetched on demand; a metric without data is absent.
     * Load only after the same place's [reference] slice is Ready, guard it with the search
     * generation, and cancel it in `withAllLoading()` and `reloadReference()`.
     */
    val extras: Loadable<Map<Metric, ReferenceClimate>> = Loadable.Idle,
)

/** Which picture the header shows; WARM and COLD are above and below the normal of the metric, whichever it is. */
enum class StatusPicture { LOADING, INITIAL, WARM, COLD, EQUAL }

/** What the screen shows for the loaded data and the current settings. */
data class WeatherReport(
    val today: LocalDate,
    val referenceLabel: String,
    /** This month so far against the same days of the reference. */
    val headline: Loadable<Assessment>,
    /** The next forecast days against the normal of those days. */
    val trend: Loadable<ForecastOutlook>,
    val summary: Loadable<List<SummaryBlock>>,
    val picture: StatusPicture,
    /** For the Month and Year views. */
    val snapshot: Loadable<ClimateSnapshot>,
    /** Complete days of this month so far. */
    val dayCount: Int,
    /** What [headline], [trend], [summary] and [snapshot] are about; their values are in its period unit. */
    val metric: Metric = Metric.TEMPERATURE,
)

/**
 * The report of [metric]. The [picture] follows the headline of the metric, so it waits for the same data.
 * Rain values are totals in mm, sunshine and temperature daily means.
 */
fun buildReport(
    state: WeatherUiState,
    forecastDays: Int,
    mode: TemperatureComparisonMode,
    historic: HistoricReference,
    metric: Metric = Metric.TEMPERATURE,
): WeatherReport {
    val today = state.today
    val forecast = state.forecast.series(metric)
    val forecastSeries = forecast.valueOrNull()
    val month = monthSoFar(state, mode, metric)
    val year = month.year
    val referenceSlice = month.referenceSlice
    val climate = referenceSlice.valueOrNull()

    val outlook = forecastSeries?.let { forecastOutlook(it, climate, forecastDays, metric) }
    val dayCount = month.comparison?.daysObserved ?: 0
    val referenceStdDev = month.stdDev
    val monthValue = month.observed
    val historicValue = month.reference
    val headline = month.headline

    val trend: Loadable<ForecastOutlook> =
        when {
            forecast is Loadable.Ready && referenceSlice is Loadable.Loading -> Loadable.Loading
            else -> forecast.map { outlook }.availableOr(Reason.NOT_AVAILABLE)
        }
    val anyLoading =
        forecast is Loadable.Loading || year is Loadable.Loading || referenceSlice is Loadable.Loading

    val failed =
        buildSet {
            if (year is Loadable.Failed) add(DataSource.OBSERVED)
            if (referenceSlice is Loadable.Failed) add(DataSource.REFERENCE)
            if (forecast is Loadable.Failed) add(DataSource.FORECAST)
        }
    return WeatherReport(
        today = today,
        referenceLabel = historic.displayLabel(today),
        headline = headline,
        trend = trend,
        summary =
            if (anyLoading) {
                Loadable.Loading
            } else {
                Loadable.Ready(
                    buildSummary(
                        SummaryInput(
                            monthName = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            monthToDate = monthValue.valueOrNull(),
                            reference = historicValue.valueOrNull(),
                            referenceLabel = historic.displayLabel(today),
                            stdDev = referenceStdDev,
                            mode = mode,
                            outlook = outlook,
                            daysObserved = dayCount,
                            metric = metric,
                            failed = failed,
                        ),
                    ),
                )
            },
        picture = pictureFor(headline),
        snapshot =
            when {
                anyLoading -> Loadable.Loading
                year is Loadable.Ready -> Loadable.Ready(ClimateSnapshot(year.value, climate, today, forecastSeries, metric))
                else -> year.carryOver()
            },
        dayCount = dayCount,
        metric = metric,
    )
}

/** This month so far of [metric] against the reference, with the slices it is made of. */
private class MonthSoFar(
    val year: Loadable<DailySeries>,
    val referenceSlice: Loadable<ReferenceClimate>,
    val comparison: PeriodComparison?,
    val observed: Loadable<Double>,
    val reference: Loadable<Double>,
    val stdDev: Double?,
    val headline: Loadable<Assessment>,
)

private fun monthSoFar(
    state: WeatherUiState,
    mode: TemperatureComparisonMode,
    metric: Metric,
): MonthSoFar {
    val today = state.today
    val year = state.year.series(metric)
    val referenceSlice = state.referenceOf(metric)
    val climate = referenceSlice.valueOrNull()
    val comparison = year.valueOrNull()?.let { monthToDate(it, climate, today, metric) }
    // Without a complete day of the month yet, fall back to the whole month's normal.
    val monthDays = YearMonth.from(today).lengthOfMonth()
    val referenceMean =
        if (comparison != null) {
            comparison.referenceMean
        } else {
            climate?.monthMean(today.monthValue)?.let { metric.periodValue(it, monthDays) }
        }
    val stdDev =
        if (comparison != null) {
            comparison.referenceStdDev
        } else {
            climate?.monthStdDev(today.monthValue)?.let { metric.periodValue(it, monthDays) }
        }
    val observed: Loadable<Double> =
        year.map { comparison?.observedMean }.availableOr(
            if (today.dayOfMonth == 1) Reason.NOT_AVAILABLE else Reason.NO_DATA_YET,
        )
    // The same days of the reference need the year's last complete day, so wait for it too.
    val reference: Loadable<Double> =
        if (referenceSlice is Loadable.Ready && year is Loadable.Loading) {
            Loadable.Loading
        } else {
            referenceSlice.map { referenceMean }.availableOr(Reason.NOT_AVAILABLE)
        }
    val headline = combineLoadables(observed, reference) { o, r -> assess(metric, o, r, stdDev, mode) }
    return MonthSoFar(year, referenceSlice, comparison, observed, reference, stdDev, headline)
}

/**
 * The series of [metric] in a loaded series. Without a single value it is a series of missing days, so the
 * report keeps telling "no data" for the days apart from "not loaded".
 */
private fun Loadable<MetricSeries>.series(metric: Metric): Loadable<DailySeries> =
    map { it[metric] ?: DailySeries.missing(it.start, it.size) }

/**
 * The normals of [metric]. Sunshine and rain come with [WeatherUiState.extras], which are on their way
 * (Loading) once the temperature reference is or will be there; a metric the extras lack is not available.
 */
private fun WeatherUiState.referenceOf(metric: Metric): Loadable<ReferenceClimate> =
    when {
        metric == Metric.TEMPERATURE -> reference
        extras is Loadable.Ready -> extras.value[metric]?.let { Loadable.Ready(it) } ?: Loadable.Unavailable(Reason.NOT_AVAILABLE)
        // Extras follow the temperature reference: nothing is on its way when that is missing or failed.
        extras is Loadable.Idle ->
            if (reference is Loadable.Ready || reference is Loadable.Loading) Loadable.Loading else reference.carryOver()
        else -> extras.carryOver()
    }

/** A loaded null becomes [Loadable.Unavailable]; every other state stays as it is. */
private fun <T : Any> Loadable<T?>.availableOr(reason: Reason): Loadable<T> =
    when (this) {
        is Loadable.Ready -> value?.let { Loadable.Ready(it) } ?: Loadable.Unavailable(reason)
        else -> carryOver()
    }

private fun pictureFor(headline: Loadable<Assessment>): StatusPicture =
    when (headline) {
        is Loadable.Loading -> StatusPicture.LOADING
        is Loadable.Ready ->
            when (headline.value.band) {
                Band.WARM -> StatusPicture.WARM
                Band.COLD -> StatusPicture.COLD
                Band.NEUTRAL -> StatusPicture.EQUAL
            }

        else -> StatusPicture.INITIAL
    }
