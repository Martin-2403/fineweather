package com.example.fineweather.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_places ORDER BY isHome DESC, addedAt DESC")
    fun observeAll(): Flow<List<FavoritePlaceEntity>>

    @Query("SELECT id FROM favorite_places WHERE isHome = 1 LIMIT 1")
    fun observeHomeId(): Flow<String?>

    @Query("SELECT * FROM favorite_places WHERE isHome = 1 LIMIT 1")
    suspend fun getHome(): FavoritePlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoritePlaceEntity)

    @Query("DELETE FROM favorite_places WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM favorite_places")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_places WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("UPDATE favorite_places SET isHome = 0 WHERE isHome = 1")
    suspend fun clearHome()

    @Query("UPDATE favorite_places SET isHome = 1 WHERE id = :id")
    suspend fun markHome(id: String)

    /** Makes [id] the only home; a place that is no favorite changes nothing, not even the current home. */
    @Transaction
    suspend fun setHome(id: String) {
        if (!exists(id)) return
        clearHome()
        markHome(id)
    }

    /** [upsert] that keeps the home flag of an existing row: the entity of a place does not know it. */
    @Transaction
    suspend fun save(entity: FavoritePlaceEntity) {
        upsert(entity.copy(isHome = getHome()?.id == entity.id))
    }
}
