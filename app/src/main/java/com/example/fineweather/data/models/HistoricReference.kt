package com.example.fineweather.data.models

import java.time.LocalDate

enum class HistoricReference(
    val id: String,
) {
    CLASSIC("1961_1990"),
    CURRENT("current");

    fun displayLabel(now: LocalDate = LocalDate.now()): String {
        return when (this) {
            CLASSIC -> "1961–1990"
            CURRENT -> {
                val endYear = latestCompleteDecadeEndYear(now)
                val startYear = endYear - 29
                "$startYear–$endYear"
            }
        }
    }

    companion object {
        fun fromId(id: String?): HistoricReference =
            when (id) {
                CLASSIC.id -> CLASSIC
                CURRENT.id -> CURRENT
                else -> CLASSIC
            }

        fun latestCompleteDecadeEndYear(now: LocalDate = LocalDate.now()): Int {
            val year = now.year
            return if (year % 10 == 0) {
                year - 10
            } else {
                (year / 10) * 10
            }
        }
    }
}
