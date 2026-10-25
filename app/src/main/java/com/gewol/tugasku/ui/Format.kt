package com.gewol.tugasku.ui

import android.content.Context
import com.gewol.tugasku.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Bahasa tanggal/jam mengikuti bahasa aplikasi (Locale.getDefault() diatur oleh LocaleHelper).
private fun fmt(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fmt("EEE, d MMM yyyy"))

/** Jam saja, mis. "14:30" (24 jam) atau "2:30 PM" (12 jam). */
fun formatTime(millis: Long, use24Hour: Boolean = true): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(fmt(if (use24Hour) "HH:mm" else "h:mm a"))

fun formatDeadline(millis: Long, use24Hour: Boolean = true): String =
    "${formatDate(millis)} • ${formatTime(millis, use24Hour)}"

/** "3 jam lagi" / "Terlambat 2 hari" (atau versi Inggris). */
fun relativeLabel(context: Context, deadline: Long, now: Long): String {
    val diff = deadline - now
    val min = abs(diff) / 60_000
    if (min < 1) return context.getString(R.string.rel_due_now)
    val (n, left, late) = when {
        min < 60 -> Triple(min.toInt(), R.plurals.rel_left_minutes, R.plurals.rel_late_minutes)
        min < 60 * 24 -> Triple((min / 60).toInt(), R.plurals.rel_left_hours, R.plurals.rel_late_hours)
        else -> Triple((min / (60 * 24)).toInt(), R.plurals.rel_left_days, R.plurals.rel_late_days)
    }
    return context.resources.getQuantityString(if (diff >= 0) left else late, n, n)
}
