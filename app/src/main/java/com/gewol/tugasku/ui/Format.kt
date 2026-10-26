package com.gewol.tugasku.ui

import android.content.Context
import com.gewol.tugasku.R
import com.gewol.tugasku.REMIND_OPTIONS
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private fun fmt(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fmt("EEE, d MMM yyyy"))

fun formatTime(millis: Long, use24Hour: Boolean = true): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(fmt(if (use24Hour) "HH:mm" else "h:mm a"))

fun formatDeadline(millis: Long, use24Hour: Boolean = true): String =
    "${formatDate(millis)} • ${formatTime(millis, use24Hour)}"

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

fun reminderLabel(context: Context, minutes: Int): String {
    val predefined = REMIND_OPTIONS.firstOrNull { it.first == minutes }
    if (predefined != null) return context.getString(predefined.second)
    return when {
        minutes <= 0 -> context.getString(R.string.remind_on_time)
        minutes % 10080 == 0 -> context.getString(R.string.remind_fmt_weeks, minutes / 10080)
        minutes % 1440 == 0 -> context.getString(R.string.remind_fmt_days, minutes / 1440)
        minutes % 60 == 0 -> context.getString(R.string.remind_fmt_hours, minutes / 60)
        minutes < 60 -> context.getString(R.string.remind_fmt_minutes, minutes)
        else -> {
            val d = minutes / 1440
            val rem = minutes % 1440
            val h = rem / 60
            val m = rem % 60
            if (d > 0) {
                if (h > 0) context.getString(R.string.remind_fmt_days_hours, d, h)
                else context.getString(R.string.remind_fmt_days, d)
            } else {
                context.getString(R.string.remind_fmt_hours_minutes, h, m)
            }
        }
    }
}
