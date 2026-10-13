package com.example.fineweather.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import com.example.fineweather.data.models.HistoricReference
import com.example.fineweather.ui.theme.FineWeatherTheme
import com.example.fineweather.data.repositories.CacheStats
import com.example.fineweather.data.repositories.SettingsDefaults
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toggleSwitch_updatesForecastDays() {
        val daysState = mutableStateOf(SettingsDefaults.FORECAST_DAYS_SHORT)
        val referenceState = mutableStateOf(HistoricReference.CLASSIC)
        val languageState = mutableStateOf(SettingsDefaults.DEFAULT_SEARCH_LANGUAGE)
        val comparisonModeState = mutableStateOf(SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE)

        composeTestRule.setContent {
            FineWeatherTheme {
                SettingsScreen(
                    forecastDays = daysState.value,
                    historicReference = referenceState.value,
                    searchLanguage = languageState.value,
                    temperatureComparisonMode = comparisonModeState.value,
                    onForecastDaysChange = { daysState.value = it },
                    onHistoricReferenceChange = { referenceState.value = it },
                    onSearchLanguageChange = { languageState.value = it },
                    onTemperatureComparisonModeChange = { comparisonModeState.value = it },
                    cacheStats = CacheStats(),
                    onClearCache = {},
                )
            }
        }

        composeTestRule.onNodeWithText("7 days").assertIsDisplayed()
        composeTestRule.onNodeWithTag("forecastDaysToggle").performClick()

        composeTestRule.onNodeWithText("14 days").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(SettingsDefaults.FORECAST_DAYS_LONG, daysState.value)
        }
    }

    @Test
    fun dropdown_updatesHistoricReference() {
        val daysState = mutableStateOf(SettingsDefaults.FORECAST_DAYS_SHORT)
        val referenceState = mutableStateOf(HistoricReference.CLASSIC)
        val languageState = mutableStateOf(SettingsDefaults.DEFAULT_SEARCH_LANGUAGE)
        val comparisonModeState = mutableStateOf(SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE)
        val targetLabel = "Current reference: ${HistoricReference.CURRENT.displayLabel()}"

        composeTestRule.setContent {
            FineWeatherTheme {
                SettingsScreen(
                    forecastDays = daysState.value,
                    historicReference = referenceState.value,
                    searchLanguage = languageState.value,
                    temperatureComparisonMode = comparisonModeState.value,
                    onForecastDaysChange = { daysState.value = it },
                    onHistoricReferenceChange = { referenceState.value = it },
                    onSearchLanguageChange = { languageState.value = it },
                    onTemperatureComparisonModeChange = { comparisonModeState.value = it },
                    cacheStats = CacheStats(),
                    onClearCache = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("historicReferenceDropdown").performClick()
        composeTestRule.onNodeWithText(targetLabel, substring = true).performClick()

        composeTestRule.runOnIdle {
            assertEquals(HistoricReference.CURRENT, referenceState.value)
        }
    }

    @Test
    fun cacheCard_showsStatsAndClearsOnClick() {
        var clearCalls = 0

        composeTestRule.setContent {
            FineWeatherTheme {
                SettingsScreen(
                    forecastDays = SettingsDefaults.FORECAST_DAYS_SHORT,
                    historicReference = HistoricReference.CLASSIC,
                    searchLanguage = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
                    temperatureComparisonMode = SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
                    onForecastDaysChange = {},
                    onHistoricReferenceChange = {},
                    onSearchLanguageChange = {},
                    onTemperatureComparisonModeChange = {},
                    cacheStats = CacheStats(weatherEntries = 2, searchEntries = 1),
                    onClearCache = { clearCalls++ },
                )
            }
        }

        composeTestRule.onNodeWithTag("cacheSummary").performScrollTo()
        composeTestRule.onNodeWithText("2 locations and 1 search cached").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clearCacheButton").performScrollTo().assertIsEnabled().performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, clearCalls)
        }
    }

    @Test
    fun cacheCard_disablesClearWhenEmpty() {
        composeTestRule.setContent {
            FineWeatherTheme {
                SettingsScreen(
                    forecastDays = SettingsDefaults.FORECAST_DAYS_SHORT,
                    historicReference = HistoricReference.CLASSIC,
                    searchLanguage = SettingsDefaults.DEFAULT_SEARCH_LANGUAGE,
                    temperatureComparisonMode = SettingsDefaults.DEFAULT_TEMPERATURE_COMPARISON_MODE,
                    onForecastDaysChange = {},
                    onHistoricReferenceChange = {},
                    onSearchLanguageChange = {},
                    onTemperatureComparisonModeChange = {},
                    cacheStats = CacheStats(),
                    onClearCache = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cache is empty").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("clearCacheButton").performScrollTo().assertIsNotEnabled()
    }
}
