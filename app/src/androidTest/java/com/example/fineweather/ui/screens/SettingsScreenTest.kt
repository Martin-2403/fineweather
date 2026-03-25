package com.example.fineweather.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.example.fineweather.ui.theme.FineWeatherTheme
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

        composeTestRule.setContent {
            FineWeatherTheme {
                SettingsScreen(
                    forecastDays = daysState.value,
                    onForecastDaysChange = { daysState.value = it },
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
}
