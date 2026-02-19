package com.example.fineweather.data.local.doa

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fineweather.data.local.entities.WeatherEntity

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_history WHERE locationId = :locId AND date = :date LIMIT 1")
    suspend fun getWeatherByIdAndDate(
        locId: Long,
        date: String,
    ): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherEntity)

    @Query("SELECT * FROM weather_history WHERE date != :today")
    suspend fun cleanOutdatedCache(today: String)
}
