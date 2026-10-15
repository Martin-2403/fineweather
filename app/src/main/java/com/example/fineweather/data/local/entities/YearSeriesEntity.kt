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
    /** Hours of sunshine per day, null for rows written before the variable was fetched. */
    val sunshineData: ByteArray? = null,
    /** Millimetres of precipitation per day, null for rows written before the variable was fetched. */
    val precipitationData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is YearSeriesEntity &&
            locationId == other.locationId &&
            startDate == other.startDate &&
            endDate == other.endDate &&
            fetchedDate == other.fetchedDate &&
            data.contentEquals(other.data) &&
            sunshineData.contentEquals(other.sunshineData) &&
            precipitationData.contentEquals(other.precipitationData)

    override fun hashCode(): Int {
        var result = locationId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sunshineData.contentHashCode()
        result = 31 * result + precipitationData.contentHashCode()
        return result
    }
}
