package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sqrt

private val rain = Metric.PRECIPITATION
private val sun = Metric.SUNSHINE

/** The analysis of sunshine and rain: means stay means, rain becomes totals of the compared days. */
class MetricAnalysisTest {

    private val today = LocalDate.of(2026, 10, 14) // last complete day: Oct 13

    private fun yearSeries(valueOf: (LocalDate) -> Double?) =
        seriesOf(LocalDate.of(2026, 1, 1), today.minusDays(1), valueOf)

    private fun constant(value: Double) = syntheticReference { value }

    // ---- month to date ----

    @Test
    fun monthToDate_rainIsTheTotalOfTheSameDays() {
        val result = monthToDate(yearSeries { 3.0 }, constant(2.0), today, rain)!!

        assertEquals(39.0, result.observedMean, 1e-9)
        assertEquals(26.0, result.referenceMean!!, 1e-9)
        assertEquals(13, result.daysObserved)
        assertEquals(13, result.toDay)
        assertEquals(rain, result.metric)
    }

    @Test
    fun monthToDate_missingDaysDoNotShrinkTheTotal() {
        val series = yearSeries { if (it.monthValue == 10 && it.dayOfMonth in 5..6) null else 3.0 }

        val result = monthToDate(series, constant(2.0), today, rain)!!

        // mean of the 11 days times the 13 days of the window
        assertEquals(39.0, result.observedMean, 1e-9)
        assertEquals(11, result.daysObserved)
    }

    @Test
    fun monthToDate_rainSpreadIsTheSpreadOfTotals() {
        val climate = syntheticReference { if (it.year % 2 == 0) 1.0 else 3.0 }

        val result = monthToDate(yearSeries { 3.0 }, climate, today, rain)!!

        // 15 years of 1 mm and 15 of 3 mm a day: sigma of the daily means times the 13 days
        assertEquals(13 * sqrt(30.0 / 29.0), result.referenceStdDev!!, 1e-9)
        assertEquals(26.0, result.referenceMean!!, 1e-9)
    }

    @Test
    fun monthToDate_sunshineStaysADailyMean() {
        val result = monthToDate(yearSeries { 6.0 }, constant(5.0), today, sun)!!

        assertEquals(6.0, result.observedMean, 1e-9)
        assertEquals(5.0, result.referenceMean!!, 1e-9)
        assertEquals(0.0, result.referenceStdDev!!, 1e-9)
        assertEquals(sun, result.metric)
    }

    @Test
    fun monthToDate_temperatureIsTheDefaultAndStaysAMean() {
        val series = yearSeries { 20.0 }
        val climate = syntheticReference()

        val default = monthToDate(series, climate, today)!!

        assertEquals(monthToDate(series, climate, today, Metric.TEMPERATURE), default)
        assertEquals(20.0, default.observedMean, 1e-9)
        assertEquals(10.07, default.referenceMean!!, 1e-4)
    }

    @Test
    fun monthToDate_withoutAReferenceStillHasTheObservedTotal() {
        val result = monthToDate(yearSeries { 3.0 }, null, today, rain)!!

        assertEquals(39.0, result.observedMean, 1e-9)
        assertNull(result.referenceMean)
        assertNull(result.referenceStdDev)
    }

    // ---- year ----

    @Test
    fun yearToDate_rainTotalsOfTheComparedDaysWithPercent() {
        val series = yearSeries { 3.0 }
        // the leap day of the reference does not enter the 28 days of February 2026
        val climate = syntheticReference { if (it.monthValue == 2 && it.dayOfMonth == 29) 100.0 else 2.0 }

        val months = yearToDate(series, climate, today, rain)

        val january = months.first()
        assertEquals(93.0, january.observedMean!!, 1e-9)
        assertEquals(62.0, january.referenceMean!!, 1e-9)
        val february = months[1]
        assertEquals(84.0, february.observedMean!!, 1e-9)
        assertEquals(56.0, february.referenceMean!!, 1e-9)
        assertEquals(28.0, february.delta!!, 1e-9)
        assertEquals(50.0, february.deltaPercent!!, 1e-9)
        val october = months.last()
        assertTrue(october.partial)
        assertEquals(39.0, october.observedMean!!, 1e-9)
        assertEquals(26.0, october.referenceMean!!, 1e-9)
        assertEquals(13, october.daysObserved)
        assertTrue(months.all { it.metric == rain })
    }

