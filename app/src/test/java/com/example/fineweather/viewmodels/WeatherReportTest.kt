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

    private fun WeatherReport.blocks() = (summary as Loadable.Ready).value

    // ---- states ----

    @Test
    fun idle_showsDashesAndThePromptToSearch() {
        val report = report(WeatherUiState(today = today))

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

        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(Loadable.Loading, report.snapshot)
        assertEquals(StatusPicture.LOADING, report.picture)
    }

    @Test
    fun loaded_comparesTheMonthSoFarWithTheSameDaysOfTheReference() {
        val report = report(loaded())

        assertEquals(1, report.dayCount)
        val headline = (report.headline as Loadable.Ready).value
        assertEquals(6.0, headline.delta, 1e-9)
        assertEquals(4.0, headline.reference!!, 1e-9)
        assertEquals(12.0, (report.trend as Loadable.Ready).value.mean, 1e-9)
        assertEquals(Band.WARM, headline.band)
        assertEquals(StatusPicture.WARM, report.picture)
        assertEquals(8.0, report.trend.value.delta!!, 1e-9)
        assertEquals("1961–1990", report.referenceLabel)
        assertEquals(today, report.today)
        assertEquals(
            listOf(
                "April so far: much warmer than the 1961–1990 average (+6.00°C, 10.00°C vs 4.00°C).",
                "Next 7 days: about 8.0°C warmer than usual (12.0°C vs 4.0°C).",
                "Only 1 day so far, so this can change quickly.",
            ),
            report.blocks().map { it.text },
        )
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
    fun forecastDays_changeTheOutlookAndTheTrend() {
        val long = report(loaded(), forecastDays = 14)

        assertTrue(long.blocks()[1].text.startsWith("Next 14 days"))
        assertEquals(13.5, (long.trend as Loadable.Ready).value.mean, 1e-9)
        assertEquals(14, long.trend.value.days)
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

        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.trend)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(Loadable.Loading, report.snapshot)
    }

    @Test
    fun yearStillLoading_keepsTheComparisonLoadingSoItCannotFlipToAnotherPeriod() {
        val report = report(loaded(year = Loadable.Loading))

        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(12.0, (report.trend as Loadable.Ready).value.mean, 1e-9)
    }

    @Test
    fun failedReference_isShownWhereItIsMissingAndTheRestStays() {
        val report = report(loaded(reference = Loadable.Failed(FailKind.NETWORK)))

        assertEquals(Loadable.Failed(FailKind.NETWORK), report.headline)
        assertEquals(StatusPicture.INITIAL, report.picture)
        // the outlook has no normal to compare with, but still its mean
        assertNull((report.trend as Loadable.Ready).value.delta)
        assertNull((report.snapshot as Loadable.Ready).value.reference)
        assertTrue(report.blocks().first().text.contains("reference is not available"))
        // the forecast itself is fine, so there is no word about it
        assertTrue(report.blocks().none { it is SummaryBlock.Caveat })
    }

    @Test
    fun failedForecast_leavesTheRestAndTheSnapshotWithoutForecast() {
        val report = report(loaded(forecast = Loadable.Failed(FailKind.RATE_LIMITED)))

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), report.trend)
        assertTrue(report.headline is Loadable.Ready)
        assertNull((report.snapshot as Loadable.Ready).value.forecast)
    }

    @Test
    fun failedForecast_isSaidInTheSummary() {
        val blocks = report(loaded(forecast = Loadable.Failed(FailKind.RATE_LIMITED))).blocks()

        assertTrue(blocks.first() is SummaryBlock.Headline)
        assertTrue(blocks.none { it.text.startsWith("Next") })
        assertEquals(SummaryBlock.Caveat("The forecast could not be loaded."), blocks.last())
    }

    @Test
    fun failedYear_failsTheComparisonAndTheSnapshot() {
        val report = report(loaded(year = Loadable.Failed(FailKind.OTHER)))

        assertEquals(Loadable.Failed(FailKind.OTHER), report.headline)
        assertEquals(Loadable.Failed(FailKind.OTHER), report.snapshot)
        assertEquals(0, report.dayCount)
        // it says what happened rather than to check back tomorrow, and the whole month's normal stands in
        assertEquals(
            listOf(
                "This year's weather data could not be loaded.",
                "The 1961–1990 average for April is 4.00°C.",
            ),
            report.blocks().take(2).map { it.text },
        )
    }

    @Test
    fun unavailableSlices_showTheirReason() {
        val na = Loadable.Unavailable(Reason.NOT_AVAILABLE)
        val report = report(WeatherUiState(today = today, forecast = na, year = na, reference = na))

        assertEquals(na, report.headline)
        assertEquals(na, report.trend)
        assertEquals(na, report.snapshot)
        assertTrue(report.blocks().single().text.startsWith("Search for a place"))
        assertEquals(StatusPicture.INITIAL, report.picture)
    }

    // ---- month so far ----

    @Test
    fun firstDayOfTheMonth_hasNoMonthToDateAndUsesTheWholeMonthsNormal() {
        val firstOfApril = LocalDate.of(2026, 4, 1)
        val report = report(loaded(today = firstOfApril, year = Loadable.Ready(year(to = LocalDate.of(2026, 3, 31)))))

        assertEquals(0, report.dayCount)
        assertEquals(
            listOf(
                "No complete day of April yet, check back tomorrow.",
                "The 1961–1990 average for April is 4.00°C.",
            ),
            report.blocks().take(2).map { it.text },
        )
    }

    @Test
    fun monthWithoutValues_saysThereIsNoCompleteDayYet() {
        val report = report(loaded(year = Loadable.Ready(year { if (it.monthValue == 4) null else 10.0 })))

        assertEquals("No complete day of April yet, check back tomorrow.", report.blocks().first().text)
        assertEquals(0, report.dayCount)
    }

    @Test
    fun caveat_isAddedOnlyForFewDaysOfTheMonth() {
        fun caveatAfter(day: Int): Boolean {
            val now = LocalDate.of(2026, 4, day)
            val state = loaded(today = now, year = Loadable.Ready(year(to = now.minusDays(1))))
            return report(state).blocks().any { it is SummaryBlock.Caveat }
        }

        assertTrue(caveatAfter(2)) // 1 day
        assertTrue(caveatAfter(7)) // 6 days
        assertFalse(caveatAfter(8)) // 7 days
        assertFalse(caveatAfter(20))
    }

    @Test
    fun caveat_isNeverAddedWhileLoadingOrAfterAnError() {
        assertEquals(Loadable.Loading, report(loaded(year = Loadable.Loading)).summary)
        assertTrue(report(loaded(year = Loadable.Failed(FailKind.OTHER))).blocks().none { it is SummaryBlock.Caveat })
        assertTrue(report(WeatherUiState(today = today)).blocks().none { it is SummaryBlock.Caveat })
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

        assertEquals(plain.summary, withExtras.summary)
        assertEquals(plain.headline, withExtras.headline)
    }

    @Test
    fun aSeriesWithoutTemperatureIsNotAvailable() {
        val noTemperature =
            MetricSeries(mapOf(Metric.SUNSHINE to seriesOf(today, today.plusDays(13)) { 5.0 }))

        val report = report(loaded(forecast = Loadable.Ready(noTemperature)))

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.trend)
        // like any forecast without a value: a series of missing days
        assertFalse((report.snapshot as Loadable.Ready).value.forecast!!.hasValues())
    }

    @Test
    fun extrasAreIdleUntilSomeoneLoadsThem() {
        assertEquals(Loadable.Idle, WeatherUiState(today = today).extras)
        assertEquals(Loadable.Idle, loaded().extras)
    }
}
