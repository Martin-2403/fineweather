package com.example.fineweather

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompactTopBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun actionsAreShownAndClickable() {
        var clicks = 0
        composeTestRule.setContent {
            FineWeatherTheme {
                CompactTopBar(
                    title = "FineWeather",
                    actions =
                        listOf(
                            TopBarAction(R.drawable.favorites_list, "Open favorites", "favoritesButton") { clicks++ },
                        ),
                )
            }
        }

        composeTestRule.onNodeWithTag("favoritesButton").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun hintTrigger_pulsesTheIconOnceAndThenRests() {
        val trigger = mutableIntStateOf(0)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            FineWeatherTheme {
                CompactTopBar(
                    title = "FineWeather",
                    actions =
                        listOf(
                            TopBarAction(
                                R.drawable.favorites_list,
                                "Open favorites",
                                "favoritesButton",
                                hintTrigger = trigger.intValue,
                            ) {},
                        ),
                )
            }
        }
        val icon = composeTestRule.onNodeWithContentDescription("Open favorites", useUnmergedTree = true)
        val restingWidth = icon.getBoundsInRoot().width

        // The initial value is no hint.
        composeTestRule.mainClock.advanceTimeBy(300)
        assertEquals(restingWidth, icon.getBoundsInRoot().width)

        trigger.intValue = 1
        composeTestRule.mainClock.advanceTimeBy(250)
        assertTrue(icon.getBoundsInRoot().width > restingWidth)

        composeTestRule.mainClock.advanceTimeBy(2_000)
        assertEquals(restingWidth, icon.getBoundsInRoot().width)
    }

    @Test
    fun returningToTheTopBarWithAHandledTrigger_doesNotReplayTheHint() {
        val onWeather = mutableStateOf(true)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            FineWeatherTheme {
                if (onWeather.value) {
                    CompactTopBar(
                        title = "FineWeather",
                        actions =
                            listOf(
                                TopBarAction(
                                    R.drawable.favorites_list,
                                    "Open favorites",
                                    "favoritesButton",
                                    hintTrigger = 1,
                                ) {},
                            ),
                    )
                } else {
                    CompactTopBar(title = "Settings", actions = emptyList())
                }
            }
        }
        val icon = composeTestRule.onNodeWithContentDescription("Open favorites", useUnmergedTree = true)
        val restingWidth = icon.getBoundsInRoot().width

        onWeather.value = false
        composeTestRule.mainClock.advanceTimeBy(100)
        onWeather.value = true
        composeTestRule.mainClock.advanceTimeBy(250)

        assertEquals(restingWidth, icon.getBoundsInRoot().width)
    }
}
