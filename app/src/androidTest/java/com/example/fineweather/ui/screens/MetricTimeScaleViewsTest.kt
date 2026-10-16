package com.example.fineweather.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The Month and Year views for sunshine and rain. */
class MetricTimeScaleViewsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val shape = RoundedCornerShape(12.dp)
    private val cardColors = CardColors(Color.Transparent, Color.Black, Color.Transparent, Color.Black)
    private val today = LocalDate.of(2026, 10, 14)
    private val label = "1961–1990"

    private fun series(
        start: LocalDate,
        end: LocalDate,
        valueOf: (LocalDate) -> Double,
    ): DailySeries {
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        return DailySeries.fromApi(start, end, dates.map { it.toString() }, dates.map(valueOf))
    }

    /** Observed [observed] a day, normal [normal] a day, forecast [forecast] a day. */
    private fun snapshot(
        metric: Metric,
        observed: Double,
        normal: Double?,
        forecast: Double?,
    ) = ClimateSnapshot(
        series = series(LocalDate.of(2026, 1, 1), today.minusDays(1)) { observed },
        reference =
            normal?.let {
                ReferenceClimate(series(LocalDate.of(1961, 1, 1), LocalDate.of(1990, 12, 31)) { _ -> it }, 1961, 1990)
            },
        today = today,
        forecast = forecast?.let { series(today, today.plusDays(13)) { _ -> it } },
        metric = metric,
    )

    private val sunshine = snapshot(Metric.SUNSHINE, 6.0, 4.5, 5.5)
    private val rain = snapshot(Metric.PRECIPITATION, 3.0, 2.0, 1.0)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeTestRule.setContent { FineWeatherTheme { content() } }
    }

    // ---- Month: sunshine ----

    @Test
    fun monthView_sunshineShowsTitleToggleInfoAndTheSentences() {
        show { MonthView(sunshine, 7, label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithText("Daily sunshine").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthInfoButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthWindow_Month").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthSummary")
            .assertTextEquals("13 days averaged 6.0 h/day, +1.5 h/day compared with the same days of 1961–1990.")
        composeTestRule.onNodeWithTag("monthCaption")
            .assertTextEquals("13 days sunnier and 0 days duller than the typical day.")
        composeTestRule.onNodeWithTag("forecastSummary")
            .assertTextEquals("Forecast: the next 7 days average 5.5 h/day, +1.0 h/day compared with normal for those days.")
        composeTestRule
            .onNodeWithContentDescription("Daily sunshine, 13 days, averaging 6.0 h/day, with the forecast for the coming days.")
            .assertExists()
    }

    @Test
    fun monthView_sunshineSwitchesToThirtyDays() {
        show { MonthView(sunshine, 7, label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithText("30 days").performClick()

        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("30 days averaged 6.0 h/day", substring = true)
    }

    @Test
    fun monthView_sunshineLegendExplainsBarsTheAccumulatedLinesAndTheDataSource() {
        show { MonthView(sunshine, 7, label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithTag("monthInfoButton").performClick()

        composeTestRule.onNodeWithTag("chartLegend").assertTextContains("Hours of sunshine each day.", substring = true)
        composeTestRule.onNodeWithTag("chartLegend")
            .assertTextContains("Historical average for this period", substring = true)
        composeTestRule.onNodeWithTag("chartLegend").assertTextContains("rain especially", substring = true)
        composeTestRule.onNodeWithTag("chartLegend").assertTextContains("(ERA5)", substring = true)
        composeTestRule.onNodeWithText("OK").performClick()
        composeTestRule.onNodeWithTag("chartLegend").assertDoesNotExist()
    }

    @Test
    fun monthView_sunshineWithoutReferenceOrForecastKeepsOnlyWhatItKnows() {
        show { MonthView(snapshot(Metric.SUNSHINE, 6.0, null, null), 7, label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithTag("monthSummary").assertTextEquals("13 days averaged 6.0 h/day.")
        composeTestRule.onNodeWithTag("monthCaption").assertDoesNotExist()
        composeTestRule.onNodeWithTag("forecastSummary").assertDoesNotExist()
    }

    // ---- Month: rain ----

    @Test
    fun monthView_rainShowsTotalsRainyDaysAndTheForecast() {
        show { MonthView(rain, 7, label, shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithText("Daily rain").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthInfoButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthSummary")
            .assertTextEquals("13 days: 39 mm in total, +50% (39 vs 26 mm) compared with the same days of 1961–1990.")
        composeTestRule.onNodeWithTag("monthCaption")
            .assertTextEquals("13 rainy days (>= 1 mm), wettest: 3.0 mm on Oct 1.")
        composeTestRule.onNodeWithTag("forecastSummary")
            .assertTextEquals("Forecast: about 7.0 mm of rain in the next 7 days, vs 14 mm normal for those days.")
        composeTestRule
            .onNodeWithContentDescription("Daily rain, 13 days, 39 mm in total, with the forecast for the coming days.")
            .assertExists()
    }

    @Test
    fun monthView_rainLegendMentionsDryDaysAndNotTheSunshineNote() {
        show { MonthView(rain, 7, label, shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithTag("monthInfoButton").performClick()

        composeTestRule.onNodeWithTag("chartLegend").assertTextContains("A thin stub is a dry day", substring = true)
        composeTestRule.onNodeWithTag("chartLegend").assertTextContains("rough guide", substring = true)
    }

    @Test
    fun monthView_aDryMonthSaysSo() {
        show { MonthView(snapshot(Metric.PRECIPITATION, 0.0, 2.0, null), 7, label, shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithTag("monthCaption").assertTextEquals("No rainy days (>= 1 mm).")
        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("0.0 mm in total, -100% (0.0 vs 26 mm)", substring = true)
    }

    // ---- window and metric ----

    @Test
    fun monthView_keepsItsWindowWhenTheMetricChanges() {
        var metric by mutableStateOf(Metric.TEMPERATURE)
        show {
            val snapshot =
                when (metric) {
                    Metric.TEMPERATURE -> snapshot(Metric.TEMPERATURE, 12.0, 8.0, 14.0)
                    Metric.SUNSHINE -> sunshine
                    Metric.PRECIPITATION -> rain
                }
            MonthView(snapshot, 7, label, shape, cardColors, metric = metric)
        }
        composeTestRule.onNodeWithText("30 days").performClick()
        composeTestRule.onNodeWithTag("monthWindow_30 days").assertIsSelected()

        metric = Metric.SUNSHINE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthWindow_30 days").assertIsSelected()
        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("30 days averaged", substring = true)

        metric = Metric.PRECIPITATION
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthWindow_30 days").assertIsSelected()
        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("30 days:", substring = true)
        composeTestRule.onNodeWithText("Daily rain").assertIsDisplayed()

        metric = Metric.TEMPERATURE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monthWindow_30 days").assertIsSelected()
        composeTestRule.onNodeWithText("Daily temperatures").assertIsDisplayed()
    }

    // ---- placeholders ----

    @Test
    fun monthView_sunshineAndRainPromptLoadAndFailLikeTemperature() {
        var metric by mutableStateOf(Metric.SUNSHINE)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
        show { MonthView(null, 7, label, shape, cardColors, loading = loading, failed = failed, metric = metric) }

        composeTestRule.onNodeWithText("Search for a place to see its daily sunshine.").assertIsDisplayed()
        metric = Metric.PRECIPITATION
        composeTestRule.onNodeWithText("Search for a place to see its daily rain.").assertIsDisplayed()

        loading = true
        composeTestRule.onNodeWithText("Daily rain").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthInfoButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthWindow_Month").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()

        loading = false
        failed = true
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    }

    @Test
    fun yearView_sunshineAndRainPromptLoadAndFailLikeTemperature() {
        var metric by mutableStateOf(Metric.SUNSHINE)
        var loading by mutableStateOf(true)
        var failed by mutableStateOf(false)
        show { YearView(null, label, shape, cardColors, loading = loading, failed = failed, metric = metric) }

        composeTestRule.onNodeWithText("Months of the year").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()

        metric = Metric.PRECIPITATION
        loading = false
        composeTestRule.onNodeWithText("Search for a place to compare its months with the climate average.").assertIsDisplayed()
        failed = true
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
    }

    // ---- Year ----

    @Test
    fun yearView_sunshineShowsTheChartTheSentenceAndTheTable() {
        show { YearView(sunshine, label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithText("2026 compared with 1961–1990").assertIsDisplayed()
        composeTestRule.onNodeWithTag("yearSummary")
            .assertTextEquals("So far this year: +1.5 h/day compared with the 1961–1990 average, 10 of 10 months sunnier.")
        composeTestRule.onNodeWithText("Observed").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jan").assertExists()
        composeTestRule.onNodeWithText("Oct*").assertExists()
        composeTestRule.onNodeWithText("Bars show the deviation in h/day.", substring = true).assertExists()
        composeTestRule
            .onNodeWithContentDescription("Monthly sunshine compared with the 1961–1990 average. 10 of 10 months were sunnier.")
            .assertExists()
    }

    @Test
    fun yearView_rainShowsPercentSentenceAndTotalsTable() {
        show { YearView(rain, label, shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithTag("yearSummary")
            .assertTextEquals("So far this year: +50% rainfall compared with the 1961–1990 average, 10 of 10 months wetter.")
        composeTestRule.onNodeWithText("Total").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bars show how much more or less rain fell than normal, in percent.", substring = true).assertExists()
        composeTestRule
            .onNodeWithContentDescription("Monthly rain compared with the 1961–1990 average. 10 of 10 months were wetter.")
            .assertExists()
    }

    @Test
    fun yearView_rainWithATinyNormalShowsTheMillimetreDeltaAndExplainsTheMissingBars() {
        show { YearView(snapshot(Metric.PRECIPITATION, 0.2, 0.05, null), label, shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithText("A month with under 2 mm of normal rain has no bar", substring = true).assertExists()
        composeTestRule.onNodeWithText("+4.7 mm", substring = true).assertExists()
    }

    @Test
    fun yearView_withoutReferenceSaysNoComparisonForSunshineToo() {
        show { YearView(snapshot(Metric.SUNSHINE, 6.0, null, null), label, shape, cardColors, metric = Metric.SUNSHINE) }

        composeTestRule.onNodeWithText("No monthly comparison is available yet.").assertIsDisplayed()
    }

    @Test
    fun yearView_temperatureKeepsItsWordingAndTable() {
        show { YearView(snapshot(Metric.TEMPERATURE, 12.0, 8.0, null), label, shape, cardColors) }

        composeTestRule.onNodeWithTag("yearSummary")
            .assertTextEquals("So far 2026 is +4.0°C compared with 1961–1990, with 10 of 10 months warmer than average.")
        composeTestRule.onNodeWithText("Reference").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bars show the deviation in °C.", substring = true).assertExists()
    }
}
