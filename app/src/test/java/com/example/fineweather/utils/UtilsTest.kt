package com.example.fineweather.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class UtilsTest {

    @Test
    fun formatDouble_formatsToTwoDecimals() {
        assertEquals("12.35", formatDouble(12.345))
        assertEquals("N/A", formatDouble(null))
    }

    @Test
    fun getCurrentMonthString_usesTwoDigitMonth() {
        assertEquals("03", getCurrentMonthString(LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun getCurrentMonthName_usesLocale() {
        val original = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("March", getCurrentMonthName(LocalDate.of(2026, 3, 10)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun formatCoordinates_usesTwoDecimalsWithDotSeparator() {
        assertEquals("48.37, 10.90", formatCoordinates(48.37154, 10.89851))
        assertEquals("-33.87, 151.21", formatCoordinates(-33.8688, 151.2093))
    }
}
