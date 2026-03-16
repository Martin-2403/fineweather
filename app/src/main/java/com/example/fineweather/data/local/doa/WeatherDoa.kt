package com.example.fineweather.data.local.doa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.WeatherEntity

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_history WHERE id = :locId LIMIT 1")
    suspend fun getWeatherById(locId: String): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherEntity)

    @Query("DELETE FROM weather_history WHERE date < :minDate")
    suspend fun cleanOutdatedCache(minDate: String)
}
