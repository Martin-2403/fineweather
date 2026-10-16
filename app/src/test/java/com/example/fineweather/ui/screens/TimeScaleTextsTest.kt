package com.example.fineweather.ui.screens

import com.example.fineweather.domain.DailyStats
import com.example.fineweather.domain.DayPoint
import com.example.fineweather.domain.ForecastOutlook
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MonthlyAnomaly
import com.example.fineweather.domain.WindowSummary
import com.example.fineweather.domain.YearSummary
import com.example.fineweather.domain.summarizeYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The sentences of the Month and Year views per metric. */
class TimeScaleTextsTest {
    private val ref = "1991–2020"

    private fun summary(
        metric: Metric,
        days: Int,
        observed: Double?,
        reference: Double?,
        warmer: Int = 0,
        colder: Int = 0,
    ) = WindowSummary(
        days,
        observed,
        reference,
        warmer,
        colder,
        metric,
        observedTotal = if (metric == Metric.SUNSHINE) observed?.times(days) else null,
        referenceTotal = if (metric == Metric.SUNSHINE) reference?.times(days) else null,
    )

    private fun outlook(
        metric: Metric,
        mean: Double,
        reference: Double?,
        days: Int = 7,
    ) = ForecastOutlook(days, mean, reference, metric)

    private fun day(
        dayOfMonth: Int,
        observed: Double?,
    ) = DayPoint(LocalDate.of(2026, 10, dayOfMonth), observed, null, DailyStats(2.0, 1.0), 2.0)

    // ---- month summary ----

    @Test
    fun monthSummary_temperatureIsUnchanged() {
        val s = summary(Metric.TEMPERATURE, 13, 12.04, 8.0)
        assertEquals("13 days averaged 12.0°C, +4.0°C compared with the same days of 1961–1990.", monthSummaryText(s, "1961–1990"))
        assertEquals("1 day averaged 12.0°C.", monthSummaryText(summary(Metric.TEMPERATURE, 1, 12.0, null), "1961–1990"))
    }

    @Test
    fun monthSummary_sunshine() {
        assertEquals(
            "13 days: 68.9 h total, +10.4 h compared with the 58.5 h average for the same days of $ref.",
            monthSummaryText(summary(Metric.SUNSHINE, 13, 5.3, 4.5), ref),
        )
        assertEquals(
            "13 days: 52.0 h total, -13.0 h compared with the 65.0 h average for the same days of $ref.",
            monthSummaryText(summary(Metric.SUNSHINE, 13, 4.0, 5.0), ref),
        )
    }

    @Test
    fun monthSummary_sunshineNeutralAndWithoutReference() {
        assertEquals(
            "13 days: 94.9 h total, in line with the 94.9 h average for the same days of $ref.",
            monthSummaryText(summary(Metric.SUNSHINE, 13, 7.3, 7.3), ref),
        )
        assertEquals("1 day: 7.3 h total.", monthSummaryText(summary(Metric.SUNSHINE, 1, 7.3, null), ref))
    }

    @Test
    fun monthSummary_rainGivesTotalPercentAndBothValues() {
        assertEquals(
            "13 days: 41 mm in total, +85% (41 vs 22 mm) compared with the same days of $ref.",
            monthSummaryText(summary(Metric.PRECIPITATION, 13, 41.3, 22.3), ref),
        )
        assertEquals(
            "30 days: 36 mm in total, -47% (36 vs 67 mm) compared with the same days of $ref.",
            monthSummaryText(summary(Metric.PRECIPITATION, 30, 35.6, 67.0), ref),
        )
        assertEquals(
            "5 days: 7.5 mm in total, +50% (7.5 vs 5.0 mm) compared with the same days of $ref.",
            monthSummaryText(summary(Metric.PRECIPITATION, 5, 7.5, 5.0), ref),
        )
    }

    @Test
    fun monthSummary_rainWithALowNormalUsesTheMillimetreDelta() {
        assertEquals(
            "3 days: 3.2 mm in total, +2.7 mm compared with the same days of $ref.",
            monthSummaryText(summary(Metric.PRECIPITATION, 3, 3.2, 0.5), ref),
        )
    }

    @Test
    fun monthSummary_rainNeutralAndWithoutReference() {
        assertEquals(
            "13 days: 41 mm in total, in line with the same days of $ref.",
            monthSummaryText(summary(Metric.PRECIPITATION, 13, 41.2, 41.0), ref),
        )
        assertEquals("13 days: 41 mm in total.", monthSummaryText(summary(Metric.PRECIPITATION, 13, 41.2, null), ref))
        assertEquals("1 day: 0.0 mm in total.", monthSummaryText(summary(Metric.PRECIPITATION, 1, 0.0, null), ref))
    }

