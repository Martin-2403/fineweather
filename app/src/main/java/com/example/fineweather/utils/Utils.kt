package com.example.fineweather.utils

import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

fun formatDouble(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"

fun getCurrentMonthString(now: LocalDate = LocalDate.now()): String =
    now.format(DateTimeFormatter.ofPattern("MM"))

fun getCurrentMonthName(now: LocalDate = LocalDate.now()): String =
    now.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
