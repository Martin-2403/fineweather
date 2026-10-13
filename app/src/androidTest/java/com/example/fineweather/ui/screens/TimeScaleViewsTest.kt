package com.example.fineweather.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.fineweather.domain.ClimateSnapshot
import com.example.fineweather.domain.DailySeries
import com.example.fineweather.domain.ReferenceClimate
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimeScaleViewsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)

    private fun series(
        start: LocalDate,
        end: LocalDate,
        valueOf: (LocalDate) -> Double,
    ): DailySeries {
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        return DailySeries.fromApi(start, end, dates.map { it.toString() }, dates.map(valueOf))
    }

    private fun snapshot(withReference: Boolean = true) =
        ClimateSnapshot(
            series = series(LocalDate.of(2026, 1, 1), today.minusDays(1)) { 12.0 },
            reference =
                if (withReference) {
                    ReferenceClimate(
                        series(LocalDate.of(1961, 1, 1), LocalDate.of(1990, 12, 31)) { 8.0 },
                        1961,
                        1990,
                    )
                } else {
                    null
                },
            today = today,
        )

    private fun show(content: @Composable (RoundedCornerShape, CardColors) -> Unit) {
        composeTestRule.setContent {
            FineWeatherTheme {
                content(
                    RoundedCornerShape(12.dp),
                    CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }

    @Test
    fun monthView_withoutData_promptsForSearch() {
        show { shape, colors -> MonthView(null, "1961–1990", shape, colors) }

        composeTestRule.onNodeWithText("Search for a place to see its daily temperatures.").assertIsDisplayed()
    }

    @Test
    fun monthView_summarisesTheMonthAndSwitchesToThirtyDays() {
        show { shape, colors -> MonthView(snapshot(), "1961–1990", shape, colors) }

        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("13 days averaged 12.0°C", substring = true)
        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("+4.0°C compared with the same days of 1961–1990", substring = true)

        composeTestRule.onNodeWithText("Last 30 days").performClick()

        composeTestRule.onNodeWithTag("monthSummary").assertTextContains("30 days averaged 12.0°C", substring = true)
    }

    @Test
    fun yearView_listsEveryMonthAndSummarisesTheYear() {
        show { shape, colors -> YearView(snapshot(), "1961–1990", shape, colors) }

        composeTestRule.onNodeWithTag("yearSummary")
            .assertTextContains(
                "So far 2026 is +4.0°C compared with 1961–1990, with 10 of 10 months warmer than average.",
                substring = true,
            )
        composeTestRule.onNodeWithText("Jan").assertExists()
        composeTestRule.onNodeWithText("Oct*").assertExists()
    }

    @Test
    fun yearView_withoutReference_saysNoComparisonYet() {
        show { shape, colors -> YearView(snapshot(withReference = false), "1961–1990", shape, colors) }

        composeTestRule.onNodeWithText("No monthly comparison is available yet.").assertIsDisplayed()
    }

    @Test
    fun yearView_withoutData_promptsForSearch() {
        show { shape, colors -> YearView(null, "1961–1990", shape, colors) }

        composeTestRule.onNodeWithText("Search for a place to compare its months with the climate average.")
            .assertIsDisplayed()
    }
}