    // ---- captions ----

    @Test
    fun monthCaption_countsTemperatureDaysButNotSunshineDays() {
        assertEquals(
            "9 days warmer and 1 day colder than the typical day.",
            monthCaptionText(summary(Metric.TEMPERATURE, 10, 1.0, 1.0, warmer = 9, colder = 1), emptyList()),
        )
        assertNull(monthCaptionText(summary(Metric.SUNSHINE, 13, 7.0, 7.0, warmer = 8, colder = 5), emptyList()))
    }

    @Test
    fun monthCaption_sunshineWithoutReferenceHasNone() {
        assertNull(monthCaptionText(summary(Metric.SUNSHINE, 13, 7.0, null), emptyList()))
    }

    @Test
    fun rainDays_countsDaysFromOneMillimetreAndFindsTheWettest() {
        val points = listOf(day(1, 0.0), day(2, 0.9), day(3, 1.0), day(4, 12.4), day(5, null), day(6, 7.0), day(7, 12.4))

        val rain = rainDays(points)

        assertEquals(4, rain.rainyDays)
        // the first of two equal maxima
        assertEquals(LocalDate.of(2026, 10, 4) to 12.4, rain.wettest)
    }

    @Test
    fun rainDays_forecastDaysAreNotCounted() {
        val forecastOnly = DayPoint(LocalDate.of(2026, 10, 20), null, 30.0, null, null)

        assertEquals(RainDays(0, null), rainDays(listOf(day(1, 0.2), forecastOnly)))
    }

    @Test
    fun rainCaption_namesTheRainyDaysAndTheWettest() {
        val s = summary(Metric.PRECIPITATION, 13, 41.0, 22.0)
        val points = listOf(day(7, 3.0), day(8, 12.0), day(9, 1.4), day(10, 0.0), day(11, 5.0), day(12, 2.0))

        assertEquals(
            "5 rainy days (>= 1 mm), wettest: 12 mm on Oct 8.",
            monthCaptionText(s, points),
        )
        assertEquals("1 rainy day (>= 1 mm), wettest: 7.5 mm on Oct 3.", rainCaption(RainDays(1, LocalDate.of(2026, 10, 3) to 7.5)))
        assertEquals("No rainy days (>= 1 mm).", monthCaptionText(s, listOf(day(1, 0.0), day(2, 0.5))))
    }

    // ---- forecast ----

    @Test
    fun forecastSummary_temperatureIsUnchanged() {
        assertEquals(
            "Forecast: the next 7 days average 14.0°C, +6.0°C compared with normal for those days.",
            forecastSummaryText(outlook(Metric.TEMPERATURE, 14.0, 8.0)),
        )
        assertEquals("Forecast: the next 7 days average 14.0°C.", forecastSummaryText(outlook(Metric.TEMPERATURE, 14.0, null)))
    }

    @Test
    fun forecastSummary_sunshine() {
        assertEquals(
            "Forecast: the next 7 days average 6.1 h/day, +1.2 h/day compared with normal for those days.",
            forecastSummaryText(outlook(Metric.SUNSHINE, 6.1, 4.9)),
        )
        assertEquals(
            "Forecast: the next 7 days average 6.1 h/day, in line with normal for those days.",
            forecastSummaryText(outlook(Metric.SUNSHINE, 6.1, 6.08)),
        )
        assertEquals("Forecast: the next 3 days average 6.1 h/day.", forecastSummaryText(outlook(Metric.SUNSHINE, 6.1, null, days = 3)))
    }

    @Test
    fun forecastSummary_rain() {
        assertEquals(
            "Forecast: about 25 mm of rain in the next 7 days, vs 14 mm normal for those days.",
            forecastSummaryText(outlook(Metric.PRECIPITATION, 25.2, 14.4)),
        )
        assertEquals(
            "Forecast: no rain in the next 7 days, vs 14 mm normal for those days.",
            forecastSummaryText(outlook(Metric.PRECIPITATION, 0.0, 14.4)),
        )
        assertEquals("Forecast: about 4.5 mm of rain in the next 7 days.", forecastSummaryText(outlook(Metric.PRECIPITATION, 4.5, null)))
    }

    // ---- legend ----

