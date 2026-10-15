package com.example.fineweather.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.data.models.TemperatureComparisonMode
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.Metric
import com.example.fineweather.domain.MetricSeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.ui.models.IconStack
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.viewmodels.FailKind
import com.example.fineweather.viewmodels.Loadable
import com.example.fineweather.viewmodels.PlaceLabel
import com.example.fineweather.viewmodels.SearchStatus
import com.example.fineweather.viewmodels.StatusPicture
import com.example.fineweather.viewmodels.WeatherReport
import com.example.fineweather.viewmodels.WeatherUiState
import com.example.fineweather.viewmodels.buildReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** The metric switch and what the header, the Current view and the other views show for each metric. */
class MetricViewsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val shape = RoundedCornerShape(12.dp)
    private val cardColors = CardColors(Color.Transparent, Color.Black, Color.Transparent, Color.Black)
    private val today = LocalDate.of(2026, 4, 2)

    private fun series(
        start: LocalDate,
        end: LocalDate,
        valueOf: (LocalDate) -> Double,
    ): DailySeries {
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        return DailySeries.fromApi(start, end, dates.map { it.toString() }, dates.map(valueOf))
    }

    private fun metrics(
        start: LocalDate,
        end: LocalDate,
        temperature: Double,
        sunshine: Double,
        rain: Double,
    ) = MetricSeries(
        mapOf(
            Metric.TEMPERATURE to series(start, end) { temperature },
            Metric.SUNSHINE to series(start, end) { sunshine },
            Metric.PRECIPITATION to series(start, end) { rain },
        ),
    )

    private fun normal(value: Double) = ReferenceClimate(series(LocalDate.of(1961, 1, 1), LocalDate.of(1990, 12, 31)) { value }, 1961, 1990)

    /**
     * April 1 against normals of 4 degrees, 6 h of sunshine and 3 mm of rain a day: 6 degrees warmer, 1 h duller,
     * twice the rain. The next 7 days are 8 degrees warmer, 2 h sunnier and a third wetter.
     */
    private fun state(extras: Loadable<Map<Metric, ReferenceClimate>>) =
        WeatherUiState(
            today = today,
            search = SearchStatus.Resolved(PlaceLabel("Berlin", "1.00, 2.00")),
            selectedPlace = GeoPlace("loc-1", "Berlin", "Germany", null, 1.0, 2.0),
            forecast = Loadable.Ready(metrics(today, today.plusDays(13), 12.0, 8.0, 4.0)),
            year = Loadable.Ready(metrics(LocalDate.of(2026, 1, 1), today.minusDays(1), 10.0, 5.0, 6.0)),
            reference = Loadable.Ready(normal(4.0)),
            extras = extras,
        )

    private val readyExtras =
        Loadable.Ready(mapOf(Metric.SUNSHINE to normal(6.0), Metric.PRECIPITATION to normal(3.0)))

    private fun report(
        metric: Metric,
        extras: Loadable<Map<Metric, ReferenceClimate>> = readyExtras,
    ): WeatherReport =
        buildReport(state(extras), 7, TemperatureComparisonMode.ABSOLUTE_DELTA, HistoricReference.CLASSIC, metric)

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent { FineWeatherTheme { content() } }
    }

    /** The header and the Current view as the screen puts them together for [report]. */
    @Composable
    private fun ReportViews(report: WeatherReport) {
        Column {
            StatusHeaderCard(
                imageRes = resolveStatusImageRes(report.picture),
                summary = report.summary.toBlocks(),
                statusCardValue = buildStatusCardValue(report.headline, isDarkCard()),
                trendCardValue = buildTrendCardValue(report.trend, isDarkCard()),
                shape = shape,
                pictureLoading = report.picture == StatusPicture.LOADING,
                summaryLoading = report.summary is Loadable.Loading,
                metric = report.metric,
            )
            CurrentView(report.rows, shape, cardColors, onWarningClick = {}, metric = report.metric)
        }
    }

    // ---- the pill ----

    @Test
    fun pills_showTheThreeMetricsWithTheSelectedOneMarked() {
        show { MetricPills(selected = Metric.SUNSHINE, onSelect = {}) }

        listOf("Temp", "Sun", "Rain").forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
        composeTestRule.onNodeWithTag("metric_Temp").assertIsNotSelected()
        composeTestRule.onNodeWithTag("metric_Sun").assertIsSelected()
        composeTestRule.onNodeWithTag("metric_Rain").assertIsNotSelected()
    }

    @Test
    fun pills_spellOutTheMetricsForScreenReaders() {
        show { MetricPills(selected = Metric.TEMPERATURE, onSelect = {}) }

        composeTestRule.onNodeWithContentDescription("Temperature").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sunshine").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Rain").assertIsDisplayed()
    }

    @Test
    fun pills_reportTheTappedMetricAndMoveTheSelection() {
        var selected by mutableStateOf(Metric.TEMPERATURE)
        val tapped = mutableListOf<Metric>()
        show { MetricPills(selected = selected, onSelect = { tapped += it; selected = it }) }

        composeTestRule.onNodeWithTag("metric_Rain").performClick()
        composeTestRule.onNodeWithTag("metric_Sun").performClick()
        composeTestRule.onNodeWithTag("metric_Temp").performClick()

        assertEquals(listOf(Metric.PRECIPITATION, Metric.SUNSHINE, Metric.TEMPERATURE), tapped)
        composeTestRule.onNodeWithTag("metric_Temp").assertIsSelected()
        composeTestRule.onNodeWithTag("metric_Rain").assertIsNotSelected()
    }

    @Test
    fun pills_leaveTheOtherTogglesUntouched() {
        show { CompactToggle(listOf("Month", "30 days"), selectedIndex = 0, onSelect = {}, tagPrefix = "monthWindow") }

        composeTestRule.onNodeWithTag("monthWindow_Month").assertIsSelected()
        composeTestRule.onAllNodesWithContentDescription("Temperature").assertCountEquals(0)
    }

    // ---- header and Current view per metric ----

    @Test
    fun temperature_showsTheTemperatureHeaderAndRows() {
        show { ReportViews(report(Metric.TEMPERATURE)) }

        composeTestRule.onNodeWithText("Average temperatures").assertIsDisplayed()
        composeTestRule.onNodeWithText("+6.00°C").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("+++ warmer than average").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Forecast warmer than normal").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("10.00°C").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Last 31 days").assertIsDisplayed()
    }

    @Test
    fun sunshine_showsHoursInTheRowsAndSunAndCloudInTheHeader() {
        show { ReportViews(report(Metric.SUNSHINE)) }

        composeTestRule.onNodeWithText("Average sunshine").assertIsDisplayed()
        composeTestRule.onNodeWithText("Average temperatures").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("5.0 h/day").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("6.0 h/day").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.0 h/day").assertIsDisplayed()
        composeTestRule.onNodeWithText("-1.0 h/day").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("-- duller than average").assertIsDisplayed()
        composeTestRule.onNodeWithText("+2.0 h/day").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Forecast sunnier than normal").assertIsDisplayed()
        composeTestRule.onNodeWithText("This month so far").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next 7 days (forecast)").assertIsDisplayed()
    }

    @Test
    fun rain_showsTotalsInTheRowsAndPercentInTheHeader() {
        show { ReportViews(report(Metric.PRECIPITATION)) }

        composeTestRule.onNodeWithText("Rain totals").assertIsDisplayed()
        // April 1 so far, the normal of that day, and the next 7 days
        composeTestRule.onNodeWithText("6.0 mm").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.0 mm").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 mm").assertIsDisplayed()
        composeTestRule.onNodeWithText("+100%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("+++ wetter than average").assertIsDisplayed()
        composeTestRule.onNodeWithText("+33%").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Forecast wetter than normal").assertIsDisplayed()
    }

    @Test
    fun theHeaderKeepsItsLabelsAndTheTemperaturePictureForEveryMetric() {
        var metric by mutableStateOf(Metric.TEMPERATURE)
        show { ReportViews(report(metric)) }

        Metric.entries.forEach {
            metric = it

            composeTestRule.onNodeWithText("Trend").assertIsDisplayed()
            composeTestRule.onNodeWithText("Divergence").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription("Status illustration").assertIsDisplayed()
            composeTestRule.onNodeWithTag("statusSummary").assertIsDisplayed()
            // the picture follows the temperature (6 degrees warmer) even while sunshine is short
            assertEquals(StatusPicture.WARM, report(it).picture)
        }
    }

    // ---- while the extras load ----

    @Test
    fun sunshineWhileTheExtrasLoad_showsPlaceholdersOnlyWhereTheNormalIsNeeded() {
        show { ReportViews(report(Metric.SUNSHINE, extras = Loadable.Loading)) }

        // the normal row, plus the two header cells
        composeTestRule.onAllNodesWithContentDescription("Loading value").assertCountEquals(1)
        composeTestRule.onAllNodesWithContentDescription("Loading").assertCountEquals(2)
        composeTestRule.onAllNodesWithContentDescription("Loading report").assertCountEquals(1)
        composeTestRule.onNodeWithText("Average sunshine").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("5.0 h/day").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("8.0 h/day").assertIsDisplayed()
        // the picture is the temperature's and needs no extras
        composeTestRule.onNodeWithContentDescription("Status illustration").assertIsDisplayed()
    }

    @Test
    fun sunshineWhenTheExtrasFail_showsErrorsInsteadOfValues() {
        show {
            ReportViews(
                report(
                    Metric.SUNSHINE,
                    extras = Loadable.Failed(FailKind.NETWORK),
                ),
            )
        }

        composeTestRule.onAllNodesWithContentDescription("Loading value").assertCountEquals(0)
        composeTestRule.onNodeWithText("Error").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("5.0 h/day").onFirst().assertIsDisplayed()
    }

    // ---- Month and Year ----

    @Test
    fun monthView_forSunshineAndRainShowsTheCaptionInsteadOfATemperatureChart() {
        var metric by mutableStateOf(Metric.SUNSHINE)
        show { MonthView(null, 7, "1961–1990", shape, cardColors, metric = metric) }

        composeTestRule.onNodeWithText("Daily sunshine").assertIsDisplayed()
        composeTestRule.onNodeWithText("The daily chart for sunshine and rain is not available yet.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthInfoButton").assertDoesNotExist()
        composeTestRule.onNodeWithText("30 days").assertDoesNotExist()

        metric = Metric.PRECIPITATION
        composeTestRule.onNodeWithText("Daily rain").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily temperatures").assertDoesNotExist()
    }

    @Test
    fun monthView_forTemperatureIsUnchanged() {
        show { MonthView(null, 7, "1961–1990", shape, cardColors) }

        composeTestRule.onNodeWithText("Daily temperatures").assertIsDisplayed()
        composeTestRule.onNodeWithText("The daily chart for sunshine and rain is not available yet.").assertDoesNotExist()
    }

    @Test
    fun yearView_forSunshineAndRainShowsACaption() {
        show { YearView(null, "1961–1990", shape, cardColors, metric = Metric.PRECIPITATION) }

        composeTestRule.onNodeWithTag("yearView").assertIsDisplayed()
        composeTestRule.onNodeWithText("The monthly chart for sunshine and rain is not available yet.").assertIsDisplayed()
    }

    @Test
    fun yearView_forTemperatureIsUnchanged() {
        show { YearView(null, "1961–1990", shape, cardColors) }

        composeTestRule
            .onNodeWithText("Search for a place to compare its months with the climate average.")
            .assertIsDisplayed()
    }

    // ---- the header keeps its height ----

    private fun icons(count: Int) = IconStack(R.drawable.up, count, Color.Red, "icons")

    private fun cell(
        text: String,
        count: Int,
    ) = ValueWithIcons(text, icons(count))

    /** The worst cells of each metric: a text and three icons, loading, and a dash. */
    private val headerCases: List<Pair<ValueWithIcons, ValueWithIcons>> =
        listOf(
            cell("", 2) to cell("+1.43°C", 3), // temperature
            cell("-3.2 h/day", 3) to cell("-1.8 h/day", 3), // sunshine
            cell("+100%", 3) to cell("+100%", 3), // rain
            cell("", 1) to ValueWithIcons("-1.00°C", null),
            ValueWithIcons("", null, loading = true) to ValueWithIcons("", null, loading = true),
            ValueWithIcons("-", null, placeholder = true) to ValueWithIcons("-", null, placeholder = true),
        )

    private fun headerHeights(
        width: Dp,
        fontScale: Float,
        metric: (Int) -> Metric,
    ): List<Dp> {
        var index by mutableStateOf(0)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                FineWeatherTheme {
                    Box(Modifier.width(width).testTag("header")) {
                        StatusHeaderCard(
                            imageRes = R.drawable.warm,
                            summary = listOf(SummaryBlock.Detail("A short sentence.")),
                            statusCardValue = headerCases[index].second,
                            trendCardValue = headerCases[index].first,
                            shape = shape,
                            metric = metric(index),
                        )
                    }
                }
            }
        }
        return headerCases.indices.map {
            index = it
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("header").getUnclippedBoundsInRoot().let { r -> r.bottom - r.top }
        }
    }

    private val metricOfCase = { index: Int -> Metric.entries[index % Metric.entries.size] }

    @Test
    fun header_onAPhoneKeepsItsHeightWhateverTheMetricAndValues() {
        val heights = headerHeights(360.dp, 1f, metricOfCase)

        assertEquals(heights.toString(), 1, heights.toSet().size)
    }

    @Test
    fun header_onAPhoneWithLargerFontsKeepsItsHeightAndDoesNotWrapTheValues() {
        val heights = headerHeights(360.dp, 1.3f, metricOfCase)

        assertEquals(heights.toString(), 1, heights.toSet().size)
    }

    @Test
    fun header_onATabletKeepsItsHeightWhateverTheMetricAndValues() {
        val heights = headerHeights(900.dp, 1f, metricOfCase)

        assertEquals(heights.toString(), 1, heights.toSet().size)
    }

    @Test
    fun stackedCell_isAtMostSixteenDpTallerThanTheInlineOne() {
        var stacked by mutableStateOf(false)
        composeTestRule.setContent {
            FineWeatherTheme {
                Box(Modifier.width(144.dp).testTag("cell")) {
                    StatusTrendCell("Trend", cell("", 3), stacked = stacked)
                }
            }
        }
        val inline = composeTestRule.onNodeWithTag("cell").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        stacked = true
        composeTestRule.waitForIdle()
        val stackedHeight = composeTestRule.onNodeWithTag("cell").getUnclippedBoundsInRoot().let { it.bottom - it.top }

        assertTrue("$inline -> $stackedHeight", stackedHeight - inline <= 16.dp)
        println("STACKED_CELL_DP inline=$inline stacked=$stackedHeight")
    }

    @Test
    fun stackedCell_showsAllThreeIconsOfAWideTextWithinItsWidth() {
        show {
            Box(Modifier.width(144.dp).testTag("cell")) {
                StatusTrendCell("Trend", ValueWithIcons("+100%", icons(3)), stacked = true)
            }
        }

        val text = composeTestRule.onNodeWithText("+100%").getUnclippedBoundsInRoot()
        val firstIcon = composeTestRule.onNodeWithContentDescription("icons").getUnclippedBoundsInRoot()
        val cell = composeTestRule.onNodeWithTag("cell").getUnclippedBoundsInRoot()
        // text and icons share one line, and nothing reaches beyond the cell
        assertTrue("$text $firstIcon", kotlin.math.abs(((text.top + text.bottom) - (firstIcon.top + firstIcon.bottom)).value) < 8f)
        assertTrue(firstIcon.right <= cell.right)
    }
}
