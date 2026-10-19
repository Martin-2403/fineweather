package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricSummaryTest {

    private fun input(
        metric: Metric,
        monthToDate: Double? = null,
        reference: Double? = null,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        outlook: ForecastOutlook? = null,
        daysObserved: Int = 13,
    ) = SummaryInput(
        monthName = "October",
        monthToDate = monthToDate,
        reference = reference,
        referenceLabel = "1961–1990",
        stdDev = stdDev,
        mode = mode,
        outlook = outlook,
        daysObserved = daysObserved,
        metric = metric,
    )

    private fun sun(observed: Double?, reference: Double? = 5.0, outlook: ForecastOutlook? = null, days: Int = 13) =
        input(Metric.SUNSHINE, observed, reference, outlook = outlook, daysObserved = days)

    private fun rain(
        observed: Double?,
        reference: Double? = 33.0,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        outlook: ForecastOutlook? = null,
        days: Int = 13,
    ) = input(Metric.PRECIPITATION, observed, reference, stdDev, mode, outlook, days)

    private fun headline(input: SummaryInput) = buildSummary(input).first() as SummaryBlock.Headline

    // ---- sunshine ----

    @Test
    fun sunshine_headlineNamesTheSeverityAndTheHourDifference() {
        assertEquals(
            "October so far: a little sunnier than the 1961–1990 average (+0.8 h/day).",
            headline(sun(5.8)).text,
        )
        assertEquals(
            "October so far: clearly sunnier than the 1961–1990 average (+1.0 h/day).",
            headline(sun(6.0)).text,
        )
        assertEquals(
            "October so far: much duller than the 1961–1990 average (-2.0 h/day).",
            headline(sun(3.0)).text,
        )
        assertEquals(
            "October so far: a little duller than the 1961–1990 average (-0.7 h/day).",
            headline(sun(4.3)).text,
        )
    }

    @Test
    fun sunshine_neutralSaysCloseTo() {
        val headline = headline(sun(5.2))

        assertEquals("October so far: close to the 1961–1990 average (+0.2 h/day).", headline.text)
        assertEquals(Band.NEUTRAL, headline.band)
    }

    @Test
    fun sunshine_headlineBandIsThePolarity() {
        assertEquals(Band.WARM, headline(sun(5.8)).band)
        assertEquals(Band.COLD, headline(sun(3.0)).band)
    }

    @Test
    fun sunshine_outlookIsComparedWithTheNormalOfThoseDays() {
        fun outlook(mean: Double, reference: Double?) = ForecastOutlook(7, mean, reference, Metric.SUNSHINE)

        assertEquals("Next 7 days: about 1.2 h/day sunnier than usual.", buildSummary(sun(6.0, outlook = outlook(6.2, 5.0))).last().text)
        assertEquals("Next 7 days: about 1.0 h/day duller than usual.", buildSummary(sun(6.0, outlook = outlook(4.0, 5.0))).last().text)
        assertEquals("Next 7 days: about as sunny as usual.", buildSummary(sun(6.0, outlook = outlook(5.2, 5.0))).last().text)
        assertEquals("Next 7 days: forecast to average 6.2 h/day.", buildSummary(sun(6.0, outlook = outlook(6.2, null))).last().text)
    }

    // ---- rain ----

    @Test
    fun rain_headlineShowsPercentAndBothTotals() {
        assertEquals(
            "October so far: much wetter than the 1961–1990 average (+85%, 61 mm vs 33 mm).",
            headline(rain(61.0)).text,
        )
        assertEquals(
            "October so far: clearly wetter than the 1961–1990 average (+52%, 50 mm vs 33 mm).",
            headline(rain(50.0)).text,
        )
        assertEquals(
            "October so far: a little wetter than the 1961–1990 average (+36%, 45 mm vs 33 mm).",
            headline(rain(45.0)).text,
        )
        assertEquals(
            "October so far: clearly drier than the 1961–1990 average (-70%, 10 mm vs 33 mm).",
            headline(rain(10.0)).text,
        )
        assertEquals(
            "October so far: much drier than the 1961–1990 average (-85%, 5.0 mm vs 33.0 mm).",
            headline(rain(5.0)).text,
        )
    }

    @Test
    fun rain_neutralSaysCloseTo() {
        val headline = headline(rain(35.0))

        assertEquals("October so far: close to the 1961–1990 average (+6%, 35 mm vs 33 mm).", headline.text)
        assertEquals(Band.NEUTRAL, headline.band)
    }

    @Test
    fun rain_smallTotalsKeepADecimal() {
        assertEquals(
            "October so far: much wetter than the 1961–1990 average (+79%, 7.5 mm vs 4.2 mm).",
            headline(rain(7.5, reference = 4.2)).text,
        )
    }

    @Test
    fun rain_aTinyNormalSaysWhatFellAndWhatIsUsual() {
        val headline = headline(rain(4.0, reference = 1.5))

        assertEquals("October so far: 4.0 mm of rain, normal for these days is under 2 mm.", headline.text)
        assertEquals(Band.NEUTRAL, headline.band)
        assertEquals(
            "October so far: 0.0 mm of rain, normal for these days is under 2 mm.",
            headline(rain(0.0, reference = 0.0)).text,
        )
    }

    @Test
    fun rain_normalizedUsesTheSpreadOfTotals() {
        val text = headline(rain(61.0, stdDev = 14.0, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY)).text

        // z = 2.0 is a "clearly", where the +85 % alone would be "much"
        assertEquals("October so far: clearly wetter than the 1961–1990 average (+85%, 61 mm vs 33 mm).", text)
    }

    @Test
    fun rain_outlookComparesTheTotals() {
        fun outlook(mean: Double, reference: Double?) = ForecastOutlook(7, mean, reference, Metric.PRECIPITATION)

        assertEquals("Next 7 days: wetter than usual (25 mm vs 14 mm).", buildSummary(rain(40.0, outlook = outlook(25.0, 14.0))).last().text)
        assertEquals("Next 7 days: drier than usual (5.0 mm vs 14.0 mm).", buildSummary(rain(40.0, outlook = outlook(5.0, 14.0))).last().text)
        assertEquals("Next 7 days: about as wet as usual (15 mm).", buildSummary(rain(40.0, outlook = outlook(15.0, 14.0))).last().text)
        assertEquals("Next 7 days: forecast to bring 25 mm.", buildSummary(rain(40.0, outlook = outlook(25.0, null))).last().text)
    }

    @Test
    fun rain_outlookWithATinyNormalSaysSo() {
        val outlook = ForecastOutlook(7, 3.0, 1.0, Metric.PRECIPITATION)

        assertEquals(
            "Next 7 days: 3.0 mm of rain forecast, normal for those days is under 2 mm.",
            buildSummary(rain(40.0, outlook = outlook)).last().text,
        )
    }

    // ---- shared ----

    @Test
    fun otherMetrics_keepTheMessagesOfMissingData() {
        listOf(Metric.SUNSHINE, Metric.PRECIPITATION).forEach { metric ->
            assertTrue(buildSummary(input(metric)).single().text.startsWith("Search for a place"))
            assertEquals(
                "No complete day of October yet, check back tomorrow.",
                buildSummary(input(metric, reference = 5.0)).single().text,
            )
            assertEquals(
                "The 1961–1990 reference is not available yet.",
                buildSummary(input(metric, monthToDate = 5.0)).single().text,
            )
        }
    }

    @Test
    fun otherMetrics_addTheCaveatForFewDays() {
        val sunshine = buildSummary(sun(6.0, days = 1)).last() as SummaryBlock.Caveat
        val rainy = buildSummary(rain(40.0, days = 5)).last() as SummaryBlock.Caveat

        assertEquals("Only 1 day so far, so this can change quickly.", sunshine.text)
        assertEquals("Only 5 days so far, so this can change quickly.", rainy.text)
        assertTrue(buildSummary(rain(40.0, days = 7)).none { it is SummaryBlock.Caveat })
    }

    @Test
    fun temperature_isTheDefaultAndKeepsItsWording() {
        val default =
            SummaryInput(
                monthName = "October",
                monthToDate = 12.3,
                reference = 11.0,
                referenceLabel = "1961–1990",
                stdDev = null,
                mode = TemperatureComparisonMode.ABSOLUTE_DELTA,
                outlook = ForecastOutlook(7, 11.5, 10.0),
                daysObserved = 13,
            )

        val blocks = buildSummary(default)

        assertEquals("October so far: a little warmer than the 1961–1990 average (+1.30°C).", blocks[0].text)
        assertEquals("Next 7 days: about 1.5°C warmer than usual.", blocks[1].text)
        assertEquals(Metric.TEMPERATURE, default.metric)
    }

    @Test
    fun rain_twoNumbersShareOnePrecision() {
        assertEquals(
            "October so far: much wetter than the 1961–1990 average (+1352%, 61.0 mm vs 4.2 mm).",
            headline(rain(61.0, reference = 4.2)).text,
        )
        val outlook = ForecastOutlook(7, 21.0, 7.0, Metric.PRECIPITATION)
        assertEquals(
            "Next 7 days: wetter than usual (21.0 mm vs 7.0 mm).",
            buildSummary(rain(40.0, outlook = outlook)).last().text,
        )
    }

    @Test
    fun normalizedMode_decidesTheSeverityWordsOfTheHeadlineOnly() {
        val anomaly = TemperatureComparisonMode.NORMALIZED_ANOMALY
        // +1.5 h/day is "much" in hours, but only 1.5 standard deviations: "clearly"
        assertEquals(
            "October so far: much sunnier than the 1961–1990 average (+1.5 h/day).",
            headline(sun(6.5)).text,
        )
        assertEquals(
            "October so far: clearly sunnier than the 1961–1990 average (+1.5 h/day).",
            headline(input(Metric.SUNSHINE, 6.5, 5.0, stdDev = 1.0, mode = anomaly)).text,
        )
        // the outlook never uses the spread
        val sunOutlook = ForecastOutlook(7, 6.2, 5.0, Metric.SUNSHINE)
        val rainOutlook = ForecastOutlook(7, 25.0, 14.0, Metric.PRECIPITATION)
        assertEquals(
            buildSummary(sun(6.0, outlook = sunOutlook)).last().text,
            buildSummary(input(Metric.SUNSHINE, 6.0, 5.0, stdDev = 1.0, mode = anomaly, outlook = sunOutlook)).last().text,
        )
        assertEquals(
            buildSummary(rain(40.0, outlook = rainOutlook)).last().text,
            buildSummary(rain(40.0, stdDev = 5.0, mode = anomaly, outlook = rainOutlook)).last().text,
        )
    }

    @Test
    fun rain_outlookTextAgreesWithTheStatusCardAroundTheClassBoundaries() {
        // percents just around 25, 50 and 75 on normals that are not binary fractions
        for (reference in listOf(3.3, 7.7, 14.4, 33.3, 61.9)) {
            for (percent in listOf(24.9, 25.0, 25.1, 49.9, 50.0, 50.1, 74.9, 75.0, 75.1, -25.0, -25.1, -50.0)) {
                val outlook = ForecastOutlook(7, reference * (1 + percent / 100), reference, Metric.PRECIPITATION)
                // what the status card judges
                val card = Assessment(outlook.delta!!, null, TemperatureComparisonMode.ABSOLUTE_DELTA, Metric.PRECIPITATION, outlook.referenceMean)

                val text = buildSummary(rain(40.0, outlook = outlook)).last().text

                assertEquals("$reference $percent", card.band == Band.NEUTRAL, text.contains("about as wet as usual"))
                assertEquals("$reference $percent", card.band == Band.WARM, text.contains("wetter than usual"))
                assertEquals("$reference $percent", card.band == Band.COLD, text.contains("drier than usual"))
            }
        }
    }
}