    @Test
    fun yearToDate_sunshineKeepsMeansAndHasNoPercent() {
        val months = yearToDate(yearSeries { 6.0 }, constant(5.0), today, sun)

        months.forEach {
            assertEquals(6.0, it.observedMean!!, 1e-9)
            assertEquals(5.0, it.referenceMean!!, 1e-9)
            assertEquals(1.0, it.delta!!, 1e-9)
            assertNull(it.deltaPercent)
        }
    }

    @Test
    fun yearToDate_sunshineTotalsAreHoursOverTheComparedDays() {
        val months = yearToDate(yearSeries { 6.0 }, constant(5.0), today, sun)

        val february = months[1]
        assertEquals(168.0, february.observedTotal!!, 1e-9)
        assertEquals(140.0, february.referenceTotal!!, 1e-9)
        assertEquals(28.0, february.totalDelta!!, 1e-9)
        assertEquals(20.0, february.totalPercent!!, 1e-9)
        val october = months.last()
        assertEquals(13, october.days)
        assertEquals(78.0, october.observedTotal!!, 1e-9)
        assertEquals(13.0, october.totalDelta!!, 1e-9)
    }

    @Test
    fun yearToDate_aRunningMonthKeepsTheNormalOfTheWholeMonth() {
        val months = yearToDate(yearSeries { 6.0 }, constant(5.0), today, sun)

        val october = months.last()
        assertEquals(13, october.days)
        assertEquals(65.0, october.referenceTotal!!, 1e-9)
        assertEquals(31, october.monthDays)
        assertEquals(155.0, october.referenceMonthTotal!!, 1e-9)
        // a finished month compares its whole length either way
        val february = months[1]
        assertEquals(february.referenceTotal!!, february.referenceMonthTotal!!, 1e-9)

        val rainMonths = yearToDate(yearSeries { 3.0 }, constant(2.0), today, rain)
        assertEquals(26.0, rainMonths.last().referenceTotal!!, 1e-9)
        assertEquals(62.0, rainMonths.last().referenceMonthTotal!!, 1e-9)
    }

    @Test
    fun upcomingMonths_carryOnlyTheNormalOfTheWholeMonth() {
        val upcoming = upcomingMonths(constant(2.0), today, rain)

        assertEquals(listOf(11, 12), upcoming.map { it.month })
        assertEquals(listOf(60.0, 62.0), upcoming.map { it.referenceMonthTotal!! })
        upcoming.forEach {
            assertNull(it.observedTotal)
            assertNull(it.delta)
            assertEquals(0, it.daysObserved)
        }
        assertEquals(listOf(150.0, 155.0), upcomingMonths(constant(5.0), today, sun).map { it.referenceMonthTotal!! })
        // they never count as compared months
        assertEquals(10, summarizeYear(yearToDate(yearSeries { 3.0 }, constant(2.0), today, rain) + upcoming).comparedMonths)
    }

    @Test
    fun upcomingMonths_areEmptyForTemperatureWithoutAReferenceAndAtTheEndOfTheYear() {
        assertTrue(upcomingMonths(constant(5.0), today).isEmpty())
        assertTrue(upcomingMonths(null, today, rain).isEmpty())
        assertTrue(upcomingMonths(constant(2.0), LocalDate.of(2027, 1, 1), rain).isEmpty())
    }

