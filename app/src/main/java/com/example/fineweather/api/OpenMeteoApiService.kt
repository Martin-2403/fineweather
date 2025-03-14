package com.example.fineweather.api


import com.example.fineweather.data.models.ForecastData
import retrofit2.http.GET
import retrofit2.http.Query

//https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&daily=temperature_2m_max,temperature_2m_min
interface OpenMeteoApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min",
    ): ForecastData
}