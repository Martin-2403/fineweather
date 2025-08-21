package com.example.fineweather.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.getValue

object OpenMeteoRetrofitClients {
    private val BASE_URL_FORECAST = "https://api.open-meteo.com/v1/"
    private val BASE_URL_ARCHIVE = "https://archive-api.open-meteo.com/v1/"
    private val BASE_URL_GEOCODING = "https://geocoding-api.open-meteo.com/v1/"

    object RetrofitFactory {
        fun create(baseUrl: String): Retrofit {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    val forecastApi: OpenMeteoWeatherApiService by lazy {
        RetrofitFactory.create(BASE_URL_FORECAST).create(OpenMeteoWeatherApiService::class.java)
    }

    val archiveApi: OpenMeteoArchiveApiService by lazy {
        RetrofitFactory.create(BASE_URL_ARCHIVE).create(OpenMeteoArchiveApiService::class.java)
    }

    val geocodingApi: OpenMeteoGeoCodeApiService by lazy {
        RetrofitFactory.create(BASE_URL_GEOCODING).create(OpenMeteoGeoCodeApiService::class.java)
    }

}