    @Test
    fun monthlyAnomaly_totalsOfRainAreItsValuesAndTemperatureHasNone() {
        val wet = MonthlyAnomaly(1, 93.0, 62.0, null, 31, false, rain)
        assertEquals(93.0, wet.observedTotal!!, 1e-9)
        assertEquals(31.0, wet.totalDelta!!, 1e-9)
        assertEquals(50.0, wet.totalPercent!!, 1e-9)

        val warm = MonthlyAnomaly(1, 12.0, 10.0, null, 31, false)
        assertNull(warm.observedTotal)
        assertNull(warm.totalDelta)
        assertNull(warm.totalPercent)

        // under an hour of normal sunshine there is no percent
        assertNull(MonthlyAnomaly(1, 0.1, 0.02, null, 31, false, sun).totalPercent)
    }

    @Test
    fun monthlyAnomaly_percentNeedsANormalAboveZero() {
        assertNull(MonthlyAnomaly(1, 5.0, 0.0, null, 31, false, rain).deltaPercent)
        assertNull(MonthlyAnomaly(1, null, 5.0, null, 0, false, rain).deltaPercent)
        assertNull(MonthlyAnomaly(1, 12.0, 10.0, null, 31, false).deltaPercent)
        assertEquals(-50.0, MonthlyAnomaly(1, 5.0, 10.0, null, 31, false, rain).deltaPercent!!, 1e-9)
    }

    @Test
    fun yearSummary_rainAddsTheMonthsUp() {
        val months = yearToDate(yearSeries { 3.0 }, constant(2.0), today, rain)

        val summary = summarizeYear(months)

        // every day is 1 mm over its normal: 273 days up to Sep 30 and 13 of October
        assertEquals(286.0, summary.delta!!, 1e-9)
        assertEquals(50.0, summary.deltaPercent!!, 1e-9)
        assertEquals(286.0, summary.totalDelta!!, 1e-9)
        assertEquals(10, summary.warmerMonths)
        assertEquals(10, summary.comparedMonths)
    }

    @Test
    fun yearSummary_sunshineIsTheDayWeightedMeanWithoutPercent() {
        val months =
            listOf(
                MonthlyAnomaly(1, 7.0, 5.0, 1.0, 30, false, sun), // +2 over 30 days
                MonthlyAnomaly(2, 4.0, 5.0, 1.0, 10, true, sun), // -1 over 10 days
            )

        val summary = summarizeYear(months)

        assertEquals((2.0 * 30 - 1.0 * 10) / 40, summary.delta!!, 1e-9)
        assertEquals(2.0 * 30 - 1.0 * 10, summary.totalDelta!!, 1e-9)
        assertNull(summary.deltaPercent)
        assertEquals(1, summary.warmerMonths)
    }

    // ---- month view ----

    private fun snapshot(
        metric: Metric,
        reference: ReferenceClimate?,
        valueOf: (LocalDate) -> Double? = { 3.0 },
    ) = ClimateSnapshot(yearSeries(valueOf), reference, today, metric = metric)

    @Test
    fun dayPoints_keepDailyValuesForEveryMetric() {
        val points = dayPoints(snapshot(rain, constant(2.0)), MonthWindow.THIS_MONTH)

        assertEquals(3.0, points.first().observed!!, 0.0)
        assertEquals(2.0, points.first().referenceMean!!, 1e-9)
        assertEquals(2.0, points.first().band!!.mean, 1e-9)
    }

    @Test
    fun dayPoints_bandOfRainAndSunshineNeverGoesBelowZero() {
        // 6 of 30 years have 5 per day, the others none: mean 1, spread about 2
        val spiky = syntheticReference { if (it.year % 5 == 0) 5.0 else 0.0 }

        listOf(rain, sun).forEach { metric ->
            val band = dayPoints(snapshot(metric, spiky), MonthWindow.THIS_MONTH).first().band!!

            assertTrue(band.mean - band.stdDev < 0.0)
            assertEquals(0.0, band.floor!!, 0.0)
            assertEquals(0.0, band.lower, 0.0)
            assertEquals(band.mean + band.stdDev, band.upper, 1e-9)
        }
    }

