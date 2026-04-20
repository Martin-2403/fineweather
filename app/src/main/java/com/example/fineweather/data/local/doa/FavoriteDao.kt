package com.example.fineweather.data.local.doa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_places ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoritePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoritePlaceEntity)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM favorite_places")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_places WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
