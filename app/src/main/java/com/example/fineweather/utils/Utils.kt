package com.example.fineweather.utils

import java.util.Calendar
import java.util.Locale

fun formatDouble(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"

fun getCurrentMonthString(): String {
    val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    return if (month >= 10) "$month" else "0$month"
}

fun getCurrentMonthName(): String {
    val calendar = Calendar.getInstance()
    return calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        ?: "Unknown"
}
