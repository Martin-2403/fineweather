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
        outlook: ForecastOutlook? = null,
        daysObserved: Int = 13,
        failed: Set<DataSource> = emptySet(),
    ) = SummaryInput(
        monthName = "October",
        monthToDate = monthToDate,
        reference = reference,
        referenceLabel = "1961–1990",
        stdDev = stdDev,
        mode = mode,
        outlook = outlook,
        daysObserved = daysObserved,
        failed = failed,
    )

    @Test
    fun noData_promptsForSearch() {
        val blocks = buildSummary(input(monthToDate = null, reference = null))

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is SummaryBlock.Detail)
        assertTrue(blocks.single().text.startsWith("Search for a place"))
    }

    @Test
    fun noCompleteDayYet_explainsWaitingAndGivesTheNormalOfTheMonth() {
        val blocks = buildSummary(input(monthToDate = null))

        assertEquals(
            listOf(
                "No complete day of October yet, check back tomorrow.",
                "The 1961–1990 average for October is 11.00°C.",
            ),
            blocks.map { it.text },
        )
        assertTrue(blocks.none { it is SummaryBlock.Headline })
    }

    @Test
    fun noCompleteDayYet_stillGivesTheOutlook() {
        val blocks = buildSummary(input(monthToDate = null, outlook = outlook(14.0, 10.0)))

        assertEquals("Next 7 days: about 4.0°C warmer than usual (14.0°C vs 10.0°C).", blocks.last().text)
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
            "October so far: clearly warmer than the 1961–1990 average (+2.00°C, 13.00°C vs 11.00°C).",
            headline.text,
        )
        assertEquals(Band.WARM, headline.band)
    }

    @Test
    fun muchColder_headline() {
        val headline = buildSummary(input(monthToDate = 6.0)).first() as SummaryBlock.Headline

        assertEquals(
            "October so far: much colder than the 1961–1990 average (-5.00°C, 6.00°C vs 11.00°C).",
            headline.text,
        )
        assertEquals(Band.COLD, headline.band)
    }

    @Test
    fun neutral_headlineSaysCloseTo() {
        val headline = buildSummary(input(monthToDate = 11.4)).first() as SummaryBlock.Headline

        assertEquals(
            "October so far: close to the 1961–1990 average (+0.40°C, 11.40°C vs 11.00°C).",
            headline.text,
        )
        assertEquals(Band.NEUTRAL, headline.band)
    }

    @Test
    fun anomalyMode_keepsTheReportToTheHeadline() {
        val blocks = buildSummary(input(stdDev = 1.0, mode = TemperatureComparisonMode.NORMALIZED_ANOMALY))

        assertEquals(1, blocks.size)
    }

    private fun outlook(mean: Double, reference: Double?) = ForecastOutlook(days = 7, mean = mean, referenceMean = reference)

    @Test
    fun outlook_isComparedWithTheNormalOfThoseDays() {
        val steady = buildSummary(input(outlook = outlook(10.4, 10.0))).last().text
        val warmer = buildSummary(input(outlook = outlook(14.0, 10.0))).last().text
        val cooler = buildSummary(input(outlook = outlook(7.5, 10.0))).last().text

        assertEquals("Next 7 days: about as warm as usual (10.4°C vs 10.0°C).", steady)
        assertEquals("Next 7 days: about 4.0°C warmer than usual (14.0°C vs 10.0°C).", warmer)
        assertEquals("Next 7 days: about 2.5°C cooler than usual (7.5°C vs 10.0°C).", cooler)
    }

    @Test
    fun outlookWithoutReference_justStatesTheAverage() {
        val text = buildSummary(input(outlook = outlook(9.8, null))).last().text

        assertEquals("Next 7 days: forecast to average 9.8°C.", text)
    }

    @Test
    fun fewDays_addsCaveatWithSingularHandling() {
        val one = buildSummary(input(daysObserved = 1)).last() as SummaryBlock.Caveat
        val five = buildSummary(input(daysObserved = 5)).last() as SummaryBlock.Caveat
        val seven = buildSummary(input(daysObserved = 7))

        assertEquals("Only 1 day so far, so this can change quickly.", one.text)
        assertEquals("Only 5 days so far, so this can change quickly.", five.text)
        assertTrue(seven.none { it is SummaryBlock.Caveat })
    }

    @Test
    fun theVerdictComesFirstSoItCanSitBesideThePicture() {
        val blocks = buildSummary(input(outlook = outlook(14.0, 10.0), daysObserved = 3, failed = setOf(DataSource.FORECAST)))

        assertTrue(blocks[0] is SummaryBlock.Headline)
        assertTrue(blocks[1] is SummaryBlock.Detail)
        assertTrue(blocks.drop(2).all { it is SummaryBlock.Caveat })
    }

    // ---- what could not be loaded ----

    @Test
    fun failedObservations_saySoInsteadOfWaitingForTomorrow() {
        val blocks = buildSummary(input(monthToDate = null, failed = setOf(DataSource.OBSERVED)))

        assertEquals("This year's weather data could not be loaded.", blocks.first().text)
        assertEquals("The 1961–1990 average for October is 11.00°C.", blocks[1].text)
    }

    @Test
    fun nothingLoadedAfterAFailure_doesNotAskToSearch() {
        val blocks = buildSummary(input(monthToDate = null, reference = null, failed = setOf(DataSource.OBSERVED, DataSource.REFERENCE)))

        assertEquals("The weather data could not be loaded.", blocks.single().text)
    }

    @Test
    fun failedForecast_endsWithACaveat() {
        val blocks = buildSummary(input(failed = setOf(DataSource.FORECAST)))

        assertEquals(2, blocks.size)
        assertEquals(SummaryBlock.Caveat("The forecast could not be loaded."), blocks.last())
    }

    @Test
    fun failedReference_keepsTheMessageOfAMissingReference() {
        val blocks = buildSummary(input(reference = null, failed = setOf(DataSource.REFERENCE)))

        assertEquals("The 1961–1990 reference is not available yet.", blocks.single().text)
    }
}
