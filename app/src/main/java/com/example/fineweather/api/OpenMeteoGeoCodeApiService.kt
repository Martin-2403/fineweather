package com.example.fineweather.api


import com.example.fineweather.data.models.GeoCodingResponse
import com.example.fineweather.data.models.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoGeoCodeApiService {
    @GET("search")
    suspend fun getGeoCoding(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeoCodingResponse
}