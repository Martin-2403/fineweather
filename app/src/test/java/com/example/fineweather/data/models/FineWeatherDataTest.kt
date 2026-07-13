package com.example.fineweather.data.models

import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.local.entities.toUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FineWeatherDataTest {

    @Test
    fun toEntity_mapsForecastAndHistoricCurrent() {
        val data = FineWeatherData("Berlin", Pair(1.0, 2.0), "loc-1", "DE")
        data.currentAverage = 10.0
        data.currentMonthAverage = 11.0
        data.forecastAverage = 12.0
        data.historicMonthlyAverage = 9.0
        data.historicTimePeriodAverage = 8.0
        data.currentTimePeriod = 10
        data.forecastTimePeriod = 20
        data.historicTimePeriod = 30

        val entity = data.toEntity(locationId = 1)

        assertEquals("loc-1", entity.id)
        assertEquals("Berlin", entity.locationName)
        assertEquals("DE", entity.country)
        assertEquals(10.0, entity.currentAverage ?: Double.NaN, 0.0001)
        assertEquals(11.0, entity.currentMonthAverage ?: Double.NaN, 0.0001)
        assertNull(entity.forecastAverage7)
        assertEquals(12.0, entity.forecastAverage14 ?: Double.NaN, 0.0001)
        assertEquals(data.timestamp, entity.forecastDate14)
        assertEquals(9.0, entity.historicMonthlyAverageCurrent ?: Double.NaN, 0.0001)
        assertNull(entity.historicMonthlyAverageClassic)
        assertEquals(8.0, data.historicTimePeriodAverage ?: Double.NaN, 0.0001)
        assertEquals(10, data.currentTimePeriod)
        assertEquals(20, data.forecastTimePeriod)
        assertEquals(30, data.historicTimePeriod)
    }

    @Test
    fun toUiModel_prefersCurrentHistoricAndForecast() {
        val entity =
            WeatherEntity(
                id = "loc-2",
                date = "2026-03-01",
                locationName = "Paris",
                country = "FR",
                latitude = 48.0,
                longitude = 2.0,
                currentAverage = 7.0,
                currentMonthAverage = 8.0,
                forecastAverage7 = 9.0,
                forecastAverage14 = null,
                forecastDate7 = "2026-03-01",
                forecastDate14 = null,
                historicMonthlyAverageClassic = 5.0,
                historicMonthlyAverageCurrent = 6.0,
            )

        val data = entity.toUiModel()

        assertEquals("Paris", data.location)
        assertEquals(Pair(48.0, 2.0), data.coordinates)
        assertEquals(7.0, data.currentAverage ?: Double.NaN, 0.0001)
        assertEquals(8.0, data.currentMonthAverage ?: Double.NaN, 0.0001)
        assertEquals(9.0, data.forecastAverage ?: Double.NaN, 0.0001)
        assertEquals(6.0, data.historicMonthlyAverage ?: Double.NaN, 0.0001)
    }

    @Test
    fun tempDifference_isNullWhenMissingData() {
        val data = FineWeatherData("Rome", Pair(1.0, 2.0), "loc-3")
        assertNull(data.tempDifference)
    }

    @Test
    fun tempDifference_returnsDifferenceWhenAvailable() {
        val data = FineWeatherData("Rome", Pair(1.0, 2.0), "loc-3")
        data.currentMonthAverage = 12.0
        data.historicMonthlyAverage = 10.0
        assertEquals(2.0, data.tempDifference ?: Double.NaN, 0.0001)
    }
}
