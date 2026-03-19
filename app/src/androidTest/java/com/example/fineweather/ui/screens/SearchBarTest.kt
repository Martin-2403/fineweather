package com.example.fineweather.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clearButtonClearsSearchText() {
        val locationState = mutableStateOf("")

        composeTestRule.setContent {
            FineWeatherTheme {
                SearchBar(
                    location = locationState.value,
                    onLocationChange = { locationState.value = it },
                    onSearch = {},
                    onClear = { locationState.value = "" },
                )
            }
        }

        composeTestRule.onNodeWithTag("searchField").performTextInput("Berlin")
        composeTestRule.runOnIdle {
            assertEquals("Berlin", locationState.value)
        }

        composeTestRule.onNodeWithTag("clearButton").performClick()
        composeTestRule.runOnIdle {
            assertEquals("", locationState.value)
        }
    }

    @Test
    fun clearButtonIsVisible() {
        composeTestRule.setContent {
            FineWeatherTheme {
                SearchBar(
                    location = "",
                    onLocationChange = {},
                    onSearch = {},
                    onClear = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("clearButton").assertIsDisplayed()
    }
}
