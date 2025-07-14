package com.example.fineweather.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object OpenMeteoRetrofitClient {
    private const val BASE_URL = "https://api.open-meteo.com/v1/"

    val client: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}