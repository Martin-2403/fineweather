package com.example.fineweather.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.fineweather.R
import com.example.fineweather.domain.Band
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.domain.SummaryBlock
import com.example.fineweather.ui.models.ValueWithIcons
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class LoadingPlaceholdersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val shape = RoundedCornerShape(12.dp)
    private val cardColors = CardColors(Color.Transparent, Color.Black, Color.Transparent, Color.Black)
    private val noDelay = LoadingConfig(delayMs = 0, animated = false)

    /** Long enough that the surrounding margins are far wider than a frame. */
    private val delayed = LoadingConfig(delayMs = 1000, animated = false)

    private val loadingCell = ValueWithIcons(text = "", icons = null, loading = true)

    /**
     * With [paused], the test clock stands still from the first composition on, so the delay only
     * elapses through [advance].
     */
    private fun show(
        config: LoadingConfig = noDelay,
        paused: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (paused) composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLoadingConfig provides config) { FineWeatherTheme { content() } }
        }
    }

    /** Moves the paused clock by [ms] and renders one frame (16 ms), so what the delay changed shows. */
    private fun advance(ms: Long) {
        composeTestRule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
        composeTestRule.mainClock.advanceTimeByFrame()
    }

    private fun bounds(tag: String): DpRect = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun assertNoPlaceholders() {
        composeTestRule.onAllNodesWithContentDescription("Loading").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Loading picture").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Loading report").assertCountEquals(0)
    }

    @Composable
    private fun HeaderCard(
        loading: Boolean,
        summary: List<SummaryBlock> = emptyList(),
    ) {
        val cell = if (loading) loadingCell else ValueWithIcons("+1.00°C", null)
        StatusHeaderCard(
            imageRes = R.drawable.warm,
            summary = summary,
            statusCardValue = cell,
            trendCardValue = cell,
            trendLabel = "Forecast of 7 days",
            shape = shape,
            pictureLoading = loading,
            summaryLoading = loading,
        )
    }

    // The default config is what tests and previews get.

    @Test
    fun defaultConfig_hasNoDelayAndNoAnimation() {
        var config: LoadingConfig? = null
        composeTestRule.setContent { config = LocalLoadingConfig.current }

        assertEquals(LoadingConfig(delayMs = 0, animated = false), config)
    }

    @Test
    fun headerCard_withDefaultConfig_showsEveryPlaceholderAtOnce() {
        composeTestRule.setContent { FineWeatherTheme { HeaderCard(loading = true) } }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading report").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Loading").assertCountEquals(2)
        composeTestRule.onNodeWithText("Forecast of 7 days").assertIsDisplayed()
        composeTestRule.onNodeWithText("This month so far").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Status illustration").assertDoesNotExist()
        composeTestRule.onNodeWithText("…").assertDoesNotExist()
        composeTestRule.onNodeWithText("Loading…").assertDoesNotExist()
    }

    @Test
    fun headerCard_withLoadedContent_replacesThePlaceholders() {
        var loading by mutableStateOf(true)
        show {
            HeaderCard(loading = loading, summary = listOf(SummaryBlock.Detail("It was warm.")))
        }
        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()

        loading = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Status illustration").assertIsDisplayed()
        composeTestRule.onNodeWithText("It was warm.").assertIsDisplayed()
        assertNoPlaceholders()
    }

    @Test
    fun headerCard_spinnerSitsInTheMiddleOfThePictureBox() {
        show { HeaderCard(loading = true) }

        val box = bounds("statusPicture")
        val spinner = composeTestRule.onNodeWithContentDescription("Loading picture").getUnclippedBoundsInRoot()
        assertTrue(abs((box.left + box.right - spinner.left - spinner.right).value) < 1f)
        assertTrue(abs((box.top + box.bottom - spinner.top - spinner.bottom).value) < 1f)
        assertTrue(spinner.right - spinner.left < box.right - box.left)
    }

    // The grace period: blank at the same size, then the placeholder.

    @Test
    fun headerCard_withDelay_staysBlankAtTheSameSizeUntilTheDelayIsOver() {
        show(delayed, paused = true) { HeaderCard(loading = true) }

        advance(800)
        assertNoPlaceholders()
        val summary = bounds("statusSummary")

        advance(400)
        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading report").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Loading").assertCountEquals(2)
        assertEquals(summary, bounds("statusSummary"))
    }

    @Test
    fun headerCard_loadedBeforeTheDelay_neverShowsAPlaceholder() {
        var loading by mutableStateOf(true)
        show(delayed, paused = true) { HeaderCard(loading = loading, summary = listOf(SummaryBlock.Detail("Done."))) }

        advance(300)
        loading = false
        advance(0)
        advance(2000)

        composeTestRule.onNodeWithText("Done.").assertIsDisplayed()
        assertNoPlaceholders()
    }

    @Test
    fun headerCard_loadingAgain_startsTheDelayAgain() {
        var loading by mutableStateOf(true)
        show(delayed, paused = true) { HeaderCard(loading = loading, summary = listOf(SummaryBlock.Detail("Done."))) }
        advance(300)
        loading = false
        advance(300)
        loading = true
        advance(0)

        advance(800)
        assertNoPlaceholders()
        advance(400)
        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()
    }

    @Test
    fun statusRow_withDelay_showsItsSpinnerOnlyAfterTheDelay() {
        show(delayed, paused = true) { StatusTrendRow("Trend", loadingCell, Modifier.testTag("cell")) }

        advance(800)
        assertNoPlaceholders()
        composeTestRule.onNodeWithText("Trend").assertIsDisplayed()
        val cell = bounds("cell")

        advance(400)
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        assertEquals(cell, bounds("cell"))
    }

    // The two parts of the report

    private val verdict = SummaryBlock.Headline("It was warm.", Band.WARM)
    private val outlook = SummaryBlock.Detail("Next 7 days: about as warm as usual.")
    private val caveat = SummaryBlock.Caveat("Only 3 days so far, so this can change quickly.")

    @Test
    fun headerCard_putsTheVerdictBesideThePictureAndTheRestBeneathIt() {
        show { HeaderCard(loading = false, summary = listOf(verdict, outlook, caveat)) }

        listOf(verdict, outlook, caveat).forEach { composeTestRule.onNodeWithText(it.text).assertIsDisplayed() }
        val picture = bounds("statusPicture")
        val summary = bounds("statusSummary")
        val details = bounds("statusDetails")
        assertTrue(summary.left >= picture.right)
        assertTrue(details.top >= picture.bottom)
        assertTrue(details.right - details.left > summary.right - summary.left)
    }

    @Test
    fun headerCard_withOnlyAVerdict_hasNothingBeneathThePicture() {
        show { HeaderCard(loading = false, summary = listOf(verdict)) }

        composeTestRule.onNodeWithText(verdict.text).assertIsDisplayed()
        composeTestRule.onNodeWithTag("statusDetails").assertDoesNotExist()
    }

    @Test
    fun headerCard_whileLoading_holdsThePlaceOfBothParts() {
        show { HeaderCard(loading = true) }

        composeTestRule.onAllNodesWithContentDescription("Loading report").assertCountEquals(1)
        composeTestRule.onNodeWithTag("statusSummary").assertIsDisplayed()
        composeTestRule.onNodeWithTag("statusDetails").assertIsDisplayed()
    }

    @Test
    fun headerCard_withDelay_keepsBothPartsAtTheSameSizeUntilTheDelayIsOver() {
        show(delayed, paused = true) { HeaderCard(loading = true) }

        advance(800)
        val summary = bounds("statusSummary")
        val details = bounds("statusDetails")

        advance(400)
        composeTestRule.onNodeWithContentDescription("Loading report").assertIsDisplayed()
        assertEquals(summary, bounds("statusSummary"))
        assertEquals(details, bounds("statusDetails"))
    }

    // The Month and Year cards

    private val today = LocalDate.of(2026, 10, 14)

    private fun series(start: LocalDate, end: LocalDate, value: Double): DailySeries {
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        return DailySeries.fromApi(start, end, dates.map { it.toString() }, dates.map { value })
    }

    private fun snapshot() =
        ClimateSnapshot(
            series = series(LocalDate.of(2026, 1, 1), today.minusDays(1), 12.0),
            reference =
                ReferenceClimate(series(LocalDate.of(1961, 1, 1), LocalDate.of(1990, 12, 31), 8.0), 1961, 1990),
            today = today,
            forecast = null,
        )

    @Test
    fun monthCard_whileLoading_keepsItsTitleRowAndShowsASpinner() {
        show { MonthView(null, 7, "1961–1990", shape, cardColors, loading = true) }

        composeTestRule.onNodeWithText("Daily temperatures").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthInfoButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("monthWindow_Month").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun yearCard_whileLoading_keepsItsTitleAndShowsASpinner() {
        show { YearView(null, "1961–1990", shape, cardColors, loading = true) }

        composeTestRule.onNodeWithText("Months of the year").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun monthCard_whenTheSnapshotArrives_replacesTheSpinner() {
        var data by mutableStateOf<ClimateSnapshot?>(null)
        show { MonthView(data, 7, "1961–1990", shape, cardColors, loading = data == null) }
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()

        data = snapshot()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("monthSummary").assertIsDisplayed()
        assertNoPlaceholders()
    }

    @Test
    fun yearCard_whenTheSnapshotArrives_replacesTheSpinner() {
        var data by mutableStateOf<ClimateSnapshot?>(null)
        show { YearView(data, "1961–1990", shape, cardColors, loading = data == null) }
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()

        data = snapshot()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("yearSummary").assertIsDisplayed()
        assertNoPlaceholders()
    }

    @Test
    fun monthCard_withDelay_staysBlankAtTheSameSizeUntilTheDelayIsOver() {
        show(delayed, paused = true) { MonthView(null, 7, "1961–1990", shape, cardColors, loading = true) }

        advance(800)
        assertNoPlaceholders()
        val card = bounds("monthView")

        advance(400)
        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        assertEquals(card, bounds("monthView"))
    }

    @Test
    fun monthAndYearCards_afterAFailure_stillSayError() {
        show {
            MonthView(null, 7, "1961–1990", shape, cardColors, failed = true)
            YearView(null, "1961–1990", shape, cardColors, failed = true)
        }

        composeTestRule.onAllNodesWithText("Error").assertCountEquals(2)
        assertNoPlaceholders()
    }

    // What the app runs with: indeterminate spinners and pulsing bars.

    @Test
    fun animatedConfig_showsAllPlaceholdersAfterTheDelayAndKeepsRunning() {
        show(LoadingConfig(delayMs = 150, animated = true), paused = true) {
            HeaderCard(loading = true)
        }

        advance(100)
        assertNoPlaceholders()
        advance(200)
        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Loading report").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Loading").assertCountEquals(2)
        repeat(20) { advance(100) }
        composeTestRule.onNodeWithContentDescription("Loading picture").assertIsDisplayed()
    }

    // rememberDelayedFlag

    @Composable
    private fun Flag(active: Boolean) {
        Text(if (rememberDelayedFlag(active, delayMs = 500)) "on" else "off")
    }

    @Test
    fun delayedFlag_withoutDelay_isOnInTheFirstComposition() {
        var first: Boolean? = null
        show(paused = true) {
            val flag = rememberDelayedFlag(active = true, delayMs = 0)
            if (first == null) first = flag
        }

        assertEquals(true, first)
    }

    @Test
    fun delayedFlag_turnsOnAfterTheDelayAndOffAtOnce() {
        var active by mutableStateOf(true)
        show(paused = true) { Flag(active) }

        advance(300)
        composeTestRule.onNodeWithText("off").assertIsDisplayed()
        advance(300)
        composeTestRule.onNodeWithText("on").assertIsDisplayed()

        active = false
        advance(0)
        composeTestRule.onNodeWithText("off").assertIsDisplayed()
    }

    @Test
    fun delayedFlag_startsTheDelayAgainWhenActiveTurnsOnAgain() {
        var active by mutableStateOf(false)
        show(paused = true) { Flag(active) }

        advance(3000)
        composeTestRule.onNodeWithText("off").assertIsDisplayed()

        active = true
        advance(0) // the frame that starts the delay
        advance(300)
        composeTestRule.onNodeWithText("off").assertIsDisplayed()
        advance(300)
        composeTestRule.onNodeWithText("on").assertIsDisplayed()
    }

    @Test
    fun delayedFlag_staysOffWhenActiveEndsBeforeTheDelay() {
        var active by mutableStateOf(true)
        show(paused = true) { Flag(active) }

        advance(300)
        active = false
        advance(300)
        active = true
        advance(0) // the frame that starts the delay again
        advance(300) // 900 ms since the start, but only 316 since the restart
        composeTestRule.onNodeWithText("off").assertIsDisplayed()
        advance(300)
        composeTestRule.onNodeWithText("on").assertIsDisplayed()
    }
}
