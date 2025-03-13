package com.example.fineweather.api


import com.example.fineweather.data.models.ForecastData
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") daily: String = "temperature_2",
    ): ForecastData
}