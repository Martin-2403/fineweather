package com.example.fineweather.data.local.entities

import androidx.room.Entity

/** Daily means from [startDate] to [endDate] as a float32 blob, valid for the day it was [fetchedDate]. */
@Entity(
    tableName = "year_series",
    primaryKeys = ["locationId"],
)
data class YearSeriesEntity(
    val locationId: String,
    val startDate: String,
    val endDate: String,
    val data: ByteArray,
    val fetchedDate: String,
) {
    override fun equals(other: Any?): Boolean =
        other is YearSeriesEntity &&
            locationId == other.locationId &&
            startDate == other.startDate &&
            endDate == other.endDate &&
            fetchedDate == other.fetchedDate &&
            data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * locationId.hashCode() + data.contentHashCode()
}
