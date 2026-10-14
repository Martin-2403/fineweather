package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Assessment
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.domain.SummaryInput
import com.example.fineweather.domain.assess
import com.example.fineweather.domain.buildSummary
import com.example.fineweather.domain.forecastOutlook
import com.example.fineweather.domain.lastDaysMean
import com.example.fineweather.domain.monthToDate
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Everything loaded for the searched place; [today] is the day the data was requested for. */
data class WeatherUiState(
    val today: LocalDate,
    val search: SearchStatus = SearchStatus.Idle,
    val selectedPlace: GeoPlace? = null,
    val places: List<GeoPlace> = emptyList(),
    /** The 14-day forecast, whatever the 7/14-day setting says. */
    val forecast: Loadable<DailySeries> = Loadable.Idle,
    /** Daily values of the current year (at least the last 30 days) up to yesterday. */
    val year: Loadable<DailySeries> = Loadable.Idle,
    val reference: Loadable<ReferenceClimate> = Loadable.Idle,
)

data class ReportRow(
    val label: String,
    val value: Loadable<Double>,
    /** Whether the value rests on so few days that the UI offers an explanation. */
    val showWarning: Boolean = false,
)

enum class StatusPicture { LOADING, INITIAL, WARM, COLD, EQUAL }

/** What the screen shows for the loaded data and the current settings. */
data class WeatherReport(
    val today: LocalDate,
    val referenceLabel: String,
    val rows: List<ReportRow>,
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
)

fun buildReport(
    state: WeatherUiState,
    forecastDays: Int,
    mode: TemperatureComparisonMode,
    historic: HistoricReference,
): WeatherReport {
    val today = state.today
    val forecastSeries = state.forecast.valueOrNull()
    val yearSeries = state.year.valueOrNull()
    val climate = state.reference.valueOrNull()

    val monthToDate = yearSeries?.let { monthToDate(it, climate, today) }
    val outlook = forecastSeries?.let { forecastOutlook(it, climate, forecastDays) }
    val dayCount = monthToDate?.daysObserved ?: 0
    // Without a complete day of the month yet, fall back to the whole month's normal.
    val referenceMean = monthToDate?.referenceMean ?: climate?.monthMean(today.monthValue)
    val referenceStdDev =
        if (monthToDate != null) monthToDate.referenceStdDev else climate?.monthStdDev(today.monthValue)

    val lastDays: Loadable<Double> =
        state.year.map { lastDaysMean(it, today) }.availableOr(Reason.NOT_AVAILABLE)
    val monthValue: Loadable<Double> =
        state.year.map { monthToDate?.observedMean }.availableOr(
            if (today.dayOfMonth == 1) Reason.NOT_AVAILABLE else Reason.NO_DATA_YET,
        )
    val forecastValue: Loadable<Double> =
        state.forecast.map { outlook?.mean }.availableOr(Reason.NOT_AVAILABLE)
    // The same days of the reference need the year's last complete day, so wait for it too.
    val historicValue: Loadable<Double> =
        if (state.reference is Loadable.Ready && state.year is Loadable.Loading) {
            Loadable.Loading
        } else {
            state.reference.map { referenceMean }.availableOr(Reason.NOT_AVAILABLE)
        }

    val headline = combineLoadables(monthValue, historicValue) { observed, reference ->
        assess(observed, reference, referenceStdDev, mode)
    }
    val trend: Loadable<ForecastOutlook> =
        when {
            state.forecast is Loadable.Ready && state.reference is Loadable.Loading -> Loadable.Loading
            else -> state.forecast.map { outlook }.availableOr(Reason.NOT_AVAILABLE)
        }
    val anyLoading =
        state.forecast is Loadable.Loading || state.year is Loadable.Loading || state.reference is Loadable.Loading

    val monthWarning =
        (monthValue is Loadable.Ready && dayCount in 1..6) ||
            (monthValue is Loadable.Unavailable && monthValue.reason == Reason.NO_DATA_YET)
    return WeatherReport(
        today = today,
        referenceLabel = historic.displayLabel(today),
        rows =
            listOf(
                ReportRow("Last 31 days", lastDays),
                ReportRow("This month so far", monthValue, monthWarning),
                ReportRow("Next $forecastDays days (forecast)", forecastValue),
                ReportRow(
                    if (dayCount > 0) "Historical (30y), same days" else "Historical (30y) for this month",
                    historicValue,
                ),
            ),
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
                        ),
                    ),
                )
            },
        picture = pictureFor(headline),
        snapshot =
            when {
                anyLoading -> Loadable.Loading
                yearSeries != null -> Loadable.Ready(ClimateSnapshot(yearSeries, climate, today, forecastSeries))
                else -> state.year.carryOver()
            },
        dayCount = dayCount,
    )
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
