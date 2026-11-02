package com.example.fineweather.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.ForecastSeriesEntity

@Dao
interface ForecastSeriesDao {
    @Query("SELECT * FROM forecast_series WHERE locationId = :locationId")
    suspend fun get(locationId: String): ForecastSeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ForecastSeriesEntity)

    /** A forecast is useless after the day it was made, so nothing is kept, favorites included. */
    @Query("DELETE FROM forecast_series WHERE fetchedDate < :minDate")
    suspend fun cleanOutdated(minDate: String)

    @Query("DELETE FROM forecast_series")
    suspend fun clearAll()
}