    @Test
    fun dayPoints_bandOfTemperatureIsUntouched() {
        val spiky = syntheticReference { if (it.year % 5 == 0) 5.0 else -3.0 }

        val band = dayPoints(snapshot(Metric.TEMPERATURE, spiky), MonthWindow.THIS_MONTH).first().band!!

        assertNull(band.floor)
        assertEquals(band.mean - band.stdDev, band.lower, 1e-12)
        assertTrue(band.lower < 0.0)
    }

    @Test
    fun dayPoints_markTheDaysThatAreOver() {
        val points = dayPoints(snapshot(rain, constant(2.0)) { if (it.dayOfMonth == 5) null else 3.0 }, MonthWindow.THIS_MONTH)

        assertEquals(13, points.count { it.elapsed })
        // a missing day is over without a value
        assertTrue(points.first { it.date.dayOfMonth == 5 }.elapsed)
        assertNull(points.first { it.date.dayOfMonth == 5 }.observed)
        assertFalse(points.first { it.date.dayOfMonth == 14 }.elapsed)
    }

    @Test
    fun summarize_rainTotalsAgreeWithTheMonthToDate() {
        val snapshot = snapshot(rain, constant(2.0)) { if (it.monthValue == 10 && it.dayOfMonth in 5..6) null else 3.0 }

        val summary = summarize(dayPoints(snapshot, MonthWindow.THIS_MONTH), rain)
        val monthToDate = monthToDate(snapshot.series, snapshot.reference, today, rain)!!

        assertEquals(11, summary.observedDays)
        assertEquals(monthToDate.observedMean, summary.observedMean!!, 1e-9)
        assertEquals(monthToDate.referenceMean!!, summary.referenceMean!!, 1e-9)
        assertEquals(13.0, summary.delta!!, 1e-9)
        assertEquals(50.0, summary.deltaPercent!!, 1e-9)
        // days above and below the normal
        assertEquals(11, summary.warmerDays)
        assertEquals(0, summary.colderDays)
    }

    @Test
    fun summarize_sunshineStaysAMean() {
        val snapshot = snapshot(sun, constant(5.0)) { 6.0 }

        val summary = summarize(dayPoints(snapshot, MonthWindow.THIS_MONTH), sun)

        assertEquals(6.0, summary.observedMean!!, 1e-9)
        assertEquals(5.0, summary.referenceMean!!, 1e-9)
        assertEquals(78.0, summary.observedTotal!!, 1e-9)
        assertEquals(65.0, summary.referenceTotal!!, 1e-9)
        assertNull(summary.deltaPercent)
    }

    @Test
    fun summarize_temperatureIsTheDefault() {
        val points = dayPoints(snapshot(Metric.TEMPERATURE, syntheticReference()) { 20.0 }, MonthWindow.THIS_MONTH)

        assertEquals(summarize(points, Metric.TEMPERATURE), summarize(points))
        assertEquals(20.0, summarize(points).observedMean!!, 1e-9)
    }

    // ---- outlook ----

    private fun forecast(
        start: LocalDate = today,
        days: Int = 14,
        valueOf: (LocalDate) -> Double? = { 3.0 },
    ) = seriesOf(start, start.plusDays(days - 1L), valueOf)

    @Test
    fun outlook_rainIsTheTotalOfTheDaysAgainstTheNormalTotal() {
        val outlook = forecastOutlook(forecast(), constant(1.0), days = 7, metric = rain)!!

        assertEquals(7, outlook.days)
        assertEquals(21.0, outlook.mean, 1e-9)
        assertEquals(7.0, outlook.referenceMean!!, 1e-9)
        assertEquals(14.0, outlook.delta!!, 1e-9)
        assertEquals(200.0, outlook.deltaPercent!!, 1e-9)
        assertEquals(rain, outlook.metric)
    }

