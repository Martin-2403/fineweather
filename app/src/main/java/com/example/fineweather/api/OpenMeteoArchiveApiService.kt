package com.example.fineweather.api

import com.example.fineweather.data.models.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoArchiveApiService {
    @GET("archive")
    suspend fun getHistoricData(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_mean",
        @Query("timezone") timezone: String = "auto",
    ): WeatherResponse
}
