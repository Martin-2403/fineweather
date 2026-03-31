package com.example.fineweather.data.local.doa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.GeoCodeEntity

@Dao
interface GeoCodeDao {
    @Query("SELECT * FROM geocode_cache WHERE `query` = :query LIMIT 1")
    suspend fun getByQuery(query: String): GeoCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GeoCodeEntity)
}
