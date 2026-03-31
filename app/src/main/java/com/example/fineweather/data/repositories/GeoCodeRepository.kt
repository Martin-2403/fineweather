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

    suspend fun getGeoCode(
        name: String,
        language: String,
    ): FineWeatherData {
        try {
            val normalizedLanguage = normalizeLanguage(language)
            val languageLocale = Locale.forLanguageTag(normalizedLanguage)
            val normalized = normalizeQuery(name, languageLocale)
            val cacheKey = buildCacheKey(normalized, normalizedLanguage)
            val cached = geoCodeDao.getByQuery(cacheKey) ?: geoCodeDao.getByQuery(normalized)
            cached?.let { cachedEntry ->
                lastLookupFromCache = true
                if (cachedEntry.query != cacheKey) {
                    geoCodeDao.insert(cachedEntry.copy(query = cacheKey))
                }
                return FineWeatherData(
                    cachedEntry.name,
                    Pair(cachedEntry.latitude, cachedEntry.longitude),
                    cachedEntry.locationId,
                    cachedEntry.country,
                )
            }

            cachedGeoCodeData =
                openMeteoGeoCodeApi
                    .getGeoCoding(
                        name = name,
                        language = normalizedLanguage,
                    ).results
                    .first()
            lastLookupFromCache = false
            val resolved = cachedGeoCodeData
            val entity =
                GeoCodeEntity(
                    query = cacheKey,
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

    private fun normalizeQuery(
        input: String,
        locale: Locale,
    ): String =
        input.trim()
            .lowercase(locale)
            .replace(Regex("\\s+"), " ")

    private fun normalizeLanguage(input: String): String =
        input.trim()
            .lowercase(Locale.ROOT)
            .ifBlank { SettingsDefaults.DEFAULT_SEARCH_LANGUAGE }

    private fun buildCacheKey(
        normalizedQuery: String,
        normalizedLanguage: String,
    ): String = "${normalizedLanguage}|${normalizedQuery}"
}
