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
                query = "berlin",
                locationId = "loc-1",
                name = "Berlin",
                country = "Germany",
                latitude = 52.52,
                longitude = 13.405,
            )
        coEvery { dao.getByQuery("berlin") } returns cached

        val result = repository.getGeoCode("Berlin")

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
        coEvery { dao.getByQuery("munich") } returns null
        coEvery { api.getGeoCoding(name = "Munich", count = any(), language = any(), format = any()) } returns response

        val result = repository.getGeoCode("Munich")

        assertEquals("Munich", result.location)
        assertEquals("Germany", result.country)
        assertEquals(Pair(48.137, 11.575), result.coordinates)
        assertTrue(!repository.wasLastLookupFromCache())
        coVerify(exactly = 1) { dao.insert(match { it.query == "munich" && it.locationId == "loc-2" }) }
    }
}
