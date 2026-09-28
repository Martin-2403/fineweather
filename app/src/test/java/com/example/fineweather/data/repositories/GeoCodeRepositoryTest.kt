package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.local.dao.GeoCodeDao
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity
import com.example.fineweather.data.models.GeoCodingResponse
import com.example.fineweather.data.models.GeocodingResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCodeRepositoryTest {

    private val api = mockk<OpenMeteoGeoCodeApiService>(relaxed = true)
    private val dao = mockk<GeoCodeDao>(relaxed = true)
    private val repository = GeoCodeRepository(api, dao)

    @Test
    fun getGeoCode_returnsCachedResultWhenAvailable() = runTest {
        val cached =
            GeoCodeCacheEntity(
                query = "en|berlin",
                locationId = "loc-1",
                rank = 0,
                name = "Berlin",
                country = "Germany",
                admin1 = null,
                latitude = 52.52,
                longitude = 13.405,
            )
        coEvery { dao.getByQuery("en|berlin") } returns listOf(cached)

        val result = repository.getGeoCode("Berlin", "en")

        assertEquals("Berlin", result.primary.name)
        assertEquals("Germany", result.primary.country)
        assertEquals(52.52, result.primary.latitude, 0.0001)
        assertEquals(13.405, result.primary.longitude, 0.0001)
        assertEquals(1, result.candidates.size)
        assertEquals("Berlin", result.candidates.first().name)
        coVerify(exactly = 0) { api.getGeoCoding(any(), any(), any(), any()) }
    }

    @Test
    fun getGeoCode_fetchesAndStoresWhenCacheMissing() = runTest {
        val response =
            GeoCodingResponse(
                results =
                    listOf<GeocodingResult?>(
                        GeocodingResult(
                            id = "loc-2",
                            name = "Munich",
                            latitude = 48.137,
                            longitude = 11.575,
                            elevation = "",
                            feature_code = "",
                            country_code = "DE",
                            admin1_id = "",
                            admin2_id = "",
                            admin3_id = "",
                            admin4_id = "",
                            timezone = "",
                            population = 0,
                            country_id = 0,
                            country = "Germany",
                            admin1 = "",
                            admin2 = "",
                            admin3 = "",
                            admin4 = "",
                        ),
                    ),
                generationtime_ms = 0.0,
            )
        coEvery { dao.getByQuery("en|munich") } returns emptyList()
        coEvery { dao.getByQuery("munich") } returns emptyList()
        coEvery { api.getGeoCoding(name = "Munich", count = any(), language = any(), format = any()) } returns response

        val result = repository.getGeoCode("Munich", "en")

        assertEquals("Munich", result.primary.name)
        assertEquals("Germany", result.primary.country)
        assertEquals(48.137, result.primary.latitude, 0.0001)
        assertEquals(11.575, result.primary.longitude, 0.0001)
        assertEquals(1, result.candidates.size)
        assertEquals("Munich", result.candidates.first().name)
        coVerify(exactly = 1) { dao.deleteByQuery("en|munich") }
        coVerify(exactly = 1) {
            dao.insertAll(
                match {
                    it.size == 1 &&
                        it[0].query == "en|munich" &&
                        it[0].locationId == "loc-2" &&
                        it[0].rank == 0
                },
            )
        }
    }

    @Test
    fun getGeoCode_normalizesWhitespaceAndCasingForCacheLookup() = runTest {
        val cached =
            GeoCodeCacheEntity(
                query = "en|new york",
                locationId = "loc-3",
                rank = 0,
                name = "New York",
                country = "USA",
                admin1 = null,
                latitude = 40.71,
                longitude = -74.0,
            )
        coEvery { dao.getByQuery("en|new york") } returns listOf(cached)

        val result = repository.getGeoCode("  NEW   york  ", "en")

        assertEquals("New York", result.primary.name)
        assertEquals("USA", result.primary.country)
        assertEquals(40.71, result.primary.latitude, 0.0001)
        assertEquals(-74.0, result.primary.longitude, 0.0001)
        coVerify(exactly = 1) { dao.getByQuery("en|new york") }
    }

    @Test
    fun getGeoCode_usesFallbackWhenApiReturnsNullEntry() = runTest {
        @Suppress("UNCHECKED_CAST")
        val response =
            GeoCodingResponse(
                results = listOf(null),
                generationtime_ms = 0.0,
            )
        coEvery { dao.getByQuery("en|unknown") } returns emptyList()
        coEvery { dao.getByQuery("unknown") } returns emptyList()
        coEvery { api.getGeoCoding(name = "Unknown", count = any(), language = any(), format = any()) } returns response

        val result = repository.getGeoCode("Unknown", "en")

        assertEquals("n/a", result.primary.name)
        assertEquals("n/a", result.primary.id)
        assertEquals(0.0, result.primary.latitude, 0.0001)
        assertEquals(0.0, result.primary.longitude, 0.0001)
        coVerify(exactly = 1) {
            dao.insertAll(
                match {
                    it.size == 1 &&
                        it[0].name == "n/a" &&
                        it[0].locationId == "n/a"
                },
            )
        }
    }

    @Test
    fun getGeoCode_throwsWhenApiFails() = runTest {
        coEvery { dao.getByQuery("en|rome") } returns emptyList()
        coEvery { dao.getByQuery("rome") } returns emptyList()
        coEvery { api.getGeoCoding(any(), any(), any(), any()) } throws RuntimeException("boom")

        val thrown = runCatching { repository.getGeoCode("Rome", "en") }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }
}