    @Test
    fun outlook_rainUsesTheDaysThatHaveAValue() {
        val outlook = forecastOutlook(forecast { if (it.dayOfMonth in 15..16) null else 3.0 }, constant(1.0), 7, rain)!!

        assertEquals(5, outlook.days)
        assertEquals(15.0, outlook.mean, 1e-9)
        assertEquals(5.0, outlook.referenceMean!!, 1e-9)
    }

    @Test
    fun outlook_acrossALeapDay() {
        val start = LocalDate.of(2028, 2, 27)

        val outlook = forecastOutlook(forecast(start, days = 4), constant(2.0), 4, rain)!!

        assertEquals(12.0, outlook.mean, 1e-9)
        assertEquals(8.0, outlook.referenceMean!!, 1e-9)
    }

    @Test
    fun outlook_sunshineStaysAMeanInTheLongerPeriodToo() {
        val outlook = forecastOutlook(forecast { 6.0 }, constant(5.0), days = 14, metric = sun)!!

        assertEquals(14, outlook.days)
        assertEquals(6.0, outlook.mean, 1e-9)
        assertEquals(5.0, outlook.referenceMean!!, 1e-9)
        assertNull(outlook.deltaPercent)
    }

    @Test
    fun outlook_withoutReferenceHasNoPercent() {
        val outlook = forecastOutlook(forecast(), null, 7, rain)!!

        assertEquals(21.0, outlook.mean, 1e-9)
        assertNull(outlook.delta)
        assertNull(outlook.deltaPercent)
    }

    @Test
    fun outlook_temperatureIsTheDefault() {
        val outlook = forecastOutlook(forecast { 8.0 }, syntheticReference(), 7)!!

        assertEquals(Metric.TEMPERATURE, outlook.metric)
        assertEquals(8.0, outlook.mean, 1e-9)
        assertNull(outlook.deltaPercent)
    }

    // ---- snapshot helpers ----

    @Test
    fun snapshotHelpers_alwaysUseTheMetricOfTheSnapshot() {
        val snapshot =
            ClimateSnapshot(
                series = yearSeries { 3.0 },
                reference = constant(2.0),
                today = today,
                forecast = forecast(),
                metric = rain,
            )

        val month = snapshot.monthToDate()!!
        val months = snapshot.months()
        val points = dayPoints(snapshot, MonthWindow.THIS_MONTH, forecastDays = 7)
        val summary = snapshot.summarize(points)
        val outlook = snapshot.outlook(7)!!

        // totals, not means
        assertEquals(39.0, month.observedMean, 1e-9)
        assertEquals(26.0, month.referenceMean!!, 1e-9)
        assertEquals(rain, month.metric)
        assertEquals(93.0, months.first().observedMean!!, 1e-9)
        assertEquals(50.0, months.last().deltaPercent!!, 1e-9)
        assertEquals(39.0, summary.observedMean!!, 1e-9)
        assertEquals(rain, summary.metric)
        assertEquals(50.0, summary.deltaPercent!!, 1e-9)
        assertEquals(21.0, outlook.mean, 1e-9)
        assertEquals(rain, outlook.metric)
        assertEquals(50.0, outlook.deltaPercent!!, 1e-9)
        // and they feed the wording
        val text =
            buildSummary(
                SummaryInput(
                    "October", month.observedMean, month.referenceMean, "1961–1990", month.referenceStdDev,
                    TemperatureComparisonMode.ABSOLUTE_DELTA, outlook, month.daysObserved, snapshot.metric,
                ),
            )
        assertEquals("October so far: clearly wetter than the 1961–1990 average (+50%, 39 mm vs 26 mm).", text[0].text)
        assertEquals("Next 7 days: wetter than usual (21 mm vs 14 mm).", text[1].text)
    }

