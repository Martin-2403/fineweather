package com.example.fineweather.api


import com.example.fineweather.data.models.WeatherResponse
import com.example.fineweather.data.models.GeoCodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoWeatherApiService {
    @GET("forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("past_days") pastDays: Int,
        @Query("timezone") timezone: String
    ): WeatherResponse
}