package com.example.fineweather.data.local.entities

import androidx.room.Entity

/** The 14-day daily forecast of one place as a float32 blob, valid for the day it was [fetchedDate]. */
@Entity(
    tableName = "forecast_series",
    primaryKeys = ["locationId"],
)
data class ForecastSeriesEntity(
    val locationId: String,
    val startDate: String,
    val endDate: String,
    val data: ByteArray,
    val fetchedDate: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ForecastSeriesEntity &&
            locationId == other.locationId &&
            startDate == other.startDate &&
            endDate == other.endDate &&
            fetchedDate == other.fetchedDate &&
            data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * locationId.hashCode() + data.contentHashCode()
}
