package com.example.fineweather.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fineweather.data.local.doa.WeatherDao
import com.example.fineweather.data.local.entities.WeatherEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherDaoTest {

    private lateinit var db: WeatherDatabase
    private lateinit var dao: WeatherDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room
                .inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.weatherDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getWeatherById_returnsMatch() = runBlocking {
        val entity = buildEntity(id = "loc-1", date = "2026-03-16")
        dao.insertWeather(entity)

        val result = dao.getWeatherById("loc-1")

        assertNotNull(result)
        assertEquals("2026-03-16", result?.date)
    }

    @Test
    fun cleanOutdatedCache_removesOlderThanMinDate() = runBlocking {
        dao.insertWeather(buildEntity(id = "loc-1", date = "2026-03-01"))
        dao.insertWeather(buildEntity(id = "loc-2", date = "2026-03-10"))
        dao.insertWeather(buildEntity(id = "loc-3", date = "2026-03-16"))

        dao.cleanOutdatedCache("2026-03-09")

        assertNull(dao.getWeatherById("loc-1"))
        assertNotNull(dao.getWeatherById("loc-2"))
        assertNotNull(dao.getWeatherById("loc-3"))
    }

    private fun buildEntity(
        id: String,
        date: String,
    ): WeatherEntity =
        WeatherEntity(
            id = id,
            date = date,
            locationName = "Test City",
            country = "TC",
            latitude = 1.0,
            longitude = 2.0,
            currentAverage = 10.0,
            currentMonthAverage = 11.0,
            forecastAverage = 12.0,
            historicMonthlyAverage = 9.0,
        )
}
