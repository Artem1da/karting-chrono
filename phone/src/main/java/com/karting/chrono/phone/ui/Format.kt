package com.karting.chrono.phone.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatLap(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val hundredths = (ms % 1000) / 10
    return "%d:%02d.%02d".format(minutes, seconds, hundredths)
}

private val DATE_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

internal fun formatDate(ms: Long): String = DATE_FMT.format(Date(ms))
