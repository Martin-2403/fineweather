package com.example.fineweather.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val country: String?,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
    val addedAt: Long,
    /** At most one favorite is the home place; the DAO keeps it that way. */
    @ColumnInfo(defaultValue = "0") val isHome: Boolean = false,
)
