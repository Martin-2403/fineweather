package com.example.fineweather.ui.screens

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.viewmodels.PlaceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show(
        location: String = "",
        canFavorite: Boolean = true,
        onLocationChange: (String) -> Unit = {},
        onSearch: () -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            FineWeatherTheme {
                SearchBar(
                    location = location,
                    onLocationChange = onLocationChange,
                    onSearch = onSearch,
                    onClear = onClear,
                    isFavorite = false,
                    canFavorite = canFavorite,
                    onToggleFavorite = {},
                )
            }
        }
    }

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
                    isFavorite = true,
                    canFavorite = true,
                    onToggleFavorite = { },
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
    fun searchButtonStartsTheSearch() {
        var searches = 0
        var clears = 0
        show(location = "Berlin", onSearch = { searches++ }, onClear = { clears++ })

        composeTestRule.onNodeWithTag("searchButton").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, searches)
            assertEquals(0, clears)
        }
    }

    @Test
    fun clearButtonDoesNotStartASearch() {
        var searches = 0
        var clears = 0
        show(location = "Berlin", onSearch = { searches++ }, onClear = { clears++ })

        composeTestRule.onNodeWithTag("clearButton").performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, searches)
            assertEquals(1, clears)
        }
    }

    @Test
    fun favoriteStarIsOnlyEnabledForAResolvedPlace() {
        show(canFavorite = false)
        composeTestRule.onNodeWithTag("favoriteButton").assertIsNotEnabled()
    }

    @Test
    fun favoriteStarIsEnabledWhenAPlaceIsResolved() {
        show(canFavorite = true)
        composeTestRule.onNodeWithTag("favoriteButton").assertIsEnabled()
    }

    @Test
    fun emptyFieldShowsThePlaceholder() {
        show(location = "")

        composeTestRule.onNodeWithText("City or place").assertIsDisplayed()
    }
}

class LocationStatusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun message_isShownWhenThereIsNoPlace() {
        composeTestRule.setContent {
            FineWeatherTheme { LocationStatus("City not found", null) }
        }

        composeTestRule.onNodeWithText("City not found").assertIsDisplayed()
    }

    @Test
    fun place_keepsTheNameOnOneLineAndTheCoordinatesVisibleEvenWhenTheNameIsTooLong() {
        composeTestRule.setContent {
            FineWeatherTheme {
                Surface(modifier = Modifier.width(260.dp)) {
                    LocationStatus(
                        status = "ignored",
                        label =
                            PlaceLabel(
                                name = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch, Gwynedd, United Kingdom",
                                coordinates = "53.22, -4.20",
                                locationDetails = "Gwynedd, United Kingdom",
                            ),
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("(53.22, -4.20)").assertIsDisplayed()
        val height = composeTestRule.onNodeWithTag("locationStatus").getBoundsInRoot().height
        assertTrue("status should stay on one line but was $height", height <= 40.dp)
    }

    @Test
    fun place_showsAdministrativeAreaAndCoordinatesWithoutCity() {
        composeTestRule.setContent {
            FineWeatherTheme {
                LocationStatus(
                    "ignored",
                    PlaceLabel(
                        name = "Augsburg, Bavaria, Germany",
                        coordinates = "48.37, 10.90",
                        locationDetails = "Bavaria, Germany",
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Bavaria, Germany").assertIsDisplayed()
        composeTestRule.onNodeWithText("Augsburg, Bavaria, Germany").assertDoesNotExist()
        composeTestRule.onNodeWithText("(48.37, 10.90)").assertIsDisplayed()
    }

    @Test
    fun alternativesLink_isOnlyShownWhenThereAreOtherMatchesAndOpensThem() {
        var opened = 0
        val label = PlaceLabel("Augsburg, Bavaria, Germany", "48.37, 10.90")
        val withLink = mutableStateOf(false)
        composeTestRule.setContent {
            FineWeatherTheme {
                LocationStatus("ignored", label, if (withLink.value) ({ opened++ }) else null)
            }
        }

        composeTestRule.onNodeWithTag("alternativesLink").assertDoesNotExist()

        withLink.value = true
        composeTestRule.onNodeWithText("Not your city?").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }
}
