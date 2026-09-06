package com.example.resilio.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    private const val PH_TIME_ZONE = "Asia/Manila"

    fun formatToPhTime(timestamp: Timestamp?, pattern: String = "MMM d, h:mm a"): String {
        if (timestamp == null) return "Just now"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(PH_TIME_ZONE)
        return sdf.format(timestamp.toDate())
    }

    fun formatToPhTime(date: Date, pattern: String = "MMM d, h:mm a"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(PH_TIME_ZONE)
        return sdf.format(date)
    }

    fun formatToPhTime(millis: Long, pattern: String = "MMM d, h:mm a"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(PH_TIME_ZONE)
        return sdf.format(Date(millis))
    }
}
