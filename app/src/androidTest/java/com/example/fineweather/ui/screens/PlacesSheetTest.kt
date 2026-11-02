package com.example.fineweather.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlacesSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val augsburg = GeoPlace("1", "Augsburg", "Germany", "Bavaria", 48.37, 10.90)
    private val berlin = GeoPlace("2", "Berlin", "Germany", "Berlin", 52.52, 13.40)

    @Test
    fun listsThePlacesAndReportsTheSelectedOne() {
        var selected: GeoPlace? = null
        composeTestRule.setContent {
            FineWeatherTheme {
                PlacesSheet(
                    title = "Other matches",
                    places = listOf(augsburg, berlin),
                    emptyMessage = "none",
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Other matches").assertIsDisplayed()
        composeTestRule.onNodeWithText("Berlin").performClick()

        assertEquals(berlin, selected)
    }

    @Test
    fun withoutPlaces_showsTheEmptyMessage() {
        composeTestRule.setContent {
            FineWeatherTheme {
                PlacesSheet(
                    title = "Other matches",
                    places = emptyList(),
                    emptyMessage = "No other matches.",
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No other matches.").assertIsDisplayed()
    }
}
