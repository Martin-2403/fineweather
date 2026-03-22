package com.example.fineweather.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "geocode_cache",
    primaryKeys = ["query"],
)
data class GeoCodeEntity(
    val query: String,
    val locationId: String,
    val name: String,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
)
