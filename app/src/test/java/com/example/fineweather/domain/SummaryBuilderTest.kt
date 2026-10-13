package com.example.fineweather.domain

import com.example.fineweather.data.models.TemperatureComparisonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryBuilderTest {

    private fun input(
        monthToDate: Double? = 13.0,
        reference: Double? = 11.0,
        stdDev: Double? = null,
        mode: TemperatureComparisonMode = TemperatureComparisonMode.ABSOLUTE_DELTA,
        forecast: Double? = null,
        daysObserved: Int = 13,
    ) = SummaryInput(
        monthName = "October",
        monthToDate = monthToDate,
        reference = reference,
        referenceLabel = "1961–1990",
        stdDev = stdDev,
        mode = mode,
        forecast = forecast,
        forecastDays = 7,
        daysObserved = daysObserved,
    )

    @Test
    fun noData_promptsForSearch() {
        val blocks = buildSummary(input(monthToDate = null, reference = null))

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is SummaryBlock.Detail)
        assertTrue(blocks.single().text.startsWith("Search for a place"))
    }

    @Test
    fun noCompleteDayYet_explainsWaiting() {
        val blocks = buildSummary(input(monthToDate = null))

        assertEquals("No complete day of October yet, check back tomorrow.", blocks.single().text)
    }

    @Test
    fun missingReference_saysSo() {
        val blocks = buildSummary(input(reference = null))

        assertEquals("The 1961–1990 reference is not available yet.", blocks.single().text)
    }

    @Test
    fun clearlyWarmer_headline() {
        val headline = buildSummary(input()).first() as SummaryBlock.Headline

        assertEquals(
            "October so far is clearly warmer than the 1961–1990 average (+2.00°C).",
            headline.text,
        )
        assertEquals(Band.WARM, headline.band)
    }

    @Test
    fun muchColder_headline() {
        val headline = buildSummary(input(monthToDate = 6.0)).first() as SummaryBlock.Headline

        assertEquals(
            "October so far is much colder than the 1961–1990 average (-5.00°C).",
            headline.text,
        )
        assertEquals(Band.COLD, headline.band)
    }

    @Test
    fun neutral_headlineSaysCloseTo() {
        val headline = buildSummary(input(monthToDate = 11.4)).first() as SummaryBlock.Headline

        assertEquals(
            "October so far is close to the 1961–1990 average (+0.40°C).",
            headline.text,
        )
        assertEquals(Band.NEUTRAL, headline.band)
    }

    @Test
    fun anomalyMode_addsSpreadDetailOnlyOutsideNeutralBand() {
        val warm =
            buildSummary(input(stdDev = 1.0, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY))
        val neutral =
            buildSummary(
                input(monthToDate = 11.2, stdDev = 1.0, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY),
            )

        assertEquals(
            "That is 2.0 standard deviations above what is typical for October.",
            warm[1].text,
        )
        assertEquals(1, neutral.size)
    }

    @Test
    fun forecast_steadyWarmerAndCooler() {
        val steady = buildSummary(input(forecast = 13.1)).last().text
        val warmer = buildSummary(input(forecast = 15.0)).last().text
        val cooler = buildSummary(input(forecast = 10.0)).last().text

        assertEquals("The next 7 days look about as warm as October so far.", steady)
        assertEquals("The next 7 days are forecast to be about 2.0°C warmer than October so far.", warmer)
        assertEquals("The next 7 days are forecast to be about 3.0°C cooler than October so far.", cooler)
    }

    @Test
    fun fewDays_addsCaveatWithSingularHandling() {
        val one = buildSummary(input(daysObserved = 1)).last() as SummaryBlock.Caveat
        val five = buildSummary(input(daysObserved = 5)).last() as SummaryBlock.Caveat
        val seven = buildSummary(input(daysObserved = 7))

        assertEquals("Only 1 day of October so far, so this can change quickly.", one.text)
        assertEquals("Only 5 days of October so far, so this can change quickly.", five.text)
        assertTrue(seven.none { it is SummaryBlock.Caveat })
    }
}
