package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.models.GeocodingResult

class GeoCodeRepository(
    private val openMeteoGeoCodeApi: OpenMeteoGeoCodeApiService,
) {
    private var cachedGeoCodeData: GeocodingResult? = null

    suspend fun getGeoCode(
        name: String
    ): Pair<Double, Double>? {
        try {
            cachedGeoCodeData = openMeteoGeoCodeApi.getGeoCoding(
                name = name
            ).results.first()
            return Pair(cachedGeoCodeData?.latitude ?: 0.0, cachedGeoCodeData?.longitude ?: 0.0)
        } catch (e: Exception) {
            throw Exception("Failed to fetch geocoding", e)
        }
    }

    fun getGeoCodeLocation(): String {
        return ("${cachedGeoCodeData?.name}, ${cachedGeoCodeData?.country}")
    }
}