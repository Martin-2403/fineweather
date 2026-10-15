package com.example.fineweather.domain

import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Consecutive daily values (a mean temperature, hours of sun, mm of rain) from [start]; missing days are NaN. */
class DailySeries(
    val start: LocalDate,
    private val values: DoubleArray,
) {
    val size: Int get() = values.size
    val end: LocalDate get() = start.plusDays(values.size - 1L)

    /** Whether any day has a value. */
    fun hasValues(): Boolean = values.any { !it.isNaN() }

    fun valueOrNull(date: LocalDate): Double? {
        val index = ChronoUnit.DAYS.between(start, date)
        if (index < 0 || index >= values.size) return null
        return values[index.toInt()].takeUnless { it.isNaN() }
    }

    /** Mean of the available days in [from]..[to] (inclusive), or null if there are none. */
    fun mean(
        from: LocalDate,
        to: LocalDate,
    ): Double? {
        var sum = 0.0
        var count = 0
        forEachDay(from, to) { _, value ->
            sum += value
            count++
        }
        return if (count == 0) null else sum / count
    }

    fun count(
        from: LocalDate,
        to: LocalDate,
    ): Int {
        var count = 0
        forEachDay(from, to) { _, _ -> count++ }
        return count
    }

    /** Available (date, value) pairs in [from]..[to] (inclusive). */
    fun days(
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, Double>> {
        val result = mutableListOf<Pair<LocalDate, Double>>()
        forEachDay(from, to) { date, value -> result += date to value }
        return result
    }

    private inline fun forEachDay(
        from: LocalDate,
        to: LocalDate,
        action: (LocalDate, Double) -> Unit,
    ) {
        val first = maxOf(ChronoUnit.DAYS.between(start, from), 0L)
        val last = minOf(ChronoUnit.DAYS.between(start, to), values.size - 1L)
        var index = first
        while (index <= last) {
            val value = values[index.toInt()]
            if (!value.isNaN()) action(start.plusDays(index), value)
            index++
        }
    }

    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
        values.forEach { buffer.putFloat(it.toFloat()) }
        return buffer.array()
    }

    companion object {
        /**
         * Builds a series covering [start]..[end] from API-style parallel lists. Values are rounded to
         * float precision so a series restored from [toBytes] is identical to a freshly built one.
         * Each value is divided by [divisor] to convert the API unit to the stored one. Entries outside
         * [start]..[end] are ignored, and when [times] and [values] differ in length the extra entries of
         * the longer one are ignored, so the days they would have covered stay missing.
         */
        fun fromApi(
            start: LocalDate,
            end: LocalDate,
            times: List<String>,
            values: List<Double?>,
            divisor: Double = 1.0,
        ): DailySeries {
            val length = (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(0)
            val result = DoubleArray(length) { Double.NaN }
            times.zip(values).forEach { (time, value) ->
                val date = runCatching { LocalDate.parse(time) }.getOrNull() ?: return@forEach
                val index = ChronoUnit.DAYS.between(start, date)
                if (value != null && index >= 0 && index < length) {
                    result[index.toInt()] = (value / divisor).toFloat().toDouble()
                }
            }
            return DailySeries(start, result)
        }

        /** A series of [days] days without a value. */
        fun missing(
            start: LocalDate,
            days: Int,
        ): DailySeries = DailySeries(start, DoubleArray(days) { Double.NaN })

        fun fromBytes(
            start: LocalDate,
            bytes: ByteArray,
        ): DailySeries {
            val buffer = ByteBuffer.wrap(bytes)
            val values = DoubleArray(bytes.size / Float.SIZE_BYTES) { buffer.getFloat().toDouble() }
            return DailySeries(start, values)
        }
    }
}
