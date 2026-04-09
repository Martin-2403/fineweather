package com.example.fineweather.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "geocode_cache_entries",
    primaryKeys = ["query", "locationId"],
    indices = [Index(value = ["query"]), Index(value = ["query", "rank"])],
)
data class GeoCodeCacheEntity(
    val query: String,
    val locationId: String,
    val rank: Int,
    val name: String,
    val country: String?,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
)
