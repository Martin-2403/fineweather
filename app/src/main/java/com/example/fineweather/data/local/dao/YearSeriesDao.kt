package com.example.fineweather.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.YearSeriesEntity

@Dao
interface YearSeriesDao {
    @Query("SELECT * FROM year_series WHERE locationId = :locationId")
    suspend fun get(locationId: String): YearSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: YearSeriesEntity)

    @Query(
        """
        DELETE FROM year_series
        WHERE fetchedDate < :minDate AND locationId NOT IN (SELECT id FROM favorite_places)
        """,
    )
    suspend fun cleanOutdated(minDate: String)

    @Query("DELETE FROM year_series")
    suspend fun clearAll()
}
