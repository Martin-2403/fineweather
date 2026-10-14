package com.example.fineweather.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.fineweather.ui.theme.FineWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InfoOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun closeButtonInTheTopRightDismissesTheOverlay() {
        var dismissed = 0
        composeTestRule.setContent {
            FineWeatherTheme { InfoOverlay(onDismiss = { dismissed++ }) }
        }

        composeTestRule.onNodeWithTag("infoCloseButton").assertIsDisplayed().performClick()

        assertEquals(1, dismissed)
    }
}
