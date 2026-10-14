package com.example.fineweather.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fineweather.data.local.dao.FavoriteDao
import com.example.fineweather.data.local.dao.ForecastSeriesDao
import com.example.fineweather.data.local.dao.ReferenceClimateDao
import com.example.fineweather.data.local.dao.YearSeriesDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import com.example.fineweather.data.local.entities.ForecastSeriesEntity
import com.example.fineweather.data.local.entities.ReferenceClimateEntity
import com.example.fineweather.data.local.entities.YearSeriesEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClimateCacheDaoTest {

    private lateinit var db: WeatherDatabase
    private lateinit var referenceDao: ReferenceClimateDao
    private lateinit var yearSeriesDao: YearSeriesDao
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var forecastDao: ForecastSeriesDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room
                .inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        referenceDao = db.referenceClimateDao()
        yearSeriesDao = db.yearSeriesDao()
        favoriteDao = db.favoriteDao()
        forecastDao = db.forecastSeriesDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun reference_roundTripsBlob() = runBlocking {
        val blob = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        referenceDao.insert(reference("loc-1", lastUsed = 1, data = blob))

        val loaded = referenceDao.get("loc-1", "1961_1990")

        assertNotNull(loaded)
        assertArrayEquals(blob, loaded!!.data)
        assertEquals(1961, loaded.firstYear)
        assertEquals(1990, loaded.lastYear)
        assertNull(referenceDao.get("loc-1", "current"))
        assertNull(referenceDao.get("other", "1961_1990"))
    }

    @Test
    fun reference_touchUpdatesLastUsed() = runBlocking {
        referenceDao.insert(reference("loc-1", lastUsed = 1))

        referenceDao.touch("loc-1", "1961_1990", now = 99)

        assertEquals(99L, referenceDao.get("loc-1", "1961_1990")!!.lastUsed)
    }

    @Test
    fun reference_evictKeepsMostRecentlyUsed() = runBlocking {
        (1..5).forEach { referenceDao.insert(reference("loc-$it", lastUsed = it.toLong())) }

        referenceDao.evictLeastRecentlyUsed(keep = 2)

        assertNull(referenceDao.get("loc-1", "1961_1990"))
        assertNull(referenceDao.get("loc-2", "1961_1990"))
        assertNull(referenceDao.get("loc-3", "1961_1990"))
        assertNotNull(referenceDao.get("loc-4", "1961_1990"))
        assertNotNull(referenceDao.get("loc-5", "1961_1990"))
    }

    @Test
    fun reference_evictNeverRemovesFavoritesAndDoesNotCountThem() = runBlocking {
        favoriteDao.upsert(favorite("loc-1"))
        (1..5).forEach { referenceDao.insert(reference("loc-$it", lastUsed = it.toLong())) }

        referenceDao.evictLeastRecentlyUsed(keep = 2)

        // loc-1 is the oldest, but a favorite: kept in addition to the 2 newest others
        assertNotNull(referenceDao.get("loc-1", "1961_1990"))
        assertNull(referenceDao.get("loc-2", "1961_1990"))
        assertNull(referenceDao.get("loc-3", "1961_1990"))
        assertNotNull(referenceDao.get("loc-4", "1961_1990"))
        assertNotNull(referenceDao.get("loc-5", "1961_1990"))
    }

    @Test
    fun reference_countAndClear() = runBlocking {
        referenceDao.insert(reference("loc-1", lastUsed = 1))
        referenceDao.insert(reference("loc-2", lastUsed = 2))
        assertEquals(2, referenceDao.observeCount().first())

        referenceDao.clearAll()

        assertEquals(0, referenceDao.observeCount().first())
    }

    @Test
    fun yearSeries_roundTripsAndReplaces() = runBlocking {
        yearSeriesDao.insert(yearSeries("loc-1", fetchedDate = "2026-10-12", data = byteArrayOf(1)))
        yearSeriesDao.insert(yearSeries("loc-1", fetchedDate = "2026-10-13", data = byteArrayOf(2)))

        val loaded = yearSeriesDao.get("loc-1")

        assertEquals("2026-10-13", loaded!!.fetchedDate)
        assertArrayEquals(byteArrayOf(2), loaded.data)
    }

    @Test
    fun yearSeries_cleanOutdatedKeepsFavoritesAndRecentRows() = runBlocking {
        favoriteDao.upsert(favorite("fav"))
        yearSeriesDao.insert(yearSeries("old", fetchedDate = "2026-09-01"))
        yearSeriesDao.insert(yearSeries("fav", fetchedDate = "2026-09-01"))
        yearSeriesDao.insert(yearSeries("recent", fetchedDate = "2026-10-12"))

        yearSeriesDao.cleanOutdated("2026-10-06")

        assertNull(yearSeriesDao.get("old"))
        assertNotNull(yearSeriesDao.get("fav"))
        assertNotNull(yearSeriesDao.get("recent"))
    }

    @Test
    fun yearSeries_clearAll() = runBlocking {
        yearSeriesDao.insert(yearSeries("loc-1", fetchedDate = "2026-10-13"))

        yearSeriesDao.clearAll()

        assertNull(yearSeriesDao.get("loc-1"))
    }

    @Test
    fun reference_locationCountCountsEachPlaceOnce() = runBlocking {
        referenceDao.insert(reference("loc-1", lastUsed = 1))
        referenceDao.insert(reference("loc-1", lastUsed = 2).copy(referenceId = "current"))
        referenceDao.insert(reference("loc-2", lastUsed = 3))

        assertEquals(2, referenceDao.observeLocationCount().first())
        assertEquals(3, referenceDao.observeCount().first())
    }

    @Test
    fun forecast_roundTripsAndReplaces() = runBlocking {
        forecastDao.insert(ForecastSeriesEntity("loc-1", "2026-10-13", "2026-10-26", byteArrayOf(1), "2026-10-13"))
        forecastDao.insert(ForecastSeriesEntity("loc-1", "2026-10-14", "2026-10-27", byteArrayOf(2), "2026-10-14"))

        val loaded = forecastDao.get("loc-1")

        assertEquals("2026-10-14", loaded!!.startDate)
        assertArrayEquals(byteArrayOf(2), loaded.data)
        assertNull(forecastDao.get("other"))
    }

    @Test
    fun forecast_cleanOutdatedRemovesEarlierDaysEvenForFavorites() = runBlocking {
        favoriteDao.upsert(favorite("fav"))
        forecastDao.insert(ForecastSeriesEntity("fav", "2026-10-13", "2026-10-26", byteArrayOf(1), "2026-10-13"))
        forecastDao.insert(ForecastSeriesEntity("today", "2026-10-14", "2026-10-27", byteArrayOf(1), "2026-10-14"))

        forecastDao.cleanOutdated("2026-10-14")

        assertNull(forecastDao.get("fav"))
        assertNotNull(forecastDao.get("today"))
    }

    @Test
    fun forecast_clearAll() = runBlocking {
        forecastDao.insert(ForecastSeriesEntity("loc-1", "2026-10-14", "2026-10-27", byteArrayOf(1), "2026-10-14"))

        forecastDao.clearAll()

        assertNull(forecastDao.get("loc-1"))
    }

    private fun reference(
        locationId: String,
        lastUsed: Long,
        data: ByteArray = byteArrayOf(0),
    ) = ReferenceClimateEntity(locationId, "1961_1990", 1961, 1990, data, lastUsed)

    private fun yearSeries(
        locationId: String,
        fetchedDate: String,
        data: ByteArray = byteArrayOf(0),
    ) = YearSeriesEntity(locationId, "2026-01-01", "2026-10-12", data, fetchedDate)

    private fun favorite(id: String) =
        FavoritePlaceEntity(
            id = id,
            name = "Place $id",
            country = null,
            admin1 = null,
            latitude = 0.0,
            longitude = 0.0,
            addedAt = 0L,
        )
}
