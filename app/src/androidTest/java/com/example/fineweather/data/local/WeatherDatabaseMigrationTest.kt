package com.example.fineweather.data.local

import androidx.core.database.getBlobOrNull
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherDatabaseMigrationTest {

    private val dbName = "migration-test"
    private val blob = byteArrayOf(1, 2, 3, 4)

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            WeatherDatabase::class.java,
        )

    private fun createV12WithRows() {
        helper.createDatabase(dbName, 12).use { db ->
            db.execSQL(
                "INSERT INTO reference_climate (locationId, referenceId, firstYear, lastYear, data, lastUsed) " +
                    "VALUES ('loc-1', '1961_1990', 1961, 1990, ?, 42)",
                arrayOf<Any>(blob),
            )
            db.execSQL(
                "INSERT INTO year_series (locationId, startDate, endDate, data, fetchedDate) " +
                    "VALUES ('loc-1', '2026-01-01', '2026-10-13', ?, '2026-10-14')",
                arrayOf<Any>(blob),
            )
            db.execSQL(
                "INSERT INTO forecast_series (locationId, startDate, endDate, data, fetchedDate) " +
                    "VALUES ('loc-1', '2026-10-14', '2026-10-27', ?, '2026-10-14')",
                arrayOf<Any>(blob),
            )
        }
    }

    private fun migrate(): SupportSQLiteDatabase = helper.runMigrationsAndValidate(dbName, 13, true, MIGRATION_12_13)

    private fun SupportSQLiteDatabase.blobsOf(table: String): Triple<ByteArray?, ByteArray?, ByteArray?> =
        query("SELECT data, sunshineData, precipitationData FROM $table WHERE locationId = 'loc-1'").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            Triple(it.getBlob(0), it.getBlobOrNull(1), it.getBlobOrNull(2))
        }

    @Test
    fun migrate12To13_keepsEveryRowAndLeavesTheNewColumnsNull() {
        createV12WithRows()

        val db = migrate()

        listOf("reference_climate", "year_series", "forecast_series").forEach { table ->
            val (data, sunshine, precipitation) = db.blobsOf(table)
            assertArrayEquals("$table data", blob, data)
            assertNull("$table sunshine", sunshine)
            assertNull("$table precipitation", precipitation)
        }
        db.query("SELECT firstYear, lastYear, lastUsed FROM reference_climate").use {
            it.moveToFirst()
            assertEquals(1961, it.getInt(0))
            assertEquals(1990, it.getInt(1))
            assertEquals(42L, it.getLong(2))
        }
        db.query("SELECT fetchedDate, endDate FROM year_series").use {
            it.moveToFirst()
            assertEquals("2026-10-14", it.getString(0))
            assertEquals("2026-10-13", it.getString(1))
        }
    }

    @Test
    fun migrate12To13_newColumnsAcceptBlobs() {
        createV12WithRows()
        val db = migrate()

        val extra = byteArrayOf(9, 8)
        db.execSQL(
            "UPDATE reference_climate SET sunshineData = ?, precipitationData = ? WHERE locationId = 'loc-1'",
            arrayOf<Any>(extra, blob),
        )

        val (_, sunshine, precipitation) = db.blobsOf("reference_climate")
        assertArrayEquals(extra, sunshine)
        assertArrayEquals(blob, precipitation)
    }

    @Test
    fun migrate12To13_resultOpensWithRoomAndOldRowsAreReadable() {
        createV12WithRows()
        migrate().close()

        val room =
            Room
                .databaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    WeatherDatabase::class.java,
                    dbName,
                ).addMigrations(MIGRATION_12_13)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                val row = room.referenceClimateDao().get("loc-1", "1961_1990")!!
                assertArrayEquals(blob, row.data)
                assertNull(row.sunshineData)
                assertNull(row.precipitationData)
                assertNull(room.yearSeriesDao().get("loc-1")!!.sunshineData)
                assertNull(room.forecastSeriesDao().get("loc-1")!!.precipitationData)
            }
        } finally {
            room.close()
        }
    }
}
