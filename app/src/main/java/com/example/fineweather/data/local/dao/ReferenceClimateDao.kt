package com.example.fineweather.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.ReferenceClimateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceClimateDao {
    @Query("SELECT * FROM reference_climate WHERE locationId = :locationId AND referenceId = :referenceId")
    suspend fun get(
        locationId: String,
        referenceId: String,
    ): ReferenceClimateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReferenceClimateEntity)

    @Query("UPDATE reference_climate SET lastUsed = :now WHERE locationId = :locationId AND referenceId = :referenceId")
    suspend fun touch(
        locationId: String,
        referenceId: String,
        now: Long,
    )

    /** Keeps the [keep] most recently used locations that are not favorites; favorites are never evicted. */
    @Query(
        """
        DELETE FROM reference_climate
        WHERE locationId NOT IN (SELECT id FROM favorite_places)
        AND rowid NOT IN (
            SELECT rowid FROM reference_climate
            WHERE locationId NOT IN (SELECT id FROM favorite_places)
            ORDER BY lastUsed DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun evictLeastRecentlyUsed(keep: Int)

    @Query("SELECT COUNT(*) FROM reference_climate")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM reference_climate")
    suspend fun clearAll()
}
