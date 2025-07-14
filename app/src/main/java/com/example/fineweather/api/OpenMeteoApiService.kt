package com.example.fineweather.api


import WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {
    /**@GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min",
    ): ForecastData**/

    @GET("forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("past_days") pastDays: Int,
        @Query("timezone") timezone: String
    ): WeatherResponse

    //https://archive-api.open-meteo.com/v1/archive?latitude=52.52&longitude=13.41&start_date=1995-02-08&end_date=2025-03-22&daily=temperature_2m_mean&timezone=auto
    @GET("archive")
    suspend fun getHistoricData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_mean",
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}
