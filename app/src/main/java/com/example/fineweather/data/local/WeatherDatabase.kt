package com.example.fineweather.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fineweather.data.local.dao.FavoriteDao
import com.example.fineweather.data.local.dao.ForecastSeriesDao
import com.example.fineweather.data.local.dao.GeoCodeDao
import com.example.fineweather.data.local.dao.ReferenceClimateDao
import com.example.fineweather.data.local.dao.WeatherDao
import com.example.fineweather.data.local.dao.YearSeriesDao
import com.example.fineweather.data.local.entities.FavoritePlaceEntity
import com.example.fineweather.data.local.entities.ForecastSeriesEntity
import com.example.fineweather.data.local.entities.GeoCodeCacheEntity
import com.example.fineweather.data.local.entities.ReferenceClimateEntity
import com.example.fineweather.data.local.entities.WeatherEntity
import com.example.fineweather.data.local.entities.YearSeriesEntity

@Database(
    entities = [
        WeatherEntity::class,
        GeoCodeCacheEntity::class,
        FavoritePlaceEntity::class,
        ReferenceClimateEntity::class,
        YearSeriesEntity::class,
        ForecastSeriesEntity::class,
    ],
    version = 12,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
    abstract fun geoCodeDao(): GeoCodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun referenceClimateDao(): ReferenceClimateDao
    abstract fun yearSeriesDao(): YearSeriesDao
    abstract fun forecastSeriesDao(): ForecastSeriesDao

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
                        .addMigrations(MIGRATION_8_9)
                        .addMigrations(MIGRATION_9_10)
                        .addMigrations(MIGRATION_10_11)
                        .addMigrations(MIGRATION_11_12)
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

private val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `weather_history`
                ADD COLUMN `historicMonthlyStdDevClassic` REAL
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `weather_history`
                ADD COLUMN `historicMonthlyStdDevCurrent` REAL
                """.trimIndent(),
            )
        }
    }

private val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE `weather_history`
                ADD COLUMN `historicMonthClassic` TEXT
                """.trimIndent(),
            )
            db.execSQL(
                """
                ALTER TABLE `weather_history`
                ADD COLUMN `historicMonthCurrent` TEXT
                """.trimIndent(),
            )
        }
    }

private val MIGRATION_10_11 =
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reference_climate` (
                    `locationId` TEXT NOT NULL,
                    `referenceId` TEXT NOT NULL,
                    `firstYear` INTEGER NOT NULL,
                    `lastYear` INTEGER NOT NULL,
                    `data` BLOB NOT NULL,
                    `lastUsed` INTEGER NOT NULL,
                    PRIMARY KEY(`locationId`, `referenceId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `year_series` (
                    `locationId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT NOT NULL,
                    `data` BLOB NOT NULL,
                    `fetchedDate` TEXT NOT NULL,
                    PRIMARY KEY(`locationId`)
                )
                """.trimIndent(),
            )
        }
    }

private val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `forecast_series` (
                    `locationId` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT NOT NULL,
                    `data` BLOB NOT NULL,
                    `fetchedDate` TEXT NOT NULL,
                    PRIMARY KEY(`locationId`)
                )
                """.trimIndent(),
            )
        }
    }
