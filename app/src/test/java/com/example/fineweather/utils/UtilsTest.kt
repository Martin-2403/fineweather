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
}
