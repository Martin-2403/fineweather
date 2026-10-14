package com.example.fineweather.data.repositories

import com.example.fineweather.data.local.dao.ForecastSeriesDao
import com.example.fineweather.data.local.dao.GeoCodeDao
import com.example.fineweather.data.local.dao.ReferenceClimateDao
import com.example.fineweather.data.local.dao.WeatherDao
import com.example.fineweather.data.local.dao.YearSeriesDao
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheRepositoryTest {

    private val weatherDao = mockk<WeatherDao>(relaxed = true)
    private val geoCodeDao = mockk<GeoCodeDao>(relaxed = true)
    private val referenceDao = mockk<ReferenceClimateDao>(relaxed = true)
    private val yearSeriesDao = mockk<YearSeriesDao>(relaxed = true)
    private val forecastDao = mockk<ForecastSeriesDao>(relaxed = true)

    @Test
    fun stats_combinesWeatherAndSearchCounts() = runTest {
        every { referenceDao.observeLocationCount() } returns flowOf(3)
        every { geoCodeDao.observeSearchCount() } returns flowOf(7)

        val stats = RoomCacheRepository(weatherDao, geoCodeDao, referenceDao, yearSeriesDao, forecastDao).stats.first()

        assertEquals(CacheStats(weatherEntries = 3, searchEntries = 7), stats)
    }

    @Test
    fun clear_clearsBothCaches() = runTest {
        every { referenceDao.observeLocationCount() } returns flowOf(0)
        every { geoCodeDao.observeSearchCount() } returns flowOf(0)

        RoomCacheRepository(weatherDao, geoCodeDao, referenceDao, yearSeriesDao, forecastDao).clear()

        coVerify(exactly = 1) { weatherDao.clearAll() }
        coVerify(exactly = 1) { geoCodeDao.clearAll() }
        coVerify(exactly = 1) { referenceDao.clearAll() }
        coVerify(exactly = 1) { yearSeriesDao.clearAll() }
        coVerify(exactly = 1) { forecastDao.clearAll() }
    }

    @Test
    fun cacheStats_isEmptyOnlyWhenBothZero() {
        assertTrue(CacheStats().isEmpty)
        assertFalse(CacheStats(weatherEntries = 1).isEmpty)
        assertFalse(CacheStats(searchEntries = 1).isEmpty)
    }
}
