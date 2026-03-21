package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoArchiveApiService
import com.example.fineweather.api.OpenMeteoWeatherApiService
import com.example.fineweather.data.local.doa.WeatherDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeatherRepositoryTest {

    private val weatherApi = mockk<OpenMeteoWeatherApiService>(relaxed = true)
    private val archiveApi = mockk<OpenMeteoArchiveApiService>(relaxed = true)
    private val weatherDao = mockk<WeatherDao>(relaxed = true)
    private val repository = WeatherRepository(weatherApi, archiveApi, weatherDao)

    @Test
    fun buildHistoricDateRange_usesExpectedYearsAndMonth() {
        val range = repository.buildHistoricDateRange(
            timeSpan = 30,
            now = LocalDate.of(2026, 3, 21),
        )

        assertEquals("1996-03-01", range.start)
        assertEquals("2025-03-31", range.end)
    }

    @Test
    fun buildHistoricDateRange_handlesLeapYearFebruary() {
        val range = repository.buildHistoricDateRange(
            timeSpan = 1,
            now = LocalDate.of(2025, 2, 15),
        )

        assertEquals("2024-02-01", range.start)
        assertEquals("2024-02-29", range.end)
    }
}
