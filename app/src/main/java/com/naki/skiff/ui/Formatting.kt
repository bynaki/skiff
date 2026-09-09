package com.naki.skiff.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (value >= 100) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[index])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}

private val dateFormat: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

fun formatTimestamp(epochSeconds: Long): String =
    if (epochSeconds <= 0) "" else dateFormat.format(Date(epochSeconds * 1000))

/** "rwxr-xr-x" from POSIX mode bits. */
fun formatMode(mode: Int?): String {
    if (mode == null) return ""
    val builder = StringBuilder(9)
    for (shift in intArrayOf(6, 3, 0)) {
        val triple = (mode shr shift) and 0b111
        builder.append(if (triple and 0b100 != 0) 'r' else '-')
        builder.append(if (triple and 0b010 != 0) 'w' else '-')
        builder.append(if (triple and 0b001 != 0) 'x' else '-')
    }
    return builder.toString()
}
