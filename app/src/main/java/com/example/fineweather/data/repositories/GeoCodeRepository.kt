package com.example.fineweather.data.repositories

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.local.doa.GeoCodeDao
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity
import com.example.fineweather.data.models.GeoPlace
import com.example.fineweather.data.models.GeocodeSelection
import com.example.fineweather.data.models.GeocodingResult
import java.util.Locale

class GeoCodeRepository(
    private val openMeteoGeoCodeApi: OpenMeteoGeoCodeApiService,
    private val geoCodeDao: GeoCodeDao,
) {
    suspend fun getGeoCode(
        name: String,
        language: String,
    ): GeocodeSelection {
        try {
            val normalizedLanguage = normalizeLanguage(language)
            val languageLocale = Locale.forLanguageTag(normalizedLanguage)
            val normalized = normalizeQuery(name, languageLocale)
            val cacheKey = buildCacheKey(normalized, normalizedLanguage)
            val cached = geoCodeDao.getByQuery(cacheKey)
            val fallbackCached =
                if (cached.isEmpty()) geoCodeDao.getByQuery(normalized) else emptyList()
            val cachedEntries = if (cached.isNotEmpty()) cached else fallbackCached
            if (cachedEntries.isNotEmpty()) {
                if (cachedEntries.first().query != cacheKey) {
                    geoCodeDao.deleteByQuery(cacheKey)
                    geoCodeDao.insertAll(cachedEntries.map { it.copy(query = cacheKey) })
                }
                return GeocodeSelection(
                    primary = cachedEntries.first().toGeoPlace(),
                    candidates = cachedEntries.map { it.toGeoPlace() },
                )
            }

            val response =
                openMeteoGeoCodeApi
                    .getGeoCoding(
                        name = name,
                        language = normalizedLanguage,
                    )
            val sorted =
                response.results
                    .filterNotNull()
                    .sortedByDescending { it.population }
                    .take(10)
            val primary = sorted.firstOrNull()
            val cacheEntries =
                if (sorted.isNotEmpty()) {
                    sorted.mapIndexed { index, result -> result.toCacheEntity(cacheKey, index) }
                } else {
                    listOf(fallbackCacheEntity(cacheKey))
                }
            geoCodeDao.deleteByQuery(cacheKey)
            geoCodeDao.insertAll(cacheEntries)
            val primaryPlace = primary?.toGeoPlace() ?: fallbackGeoPlace()

            val candidates =
                sorted.map { it.toGeoPlace() }
            return GeocodeSelection(
                primary = primaryPlace,
                candidates = candidates,
            )
        } catch (e: Exception) {
            throw Exception("Failed to fetch geocoding", e)
        }
    }

    private fun normalizeQuery(
        input: String,
        locale: Locale,
    ): String =
        input.trim()
            .lowercase(locale)
            .replace(Regex("\\s+"), " ")

    @RequiresApi(Build.VERSION_CODES.GINGERBREAD)
    private fun normalizeLanguage(input: String): String =
        input.trim()
            .lowercase(Locale.ROOT)
            .ifBlank { SettingsDefaults.DEFAULT_SEARCH_LANGUAGE }

    private fun buildCacheKey(
        normalizedQuery: String,
        normalizedLanguage: String,
    ): String = "${normalizedLanguage}|${normalizedQuery}"

    private fun GeoCodeCacheEntity.toGeoPlace(): GeoPlace =
        GeoPlace(
            id = locationId,
            name = name,
            country = country,
            admin1 = admin1,
            latitude = latitude,
            longitude = longitude,
        )

    private fun GeocodingResult.toCacheEntity(
        cacheKey: String,
        rank: Int,
    ): GeoCodeCacheEntity =
        GeoCodeCacheEntity(
            query = cacheKey,
            locationId = id,
            rank = rank,
            name = name,
            country = country,
            admin1 = admin1,
            latitude = latitude,
            longitude = longitude,
        )

    private fun fallbackCacheEntity(cacheKey: String): GeoCodeCacheEntity =
        GeoCodeCacheEntity(
            query = cacheKey,
            locationId = "n/a",
            rank = 0,
            name = "n/a",
            country = null,
            admin1 = null,
            latitude = 0.0,
            longitude = 0.0,
        )

    private fun fallbackGeoPlace(): GeoPlace =
        GeoPlace(
            id = "n/a",
            name = "n/a",
            country = null,
            admin1 = null,
            latitude = 0.0,
            longitude = 0.0,
        )

    private fun GeocodingResult.toGeoPlace(): GeoPlace =
        GeoPlace(
            id = id,
            name = name,
            country = country,
            admin1 = admin1,
            latitude = latitude,
            longitude = longitude,
        )
}
