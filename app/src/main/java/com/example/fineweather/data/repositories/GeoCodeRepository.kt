package com.example.fineweather.data.repositories

import com.example.fineweather.api.OpenMeteoGeoCodeApiService
import com.example.fineweather.data.models.FineWeatherData
import com.example.fineweather.data.models.GeocodingResult

class GeoCodeRepository(
    private val openMeteoGeoCodeApi: OpenMeteoGeoCodeApiService,
) {
    private var cachedGeoCodeData: GeocodingResult? = null

    suspend fun getGeoCode(name: String): FineWeatherData {
        try {
            cachedGeoCodeData =
                openMeteoGeoCodeApi
                    .getGeoCoding(
                        name = name,
                    ).results
                    .first()
            return FineWeatherData(
                cachedGeoCodeData?.name ?: "n/a",
                Pair(
                    cachedGeoCodeData?.latitude ?: 0.0,
                    cachedGeoCodeData?.longitude ?: 0.0,
                ),
                cachedGeoCodeData?.id ?: "n/a",
            )
        } catch (e: Exception) {
            throw Exception("Failed to fetch geocoding", e)
        }
    }
}