    @Test
    fun legend_temperatureExplainsLineDotsBandAndForecast() {
        val entries = chartLegendEntries("1961–1990")
        assertEquals(entries, chartLegendEntries("1961–1990", Metric.TEMPERATURE))
        assertEquals(
            listOf(
                LegendSymbol.DAILY_LINE,
                LegendSymbol.WARM_COLD_DOTS,
                LegendSymbol.TYPICAL_BAND,
                LegendSymbol.TEMPERATURE_FORECAST,
                null,
            ),
            entries.map { it.symbol },
        )
        val text = chartLegendText("1961–1990")
        assertTrue(text.startsWith("Daily mean."))
        assertTrue(text.contains("Typical range of 1961–1990 (±1 standard deviation)"))
        assertTrue(text.contains("rough guide"))
    }

    @Test
    fun legend_sunshineExplainsDailyBarsAccumulatedLinesForecastAndTheDataSource() {
        val entries = chartLegendEntries(ref, Metric.SUNSHINE)

        assertEquals(
            listOf(
                LegendSymbol.DAILY_BARS,
                LegendSymbol.ACCUMULATED_LINE,
                LegendSymbol.AVERAGE_LINE,
                LegendSymbol.BAR_FORECAST,
                null,
                null,
            ),
            entries.map { it.symbol },
        )
        val text = chartLegendText(ref, Metric.SUNSHINE)
        assertTrue(text.contains("Hours of sunshine each day."))
        assertTrue(text.contains("Accumulated sunshine hours (right axis)."))
        assertTrue(text.contains("Historical average for this period, accumulated at an even daily pace (right axis)."))
        assertTrue(text.contains("The forecast: lighter outlined bars"))
        assertTrue(text.contains("rain especially: treat days beyond about a week as a rough guide"))
        assertTrue(text.contains("modelled from reanalysis data (ERA5), not measured at a station"))
        assertFalse(text.contains("standard deviation"))
    }

    @Test
    fun legend_rainExplainsDailyBarsAndAccumulatedLines() {
        val entries = chartLegendEntries(ref, Metric.PRECIPITATION)

        assertEquals(
            listOf(LegendSymbol.DAILY_BARS, LegendSymbol.ACCUMULATED_LINE, LegendSymbol.AVERAGE_LINE, LegendSymbol.BAR_FORECAST, null),
            entries.map { it.symbol },
        )
        val text = chartLegendText(ref, Metric.PRECIPITATION)
        assertTrue(text.contains("Rain per day in mm."))
        assertTrue(text.contains("A thin stub is a dry day"))
        assertTrue(text.contains("Accumulated rain in this period (right axis)."))
        assertTrue(text.contains("Historical average for this period"))
        assertTrue(text.contains("rough guide"))
        assertFalse(text.contains("ERA5"))
    }

    // ---- descriptions ----

    @Test
    fun dailyChartDescription_perMetric() {
        assertEquals(
            "Daily mean temperatures, 13 days, averaging 12.0°C.",
            dailyChartDescription(summary(Metric.TEMPERATURE, 13, 12.0, 8.0), withForecast = false),
        )
        assertEquals(
            "Daily sunshine, 13 days, 68.9 h total, historical average 52.0 h, with the forecast for the coming days.",
            dailyChartDescription(summary(Metric.SUNSHINE, 13, 5.3, 4.0), withForecast = true),
        )
        assertEquals(
            "Daily rain, 13 days, 41 mm in total, historical average 22 mm.",
            dailyChartDescription(summary(Metric.PRECIPITATION, 13, 41.0, 22.0), withForecast = false),
        )
    }

    // ---- year ----

    private fun months(
        metric: Metric,
        vararg pairs: Pair<Double, Double>,
    ) = pairs.mapIndexed { i, (observed, reference) -> MonthlyAnomaly(i + 1, observed, reference, null, 30, false, metric) }

    @Test
    fun yearSummary_temperatureIsUnchanged() {
        val s = YearSummary(delta = 4.0, warmerMonths = 10, comparedMonths = 10)
        assertEquals(
            "So far 2026 is +4.0°C compared with 1961–1990, with 10 of 10 months warmer than average.",
            yearSummaryText(2026, s, "1961–1990"),
        )
        assertEquals("No monthly comparison is available yet.", yearSummaryText(2026, YearSummary(null, 0, 0), "1961–1990"))
    }

    @Test
    fun yearSummary_sunshine() {
        val s = summarizeYear(months(Metric.SUNSHINE, 5.0 to 4.5, 6.0 to 5.0, 4.0 to 5.0))

        assertEquals(
            "So far this year: +15 h sunshine compared with the $ref average, 2 of 3 months sunnier.",
            yearSummaryText(2026, s, ref, Metric.SUNSHINE),
        )
        val duller = summarizeYear(months(Metric.SUNSHINE, 4.0 to 5.0, 4.5 to 5.0))
        assertEquals(
            "So far this year: -45 h sunshine compared with the $ref average, 0 of 2 months sunnier.",
            yearSummaryText(2026, duller, ref, Metric.SUNSHINE),
        )
    }

