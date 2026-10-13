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
) {
    override fun equals(other: Any?): Boolean =
        other is ReferenceClimateEntity &&
            locationId == other.locationId &&
            referenceId == other.referenceId &&
            firstYear == other.firstYear &&
            lastYear == other.lastYear &&
            lastUsed == other.lastUsed &&
            data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * (31 * locationId.hashCode() + referenceId.hashCode()) + data.contentHashCode()
}