    @Test
    fun snapshotHelpers_keepTemperatureAMean() {
        val snapshot = ClimateSnapshot(yearSeries { 20.0 }, syntheticReference(), today, forecast { 8.0 })

        assertEquals(20.0, snapshot.monthToDate()!!.observedMean, 1e-9)
        assertEquals(20.0, snapshot.months().last().observedMean!!, 1e-9)
        assertEquals(8.0, snapshot.outlook(7)!!.mean, 1e-9)
    }

    // ---- percent guard ----

    @Test
    fun percent_isNullForANormalBelowTwoMillimetresEverywhere() {
        assertNull(MonthlyAnomaly(1, 3.0, 1.9, null, 31, false, rain).deltaPercent)
        assertEquals(50.0, MonthlyAnomaly(1, 3.0, 2.0, null, 31, false, rain).deltaPercent!!, 1e-9)
        assertNull(WindowSummary(5, 3.0, 1.9, 1, 0, rain).deltaPercent)
        assertNull(ForecastOutlook(7, 3.0, 1.9, rain).deltaPercent)
        val summary = summarizeYear(listOf(MonthlyAnomaly(1, 3.0, 0.3, null, 31, false, rain)))
        assertEquals(2.7, summary.delta!!, 1e-9)
        assertNull(summary.deltaPercent)
    }

    @Test
    fun yearSummary_rainIgnoresMonthsWithoutObservedDays() {
        val months =
            listOf(
                MonthlyAnomaly(1, 93.0, 62.0, null, 31, false, rain),
                MonthlyAnomaly(2, null, 56.0, null, 0, false, rain),
                MonthlyAnomaly(3, 10.0, 5.0, null, 0, false, rain),
            )

        val summary = summarizeYear(months)

        assertEquals(31.0, summary.delta!!, 1e-9)
        assertEquals(50.0, summary.deltaPercent!!, 1e-9)
        assertEquals(1, summary.comparedMonths)
        assertNull(summarizeYear(months.drop(1)).delta)
        assertNull(summarizeYear(months.drop(1)).deltaPercent)
    }

    // ---- leap year ----

    private fun leapSeries(to: LocalDate) = seriesOf(LocalDate.of(2028, 1, 1), to) { 3.0 }

    @Test
    fun leapYear_theDayAfterTheLeapDayHasNoMonthToDate() {
        val leapDay = LocalDate.of(2028, 2, 29)

        // yesterday is Feb 28: 28 days of the window
        val onLeapDay = monthToDate(leapSeries(leapDay.minusDays(1)), constant(2.0), leapDay, rain)!!
        assertEquals(28, onLeapDay.toDay)
        assertEquals(84.0, onLeapDay.observedMean, 1e-9)
        assertEquals(56.0, onLeapDay.referenceMean!!, 1e-9)
        // yesterday is Feb 29 but today starts a month
        assertNull(monthToDate(leapSeries(leapDay), constant(2.0), LocalDate.of(2028, 3, 1), rain))
    }

    @Test
    fun leapYear_februaryHas29DaysOnceItIsOver() {
        val running = yearToDate(leapSeries(LocalDate.of(2028, 2, 28)), constant(2.0), LocalDate.of(2028, 2, 29), rain)
        val finished = yearToDate(leapSeries(LocalDate.of(2028, 2, 29)), constant(2.0), LocalDate.of(2028, 3, 1), rain)

        assertEquals(listOf(1, 2), running.map { it.month })
        assertTrue(running.last().partial)
        assertEquals(28, running.last().daysObserved)
        assertEquals(84.0, running.last().observedMean!!, 1e-9)
        assertEquals(56.0, running.last().referenceMean!!, 1e-9)

        assertEquals(listOf(1, 2), finished.map { it.month })
        assertFalse(finished.last().partial)
        assertEquals(29, finished.last().daysObserved)
        assertEquals(87.0, finished.last().observedMean!!, 1e-9)
        // non-leap reference years clip the window to 28 days, the mean is still 2 mm a day
        assertEquals(58.0, finished.last().referenceMean!!, 1e-9)
    }
}
