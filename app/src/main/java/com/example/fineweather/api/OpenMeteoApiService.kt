package com.example.fineweather.api


import com.example.fineweather.data.models.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    @GET("https://api.open-meteo.com/v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("past_days") pastDays: Int,
        @Query("timezone") timezone: String
    ): WeatherResponse

    @GET("https://archive-api.open-meteo.com/v1/archive")
    suspend fun getHistoricData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_mean",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}