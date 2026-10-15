package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.domain.metricSeriesOf
import com.example.fineweather.domain.seriesOf
import com.example.fineweather.domain.syntheticReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherReportTest {

    private val today = LocalDate.of(2026, 4, 2)

    private fun year(
        to: LocalDate = today.minusDays(1),
        valueOf: (LocalDate) -> Double? = { 10.0 },
    ): MetricSeries = metricSeriesOf(LocalDate.of(2026, 1, 1), to, valueOf)

    /** 12.0 for the first week, 15.0 for the second. */
    private val forecast = metricSeriesOf(today, today.plusDays(13)) { if (it.isBefore(today.plusDays(7))) 12.0 else 15.0 }

    /** Every day of month m is worth m in every reference year. */
    private val climate = syntheticReference { it.monthValue.toDouble() }

    private fun loaded(
        forecast: Loadable<MetricSeries> = Loadable.Ready(this.forecast),
        year: Loadable<MetricSeries> = Loadable.Ready(year()),
        reference: Loadable<ReferenceClimate> = Loadable.Ready(climate),
        today: LocalDate = this.today,
    ) = WeatherUiState(today = today, forecast = forecast, year = year, reference = reference)

    private fun report(
        state: WeatherUiState,
        forecastDays: Int = 7,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        historic: HistoricReference = HistoricReference.CLASSIC,
    ) = buildReport(state, forecastDays, mode, historic)

    private fun WeatherReport.values() = rows.map { it.value }

    // ---- states ----

    @Test
    fun idle_showsDashesAndThePromptToSearch() {
        val report = report(WeatherUiState(today = today))

        report.values().forEach { assertEquals(Loadable.Idle, it) }
        assertEquals(Loadable.Idle, report.headline)
        assertEquals(Loadable.Idle, report.trend)
        assertEquals(Loadable.Idle, report.snapshot)
        assertEquals(StatusPicture.INITIAL, report.picture)
        val summary = (report.summary as Loadable.Ready).value.single()
        assertTrue(summary.text.startsWith("Search for a place"))
    }

    @Test
    fun loading_hasNoValuesAndNeverTheSearchPrompt() {
        val loading =
            WeatherUiState(
                today = today,
                forecast = Loadable.Loading,
                year = Loadable.Loading,
                reference = Loadable.Loading,
            )

        val report = report(loading)

        report.values().forEach { assertEquals(Loadable.Loading, it) }
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(Loadable.Loading, report.snapshot)
        assertEquals(StatusPicture.LOADING, report.picture)
        assertFalse(report.rows.any { it.showWarning })
    }

    @Test
    fun loaded_comparesTheMonthSoFarWithTheSameDaysOfTheReference() {
        val report = report(loaded())

        assertEquals(
            listOf("Last 31 days", "This month so far", "Next 7 days (forecast)", "Historical (30y), same days"),
            report.rows.map { it.label },
        )
        assertEquals(
            listOf(10.0, 10.0, 12.0, 4.0).map { Loadable.Ready(it) },
            report.values(),
        )
        assertEquals(1, report.dayCount)
        val headline = (report.headline as Loadable.Ready).value
        assertEquals(6.0, headline.delta, 1e-9)
        assertEquals(Band.WARM, headline.band)
        assertEquals(StatusPicture.WARM, report.picture)
        assertEquals(8.0, (report.trend as Loadable.Ready).value.delta!!, 1e-9)
        assertEquals("1961–1990", report.referenceLabel)
        assertEquals(today, report.today)
        val snapshot = (report.snapshot as Loadable.Ready).value
        assertEquals(today, snapshot.today)
        assertEquals(forecast.start, snapshot.forecast!!.start)
    }

    @Test
    fun picture_followsTheBandOfTheDivergence() {
        val cold = report(loaded(year = Loadable.Ready(year { -5.0 })))
        assertEquals(StatusPicture.COLD, cold.picture)
        val equal = report(loaded(year = Loadable.Ready(year { 4.2 })))
        assertEquals(StatusPicture.EQUAL, equal.picture)
    }

    @Test
    fun summary_namesTheMonthOfTheReportsDay() {
        val april = report(loaded())
        val october = report(loaded(today = LocalDate.of(2026, 10, 14), year = Loadable.Ready(year(to = LocalDate.of(2026, 10, 13)))))

        val aprilHeadline = (april.summary as Loadable.Ready).value.first() as SummaryBlock.Headline
        val octoberHeadline = (october.summary as Loadable.Ready).value.first() as SummaryBlock.Headline
        assertTrue(aprilHeadline.text.startsWith("April so far"))
        assertTrue(octoberHeadline.text.startsWith("October so far"))
    }

    @Test
    fun forecastDays_changeTheForecastRowAndTheTrend() {
        val long = report(loaded(), forecastDays = 14)

        assertEquals("Next 14 days (forecast)", long.rows[2].label)
        assertEquals(Loadable.Ready(13.5), long.rows[2].value)
        assertEquals(14, (long.trend as Loadable.Ready).value.days)
    }

    @Test
    fun comparisonMode_decidesWhichStdDevTheAssessmentUses() {
        val noisy = syntheticReference { date -> date.monthValue + if (date.year % 2 == 0) 1.0 else -1.0 }
        val state = loaded(reference = Loadable.Ready(noisy))

        val absolute = (report(state).headline as Loadable.Ready).value
        val normalized = (report(state, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY).headline as Loadable.Ready).value

        assertFalse(absolute.normalized)
        assertTrue(normalized.normalized)
    }

    // ---- partial states ----

    @Test
    fun referenceStillLoading_keepsTheComparisonAndTheTrendLoading() {
        val report = report(loaded(reference = Loadable.Loading))

        assertEquals(Loadable.Ready(10.0), report.rows[0].value)
        assertEquals(Loadable.Ready(10.0), report.rows[1].value)
        assertEquals(Loadable.Ready(12.0), report.rows[2].value)
        assertEquals(Loadable.Loading, report.rows[3].value)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(Loadable.Loading, report.snapshot)
    }

    @Test
    fun yearStillLoading_keepsTheHistoricRowLoadingSoItCannotFlipToAnotherPeriod() {
        val report = report(loaded(year = Loadable.Loading))

        assertEquals(Loadable.Loading, report.rows[3].value)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Ready(12.0), report.rows[2].value)
    }

    @Test
    fun failedReference_isShownWhereItIsMissingAndTheRestStays() {
        val report = report(loaded(reference = Loadable.Failed(FailKind.NETWORK)))

        assertEquals(Loadable.Failed(FailKind.NETWORK), report.rows[3].value)
        assertEquals(Loadable.Failed(FailKind.NETWORK), report.headline)
        assertEquals(Loadable.Ready(10.0), report.rows[1].value)
        assertEquals(StatusPicture.INITIAL, report.picture)
        // the outlook has no normal to compare with, but still its mean
        assertNull((report.trend as Loadable.Ready).value.delta)
        assertNull((report.snapshot as Loadable.Ready).value.reference)
        val summary = (report.summary as Loadable.Ready).value.single()
        assertTrue(summary.text.contains("reference is not available"))
    }

    @Test
    fun failedForecast_leavesTheRestAndTheSnapshotWithoutForecast() {
        val report = report(loaded(forecast = Loadable.Failed(FailKind.RATE_LIMITED)))

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), report.rows[2].value)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), report.trend)
        assertTrue(report.headline is Loadable.Ready)
        assertNull((report.snapshot as Loadable.Ready).value.forecast)
    }

    @Test
    fun failedYear_failsItsRowsAndTheSnapshot() {
        val report = report(loaded(year = Loadable.Failed(FailKind.OTHER)))

        assertEquals(Loadable.Failed(FailKind.OTHER), report.rows[0].value)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.rows[1].value)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.snapshot)
        assertEquals(0, report.dayCount)
        // without the year, the whole month's normal stands in for the same days
        assertEquals(Loadable.Ready(4.0), report.rows[3].value)
        assertEquals("Historical (30y) for this month", report.rows[3].label)
    }

    @Test
    fun unavailableSlices_showTheirReason() {
        val na = Loadable.Unavailable(Reason.NOT_AVAILABLE)
        val report = report(WeatherUiState(today = today, forecast = na, year = na, reference = na))

        report.values().forEach { assertEquals(na, it) }
        assertEquals(na, report.snapshot)
        assertEquals(StatusPicture.INITIAL, report.picture)
    }

    // ---- month so far ----

    @Test
    fun firstDayOfTheMonth_hasNoMonthToDateAndUsesTheWholeMonthsNormal() {
        val firstOfApril = LocalDate.of(2026, 4, 1)
        val report = report(loaded(today = firstOfApril, year = Loadable.Ready(year(to = LocalDate.of(2026, 3, 31)))))

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[1].value)
        assertFalse(report.rows[1].showWarning)
        assertEquals(0, report.dayCount)
        assertEquals("Historical (30y) for this month", report.rows[3].label)
        assertEquals(Loadable.Ready(4.0), report.rows[3].value)
        assertEquals(Loadable.Ready(10.0), report.rows[0].value)
    }

    @Test
    fun monthWithoutValues_saysNoDataYetAndOffersTheExplanation() {
        val report = report(loaded(year = Loadable.Ready(year { if (it.monthValue == 4) null else 10.0 })))

        assertEquals(Loadable.Unavailable(Reason.NO_DATA_YET), report.rows[1].value)
        assertTrue(report.rows[1].showWarning)
        assertEquals(0, report.dayCount)
    }

    @Test
    fun warning_isOfferedOnlyForFewDaysOfTheMonth() {
        fun warningAfter(day: Int): Boolean {
            val now = LocalDate.of(2026, 4, day)
            val state = loaded(today = now, year = Loadable.Ready(year(to = now.minusDays(1))))
            return report(state).rows[1].showWarning
        }

        assertTrue(warningAfter(2)) // 1 day
        assertTrue(warningAfter(7)) // 6 days
        assertFalse(warningAfter(8)) // 7 days
        assertFalse(warningAfter(20))
    }

    @Test
    fun warning_isNeverOfferedWhileLoadingOrAfterAnError() {
        assertFalse(report(loaded(year = Loadable.Loading)).rows[1].showWarning)
        assertFalse(report(loaded(year = Loadable.Failed(FailKind.OTHER))).rows[1].showWarning)
        assertFalse(report(WeatherUiState(today = today)).rows[1].showWarning)
    }

    @Test
    fun lastDaysRow_isNotAvailableWithoutAnyValue() {
        val report = report(loaded(year = Loadable.Ready(year { null })))

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[0].value)
    }

    // ---- metrics ----

    @Test
    fun theReportIsBuiltFromTheTemperatureOfTheMetricSeries() {
        // wild sunshine and rain values do not leak into the temperature report
        val noisy =
            MetricSeries(
                year().toMap() + mapOf(
                    Metric.SUNSHINE to seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1)) { 99.0 },
                    Metric.PRECIPITATION to seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1)) { 77.0 },
                ),
            )

        val plain = report(loaded())
        val withExtras = report(loaded(year = Loadable.Ready(noisy)))

        assertEquals(plain.values(), withExtras.values())
        assertEquals(plain.headline, withExtras.headline)
    }

    @Test
    fun aSeriesWithoutTemperatureIsNotAvailable() {
        val noTemperature =
            MetricSeries(mapOf(Metric.SUNSHINE to seriesOf(today, today.plusDays(13)) { 5.0 }))

        val report = report(loaded(forecast = Loadable.Ready(noTemperature)))

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[2].value)
        // like any forecast without a value: a series of missing days
        assertFalse((report.snapshot as Loadable.Ready).value.forecast!!.hasValues())
    }

    @Test
    fun extrasAreIdleUntilSomeoneLoadsThem() {
        assertEquals(Loadable.Idle, WeatherUiState(today = today).extras)
        assertEquals(Loadable.Idle, loaded().extras)
    }
}
