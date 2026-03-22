package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.local.doa.GeoCodeDao
import com.example.fineweather.data.local.entities.GeoCodeEntity
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeocodingResult
import java.util.Locale

class GeoCodeRepository(
    private val openMeteoGeoCodeApi: OpenMeteoGeoCodeApiService,
    private val geoCodeDao: GeoCodeDao,
) {
    private var cachedGeoCodeData: GeocodingResult? = null
    private var lastLookupFromCache: Boolean = false

    suspend fun getGeoCode(name: String): FineWeatherData {
        try {
            val normalized = normalizeQuery(name)
            geoCodeDao.getByQuery(normalized)?.let { cached ->
                lastLookupFromCache = true
                return FineWeatherData(
                    cached.name,
                    Pair(cached.latitude, cached.longitude),
                    cached.locationId,
                    cached.country,
                )
            }

            cachedGeoCodeData =
                openMeteoGeoCodeApi
                    .getGeoCoding(
                        name = name,
                    ).results
                    .first()
            lastLookupFromCache = false
            val resolved = cachedGeoCodeData
            val entity =
                GeoCodeEntity(
                    query = normalized,
                    locationId = resolved?.id ?: "n/a",
                    name = resolved?.name ?: "n/a",
                    country = resolved?.country,
                    latitude = resolved?.latitude ?: 0.0,
                    longitude = resolved?.longitude ?: 0.0,
                )
            geoCodeDao.insert(entity)
            return FineWeatherData(
                resolved?.name ?: "n/a",
                Pair(
                    resolved?.latitude ?: 0.0,
                    resolved?.longitude ?: 0.0,
                ),
                resolved?.id ?: "n/a",
                resolved?.country,
            )
        } catch (e: Exception) {
            throw Exception("Failed to fetch geocoding", e)
        }
    }

    fun wasLastLookupFromCache(): Boolean = lastLookupFromCache

    private fun normalizeQuery(input: String): String =
        input.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
