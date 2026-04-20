package com.example.fineweather.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fineweather.data.local.doa.FavoriteDao
import com.example.fineweather.data.local.doa.GeoCodeDao
import com.example.fineweather.data.local.doa.WeatherDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity
import com.example.fineweather.data.local.entities.WeatherEntity

@Database(
    entities = [WeatherEntity::class, GeoCodeCacheEntity::class, FavoritePlaceEntity::class],
    version = 8,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun geoCodeDao(): GeoCodeDao
    abstract fun favoriteDao(): FavoriteDao

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
                        .addMigrations(MIGRATION_7_8)
                        .fallbackToDestructiveMigration(false)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}

private val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `favorite_places` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `country` TEXT,
                    `admin1` TEXT,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
        }
    }
