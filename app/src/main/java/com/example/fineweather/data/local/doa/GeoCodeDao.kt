package com.example.fineweather.data.local.doa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity

@Dao
interface GeoCodeDao {
    @Query("SELECT * FROM geocode_cache_entries WHERE `query` = :query ORDER BY rank ASC")
    suspend fun getByQuery(query: String): List<GeoCodeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<GeoCodeCacheEntity>)

    @Query("DELETE FROM geocode_cache_entries WHERE `query` = :query")
    suspend fun deleteByQuery(query: String)
}
