package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.local.doa.GeoCodeDao
import com.example.fineweather.data.local.entities.GeoCodeEntity
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
            GeoCodeEntity(
                query = "en|berlin",
                locationId = "loc-1",
                name = "Berlin",
                country = "Germany",
                latitude = 52.52,
                longitude = 13.405,
            )
        coEvery { dao.getByQuery("en|berlin") } returns cached

        val result = repository.getGeoCode("Berlin", "en")

        assertEquals("Berlin", result.location)
        assertEquals("Germany", result.country)
        assertEquals(Pair(52.52, 13.405), result.coordinates)
        assertTrue(repository.wasLastLookupFromCache())
        coVerify(exactly = 0) { api.getGeoCoding(any(), any(), any(), any()) }
    }

    @Test
    fun getGeoCode_fetchesAndStoresWhenCacheMissing() = runTest {
        val response =
            GeoCodingResponse(
                results =
                    listOf(
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
        coEvery { dao.getByQuery("en|munich") } returns null
        coEvery { dao.getByQuery("munich") } returns null
        coEvery { api.getGeoCoding(name = "Munich", count = any(), language = any(), format = any()) } returns response

        val result = repository.getGeoCode("Munich", "en")

        assertEquals("Munich", result.location)
        assertEquals("Germany", result.country)
        assertEquals(Pair(48.137, 11.575), result.coordinates)
        assertTrue(!repository.wasLastLookupFromCache())
        coVerify(exactly = 1) { dao.insert(match { it.query == "en|munich" && it.locationId == "loc-2" }) }
    }

    @Test
    fun getGeoCode_normalizesWhitespaceAndCasingForCacheLookup() = runTest {
        val cached =
            GeoCodeEntity(
                query = "en|new york",
                locationId = "loc-3",
                name = "New York",
                country = "USA",
                latitude = 40.71,
                longitude = -74.0,
            )
        coEvery { dao.getByQuery("en|new york") } returns cached

        val result = repository.getGeoCode("  NEW   york  ", "en")

        assertEquals("New York", result.location)
        assertEquals("USA", result.country)
        assertEquals(Pair(40.71, -74.0), result.coordinates)
        coVerify(exactly = 1) { dao.getByQuery("en|new york") }
    }

    @Test
    fun getGeoCode_usesFallbackWhenApiReturnsNullEntry() = runTest {
        @Suppress("UNCHECKED_CAST")
        val response =
            GeoCodingResponse(
                results = listOf(null) as List<GeocodingResult>,
                generationtime_ms = 0.0,
            )
        coEvery { dao.getByQuery("en|unknown") } returns null
        coEvery { dao.getByQuery("unknown") } returns null
        coEvery { api.getGeoCoding(name = "Unknown", count = any(), language = any(), format = any()) } returns response

        val result = repository.getGeoCode("Unknown", "en")

        assertEquals("n/a", result.location)
        assertEquals("n/a", result.id)
        assertEquals(Pair(0.0, 0.0), result.coordinates)
        coVerify(exactly = 1) { dao.insert(match { it.name == "n/a" && it.locationId == "n/a" }) }
    }

    @Test
    fun getGeoCode_throwsWhenApiFails() = runTest {
        coEvery { dao.getByQuery("en|rome") } returns null
        coEvery { dao.getByQuery("rome") } returns null
        coEvery { api.getGeoCoding(any(), any(), any(), any()) } throws RuntimeException("boom")

        val thrown = runCatching { repository.getGeoCode("Rome", "en") }.exceptionOrNull()

        assertTrue(thrown is Exception)
    }
}
