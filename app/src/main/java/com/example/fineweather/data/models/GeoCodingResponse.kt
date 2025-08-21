package com.example.fineweather.data.models

data class GeoCodingResponse(
    val results: List<GeocodingResult>,
    val generationtime_ms: Double

)

data class GeocodingResult(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: String,
    val feature_code: String,
    val country_code: String,
    val admin1_id: String,
    val admin2_id: String,
    val admin3_id: String,
    val admin4_id: String,
    val timezone: String,
    val population: Int,
    val country_id: Int,
    val country: String,
    val admin1: String,
    val admin2: String,
    val admin3: String,
    val admin4: String
)