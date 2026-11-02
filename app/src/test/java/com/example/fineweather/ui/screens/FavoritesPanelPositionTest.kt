package com.example.fineweather.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesPanelPositionTest {
    @Test
    fun panelLeft_putsThePanelAtTheEndOfTheWindowWithTheMargin() {
        assertEquals(1080 - 800 - 24, panelLeft(windowWidth = 1080, panelWidth = 800, edgeMargin = 24, rtl = false))
    }

    @Test
    fun panelLeft_neverGoesPastTheLeftMarginWhenThePanelIsWiderThanTheWindow() {
        assertEquals(24, panelLeft(windowWidth = 600, panelWidth = 900, edgeMargin = 24, rtl = false))
    }

    @Test
    fun panelLeft_isAtTheStartInRightToLeftLayouts() {
        assertEquals(24, panelLeft(windowWidth = 1080, panelWidth = 800, edgeMargin = 24, rtl = true))
    }
}
