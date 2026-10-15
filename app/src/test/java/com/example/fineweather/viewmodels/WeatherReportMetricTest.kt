package com.example.fineweather.viewmodels

import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.domain.seriesOf
import com.example.fineweather.domain.syntheticReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherReportMetricTest {

    private val today = LocalDate.of(2026, 10, 14) // yesterday: Oct 13, 13 days of the month

    private fun year(
        to: LocalDate = today.minusDays(1),
        temperature: (LocalDate) -> Double? = { 10.0 },
        sunshine: (LocalDate) -> Double? = { 6.0 },
        rain: (LocalDate) -> Double? = { 2.0 },
    ): MetricSeries {
        val start = LocalDate.of(2026, 1, 1)
        return MetricSeries(
            mapOf(
                Metric.TEMPERATURE to seriesOf(start, to, temperature),
                Metric.SUNSHINE to seriesOf(start, to, sunshine),
                Metric.PRECIPITATION to seriesOf(start, to, rain),
            ),
        )
    }

    /** First week 7 h of sun and 3 mm, second week 4 h and 1 mm. */
    private val forecast =
        MetricSeries(
            mapOf(
                Metric.TEMPERATURE to seriesOf(today, today.plusDays(13)) { 12.0 },
                Metric.SUNSHINE to seriesOf(today, today.plusDays(13)) { if (it.isBefore(today.plusDays(7))) 7.0 else 4.0 },
                Metric.PRECIPITATION to seriesOf(today, today.plusDays(13)) { if (it.isBefore(today.plusDays(7))) 3.0 else 1.0 },
            ),
        )

    /** Every day of month m is worth m, so October is 10 °C. */
    private val temperatureClimate = syntheticReference { it.monthValue.toDouble() }
    private val sunClimate = syntheticReference { 5.0 }
    private val rainClimate = syntheticReference { 1.0 }
    private val extras = mapOf(Metric.SUNSHINE to sunClimate, Metric.PRECIPITATION to rainClimate)

    private fun loaded(
        forecast: Loadable<MetricSeries> = Loadable.Ready(this.forecast),
        year: Loadable<MetricSeries> = Loadable.Ready(year()),
        reference: Loadable<ReferenceClimate> = Loadable.Ready(temperatureClimate),
        extras: Loadable<Map<Metric, ReferenceClimate>> = Loadable.Ready(this.extras),
        today: LocalDate = this.today,
    ) = WeatherUiState(today = today, forecast = forecast, year = year, reference = reference, extras = extras)

    private fun report(
        state: WeatherUiState,
        metric: Metric,
        forecastDays: Int = 7,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
    ) = buildReport(state, forecastDays, mode, HistoricReference.CLASSIC, metric)

    private fun WeatherReport.values() = rows.map { it.value }

    // ---- rain ----

    @Test
    fun rain_reportsTotalsInMillimetres() {
        val report = report(loaded(), Metric.PRECIPITATION)

        assertEquals(Metric.PRECIPITATION, report.metric)
        assertEquals(
            listOf("Last 31 days", "This month so far", "Next 7 days (forecast)", "Historical (30y), same days"),
            report.rows.map { it.label },
        )
        // 31 and 13 days of 2 mm, 7 days of 3 mm, 13 days of 1 mm
        assertEquals(listOf(62.0, 26.0, 21.0, 13.0).map { Loadable.Ready(it) }, report.values())
        assertEquals(13, report.dayCount)
    }

    @Test
    fun rain_headlineIsThePercentJudgedAssessment() {
        val headline = (report(loaded(), Metric.PRECIPITATION).headline as Loadable.Ready).value

        assertEquals(Metric.PRECIPITATION, headline.metric)
        assertEquals(13.0, headline.delta, 1e-9)
        assertEquals(100.0, headline.percent!!, 1e-9)
        assertEquals(3, headline.severity)
        assertEquals(Band.WARM, headline.band)
    }

    @Test
    fun rain_trendIsTheOutlookOfTotals() {
        val trend = (report(loaded(), Metric.PRECIPITATION).trend as Loadable.Ready).value

        assertEquals(Metric.PRECIPITATION, trend.metric)
        assertEquals(7, trend.days)
        assertEquals(21.0, trend.mean, 1e-9)
        assertEquals(7.0, trend.referenceMean!!, 1e-9)
        assertEquals(14.0, trend.delta!!, 1e-9)
    }

    @Test
    fun rain_summaryFollowsTheMetric() {
        val blocks = ((report(loaded(), Metric.PRECIPITATION).summary) as Loadable.Ready).value

        assertEquals(
            "October so far: much wetter than the 1961–1990 average (+100%, 26 vs 13 mm).",
            (blocks.first() as SummaryBlock.Headline).text,
        )
        assertEquals("Next 7 days: wetter than usual (21.0 vs 7.0 mm).", blocks[1].text)
    }

    @Test
    fun rain_snapshotCarriesTheMetricsSeriesAndReference() {
        val snapshot = (report(loaded(), Metric.PRECIPITATION).snapshot as Loadable.Ready).value

        assertEquals(Metric.PRECIPITATION, snapshot.metric)
        assertEquals(2.0, snapshot.series.valueOrNull(LocalDate.of(2026, 10, 1))!!, 0.0)
        assertEquals(rainClimate, snapshot.reference)
        assertEquals(3.0, snapshot.forecast!!.valueOrNull(today)!!, 0.0)
        assertEquals(today, snapshot.today)
    }

    @Test
    fun rain_forecastDaysSettingSlicesTheForecast() {
        val long = report(loaded(), Metric.PRECIPITATION, forecastDays = 14)

        assertEquals("Next 14 days (forecast)", long.rows[2].label)
        assertEquals(Loadable.Ready(28.0), long.rows[2].value) // 7 x 3 + 7 x 1
        assertEquals(14, (long.trend as Loadable.Ready).value.days)
        assertEquals(14.0, (long.trend as Loadable.Ready).value.referenceMean!!, 1e-9)
    }

    @Test
    fun rain_firstDayOfTheMonthComparesWithTheWholeMonthsNormalTotal() {
        val first = LocalDate.of(2026, 10, 1)
        val report = report(loaded(today = first, year = Loadable.Ready(year(to = LocalDate.of(2026, 9, 30)))), Metric.PRECIPITATION)

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[1].value)
        assertEquals("Historical (30y) for this month", report.rows[3].label)
        assertEquals(Loadable.Ready(31.0), report.rows[3].value) // 31 days of 1 mm
        assertEquals(Loadable.Ready(62.0), report.rows[0].value)
    }

    @Test
    fun rain_normalizedModeUsesTheSpreadOfTotals() {
        val noisy = syntheticReference { if (it.year % 2 == 0) 0.0 else 2.0 }
        val state = loaded(extras = Loadable.Ready(mapOf(Metric.PRECIPITATION to noisy)))

        val headline =
            (report(state, Metric.PRECIPITATION, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY).headline as Loadable.Ready).value

        // 15 years of 0 mm and 15 of 2 mm a day: sigma of the 13-day totals is 13 times that of the daily means
        assertTrue(headline.normalized)
        assertEquals(13 * Math.sqrt(30.0 / 29.0), headline.stdDev!!, 1e-9)
        assertEquals(13.0, headline.reference!!, 1e-9)
    }

    @Test
    fun rain_tinyNormalMakesTheHeadlineNeutral() {
        val dry = loaded(extras = Loadable.Ready(mapOf(Metric.PRECIPITATION to syntheticReference { 0.1 })))

        val report = report(dry, Metric.PRECIPITATION)

        val headline = (report.headline as Loadable.Ready).value
        assertEquals(Band.NEUTRAL, headline.band)
        assertEquals(
            "October so far: 26 mm of rain, normal for these days is under 2 mm.",
            ((report.summary as Loadable.Ready).value.first()).text,
        )
    }

    // ---- sunshine ----

    @Test
    fun sunshine_reportsDailyMeansInHours() {
        val report = report(loaded(), Metric.SUNSHINE)

        assertEquals(listOf(6.0, 6.0, 7.0, 5.0).map { Loadable.Ready(it) }, report.values())
        val headline = (report.headline as Loadable.Ready).value
        assertEquals(Metric.SUNSHINE, headline.metric)
        assertEquals(1.0, headline.delta, 1e-9)
        assertEquals(Band.WARM, headline.band)
        assertEquals(2, headline.severity)
        assertNull(headline.percent)
        val trend = (report.trend as Loadable.Ready).value
        assertEquals(7.0, trend.mean, 1e-9)
        assertEquals(2.0, trend.delta!!, 1e-9)
        assertEquals(
            "October so far: clearly sunnier than the 1961–1990 average (+1.0 h/day).",
            (((report.summary as Loadable.Ready).value).first()).text,
        )
    }

    @Test
    fun sunshine_fourteenDaysIsTheMeanOfBothWeeks() {
        val report = report(loaded(), Metric.SUNSHINE, forecastDays = 14)

        assertEquals(Loadable.Ready(5.5), report.rows[2].value)
    }

    // ---- the picture ----

    @Test
    fun picture_isAlwaysTheTemperaturesWhateverTheMetric() {
        // temperature is on its normal, rain is far above it
        listOf(Metric.TEMPERATURE, Metric.SUNSHINE, Metric.PRECIPITATION).forEach { metric ->
            assertEquals(StatusPicture.EQUAL, report(loaded(), metric).picture)
        }
        val warm = loaded(year = Loadable.Ready(year(temperature = { 20.0 }, rain = { 0.0 })))
        listOf(Metric.TEMPERATURE, Metric.SUNSHINE, Metric.PRECIPITATION).forEach { metric ->
            assertEquals(StatusPicture.WARM, report(warm, metric).picture)
        }
    }

    @Test
    fun picture_isLoadingWhileTheTemperatureReferenceLoadsEvenIfTheMetricIsReady() {
        val state = loaded(reference = Loadable.Loading)

        val report = report(state, Metric.PRECIPITATION)

        assertTrue(report.headline is Loadable.Ready)
        assertEquals(StatusPicture.LOADING, report.picture)
    }

    // ---- the extras ----

    @Test
    fun extras_stillToLoadKeepTheComparisonAndTheTrendLoading() {
        listOf(Loadable.Idle, Loadable.Loading).forEach { extras ->
            val report = report(loaded(extras = extras), Metric.PRECIPITATION)

            assertEquals(Loadable.Ready(62.0), report.rows[0].value)
            assertEquals(Loadable.Ready(26.0), report.rows[1].value)
            assertEquals(Loadable.Ready(21.0), report.rows[2].value)
            assertEquals(Loadable.Loading, report.rows[3].value)
            assertEquals(Loadable.Loading, report.headline)
            assertEquals(Loadable.Loading, report.trend)
            assertEquals(Loadable.Loading, report.summary)
            assertEquals(Loadable.Loading, report.snapshot)
            assertFalse(report.rows.any { it.showWarning })
        }
    }

    @Test
    fun extras_idleBeforeAnythingWasRequestedStayIdle() {
        val report = report(WeatherUiState(today = today), Metric.SUNSHINE)

        report.values().forEach { assertEquals(Loadable.Idle, it) }
        assertEquals(Loadable.Idle, report.headline)
        assertEquals(Loadable.Idle, report.trend)
        assertEquals(Loadable.Idle, report.snapshot)
        assertTrue((report.summary as Loadable.Ready).value.single().text.startsWith("Search for a place"))
    }

    @Test
    fun extras_neverComeAfterAFailedTemperatureReference() {
        val report = report(loaded(reference = Loadable.Failed(FailKind.NETWORK), extras = Loadable.Idle), Metric.SUNSHINE)

        assertEquals(Loadable.Failed(FailKind.NETWORK), report.rows[3].value)
        assertEquals(Loadable.Failed(FailKind.NETWORK), report.headline)
        assertEquals(Loadable.Ready(6.0), report.rows[1].value)
    }

    @Test
    fun extras_failedAreShownWhereTheyAreMissing() {
        val report = report(loaded(extras = Loadable.Failed(FailKind.RATE_LIMITED)), Metric.PRECIPITATION)

        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), report.rows[3].value)
        assertEquals(Loadable.Failed(FailKind.RATE_LIMITED), report.headline)
        assertEquals(Loadable.Ready(26.0), report.rows[1].value)
        // the outlook has no normal, but still its total
        assertNull((report.trend as Loadable.Ready).value.delta)
        assertEquals(21.0, (report.trend as Loadable.Ready).value.mean, 1e-9)
        assertNull((report.snapshot as Loadable.Ready).value.reference)
        assertTrue((report.summary as Loadable.Ready).value.single().text.contains("reference is not available"))
        // the picture is still the temperature's
        assertEquals(StatusPicture.EQUAL, report.picture)
    }

    @Test
    fun extras_aMetricWithoutDataIsNotAvailable() {
        val state = loaded(extras = Loadable.Ready(mapOf(Metric.SUNSHINE to sunClimate)))

        val report = report(state, Metric.PRECIPITATION)

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[3].value)
        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.headline)
        // the other metric of the same extras is fine
        assertTrue(report(state, Metric.SUNSHINE).headline is Loadable.Ready)
    }

    // ---- the series ----

    @Test
    fun aSeriesWithoutTheMetricIsNotAvailable() {
        val onlyTemperature = MetricSeries(mapOf(Metric.TEMPERATURE to seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1)) { 10.0 }))

        val report = report(loaded(year = Loadable.Ready(onlyTemperature)), Metric.SUNSHINE)

        assertEquals(Loadable.Unavailable(Reason.NOT_AVAILABLE), report.rows[0].value)
        assertEquals(Loadable.Unavailable(Reason.NO_DATA_YET), report.rows[1].value)
        assertTrue(report.rows[1].showWarning)
        assertFalse((report.snapshot as Loadable.Ready).value.series.hasValues())
    }

    @Test
    fun missingDays_doNotShrinkTheRainTotals() {
        val gaps = year(rain = { if (it.monthValue == 10 && it.dayOfMonth in 5..6) null else 2.0 })

        val report = report(loaded(year = Loadable.Ready(gaps)), Metric.PRECIPITATION)

        assertEquals(Loadable.Ready(26.0), report.rows[1].value)
        assertEquals(11, report.dayCount)
    }

    // ---- temperature stays as it was ----

    @Test
    fun temperature_isTheDefaultAndIgnoresTheExtras() {
        val withExtras = buildReport(loaded(), 7, TemperatureComparisonMode.ABSOLUTE_DELTA, HistoricReference.CLASSIC)
        val without =
            buildReport(loaded(extras = Loadable.Idle), 7, TemperatureComparisonMode.ABSOLUTE_DELTA, HistoricReference.CLASSIC)
        val explicit = report(loaded(), Metric.TEMPERATURE)

        // the series have no equality, so compare what the report is made of
        listOf(without, explicit).forEach {
            assertEquals(withExtras.rows, it.rows)
            assertEquals(withExtras.headline, it.headline)
            assertEquals(withExtras.trend, it.trend)
            assertEquals(withExtras.summary, it.summary)
            assertEquals(withExtras.picture, it.picture)
            assertEquals(withExtras.dayCount, it.dayCount)
        }
        assertEquals(Metric.TEMPERATURE, withExtras.metric)
        assertEquals(listOf(10.0, 10.0, 12.0, 10.0).map { Loadable.Ready(it) }, withExtras.values())
        val headline = (withExtras.headline as Loadable.Ready).value
        assertEquals(Metric.TEMPERATURE, headline.metric)
        assertEquals(0.0, headline.delta, 1e-9)
    }

    @Test
    fun extras_readyWhileTheYearLoads_keepTheHistoricRowAndTheHeadlineLoading() {
        val report = report(loaded(year = Loadable.Loading), Metric.PRECIPITATION)

        assertEquals(Loadable.Loading, report.rows[0].value)
        assertEquals(Loadable.Loading, report.rows[1].value)
        assertEquals(Loadable.Ready(21.0), report.rows[2].value)
        // it must not flip to the whole month's normal while the same days are still unknown
        assertEquals(Loadable.Loading, report.rows[3].value)
        assertEquals(Loadable.Loading, report.headline)
        assertEquals(Loadable.Loading, report.summary)
        assertEquals(Loadable.Loading, report.snapshot)
        assertEquals(14.0, (report.trend as Loadable.Ready).value.delta!!, 1e-9)
    }
}
