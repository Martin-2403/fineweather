package com.example.fineweather.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fineweather.data.local.doa.GeoCodeDao
import com.example.fineweather.data.local.doa.WeatherDao
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity
import com.example.fineweather.data.local.entities.WeatherEntity

@Database(
    entities = [WeatherEntity::class, GeoCodeCacheEntity::class],
    version = 7,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun geoCodeDao(): GeoCodeDao

    companion object {
        @Suppress("ktlint:standard:property-naming")
        @Volatile
        private var INSTANCE: WeatherDatabase? = null
        fun getDatabase(context: Context): WeatherDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            WeatherDatabase::class.java,
                            "weather_database",
                        )
                        .fallbackToDestructiveMigration(false)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