    @Test
    fun yearSummary_rainIsTheAbsoluteDifferenceInMillimetres() {
        val s = summarizeYear(months(Metric.PRECIPITATION, 80.0 to 60.0, 50.0 to 70.0, 120.0 to 100.0))

        assertEquals(
            "So far this year: +20 mm rainfall compared with the $ref average, 2 of 3 months wetter.",
            yearSummaryText(2026, s, ref, Metric.PRECIPITATION),
        )
    }

    @Test
    fun yearSummary_neutralAndLowNormalVariants() {
        val flat = summarizeYear(months(Metric.SUNSHINE, 5.0 to 5.0, 5.001 to 5.0))
        assertEquals(
            "So far this year: sunshine in line with the $ref average, 1 of 2 months sunnier.",
            yearSummaryText(2026, flat, ref, Metric.SUNSHINE),
        )
        val dry = summarizeYear(months(Metric.PRECIPITATION, 0.5 to 0.2, 0.4 to 0.3))
        assertNull(dry.deltaPercent)
        assertEquals(
            "So far this year: +0.4 mm rainfall compared with the $ref average, 2 of 2 months wetter.",
            yearSummaryText(2026, dry, ref, Metric.PRECIPITATION),
        )
        val even = summarizeYear(months(Metric.PRECIPITATION, 100.0 to 100.0, 50.0 to 50.0))
        assertEquals(
            "So far this year: rainfall in line with the $ref average, 0 of 2 months wetter.",
            yearSummaryText(2026, even, ref, Metric.PRECIPITATION),
        )
    }

    @Test
    fun yearChartCaption_perMetric() {
        assertEquals(
            "Bars show the deviation in °C. A lighter bar is a month that is still running, compared with the same days only.",
            yearChartCaption(Metric.TEMPERATURE),
        )
        assertTrue(
            yearChartCaption(Metric.SUNSHINE)
                .startsWith("Bars show each month's hours of sunshine; the wider grey bar behind is the historical average."),
        )
        assertTrue(
            yearChartCaption(Metric.PRECIPITATION)
                .startsWith("Bars show each month's rain in mm; the wider grey bar behind is the historical average."),
        )
    }

    @Test
    fun yearTable_headersAndCellsPerMetric() {
        assertEquals(listOf("Month", "Observed", "Reference", "Δ"), yearTableHeader(Metric.TEMPERATURE))
        assertEquals(listOf("Month", "Total", "Reference", "Δ", "Δ%"), yearTableHeader(Metric.SUNSHINE))
        assertEquals(listOf("Month", "Total", "Reference", "Δ", "Δ%"), yearTableHeader(Metric.PRECIPITATION))

        assertEquals(
            listOf("12.0°C", "8.0°C", "+4.0"),
            yearTableCells(Metric.TEMPERATURE, months(Metric.TEMPERATURE, 12.0 to 8.0).first()),
        )
        // sunshine is shown as hours per month: 30 days of 5.4 vs 4.8 h/day
        assertEquals(
            listOf("162 h", "144 h", "+18 h", "+13%"),
            yearTableCells(Metric.SUNSHINE, months(Metric.SUNSHINE, 5.4 to 4.8).first()),
        )
        assertEquals(
            listOf("81 mm", "60 mm", "+21 mm", "+35%"),
            yearTableCells(Metric.PRECIPITATION, months(Metric.PRECIPITATION, 81.0 to 60.0).first()),
        )
        // a tiny normal has no percent
        assertEquals(
            listOf("3.0 mm", "1.0 mm", "+2.0 mm", "–"),
            yearTableCells(Metric.PRECIPITATION, months(Metric.PRECIPITATION, 3.0 to 1.0).first()),
        )
        assertEquals(
            listOf("–", "8.0°C", "–"),
            yearTableCells(Metric.TEMPERATURE, MonthlyAnomaly(1, null, 8.0, null, 0, false)),
        )
    }

    @Test
    fun yearTable_aRunningMonthUsesTheDaysSoFar() {
        val running = MonthlyAnomaly(10, 4.0, 3.0, null, daysObserved = 14, partial = true, metric = Metric.SUNSHINE, days = 15)

        assertEquals(listOf("60 h", "45 h", "+15 h", "+33%"), yearTableCells(Metric.SUNSHINE, running))
    }

    @Test
    fun titles() {
        assertEquals("Daily temperatures", Metric.TEMPERATURE.dailyTitle)
        assertEquals("Daily sunshine", Metric.SUNSHINE.dailyTitle)
        assertEquals("Daily rain", Metric.PRECIPITATION.dailyTitle)
    }
}
