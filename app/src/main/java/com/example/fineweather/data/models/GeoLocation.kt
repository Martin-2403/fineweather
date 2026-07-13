package com.example.fineweather.data.models

data class GeoPlace(
    val id: String,
    val name: String,
    val country: String?,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
) {
    fun displayName(): String = name

    fun displaySubtitle(): String =
        listOfNotNull(admin1?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
            .joinToString(", ")
}

fun GeoPlace.toFineWeatherData(): FineWeatherData =
    FineWeatherData(
        location = name,
        coordinates = Pair(latitude, longitude),
        id = id,
        country = country,
    )
