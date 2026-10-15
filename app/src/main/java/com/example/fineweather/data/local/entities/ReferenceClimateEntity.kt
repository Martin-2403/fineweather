package com.example.fineweather.data.local.entities

import androidx.room.Entity

/** Daily means of one reference period (first January to last December) as a float32 blob. */
@Entity(
    tableName = "reference_climate",
    primaryKeys = ["locationId", "referenceId"],
)
data class ReferenceClimateEntity(
    val locationId: String,
    val referenceId: String,
    val firstYear: Int,
    val lastYear: Int,
    val data: ByteArray,
    val lastUsed: Long,
    /** Hours of sunshine per day; null until the lazily fetched extras were stored. */
    val sunshineData: ByteArray? = null,
    /** Millimetres of precipitation per day; null until the lazily fetched extras were stored. */
    val precipitationData: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is ReferenceClimateEntity &&
            locationId == other.locationId &&
            referenceId == other.referenceId &&
            firstYear == other.firstYear &&
            lastYear == other.lastYear &&
            lastUsed == other.lastUsed &&
            data.contentEquals(other.data) &&
            sunshineData.contentEquals(other.sunshineData) &&
            precipitationData.contentEquals(other.precipitationData)

    override fun hashCode(): Int {
        var result = 31 * locationId.hashCode() + referenceId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sunshineData.contentHashCode()
        result = 31 * result + precipitationData.contentHashCode()
        return result
    }
}
