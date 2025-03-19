package com.example.fineweather.data.models

import kotlinx.serialization.Serializable

@Serializable
data class StationData(
    val Stations_id: String,
    val von_datum: String,
    val bis_datum: String,
    val Stationshoehe: Int,
    val geoBreite: Double,
    val geoLaenge: Double,
    val Stationsname: String,
    val Bundesland: String,
    val Abgabe: String
)

