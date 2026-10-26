package com.gewol.tugasku

import java.time.Instant
import java.time.ZoneId

data class Subtask(
    val id: Long,
    val title: String,
    val done: Boolean = false
)

const val PRIORITY_LOW = 0
const val PRIORITY_MEDIUM = 1
const val PRIORITY_HIGH = 2

data class Task(
    val id: Long,
    val title: String,
    val note: String,
    val subject: String,
    val lecturer: String,
    val deadline: Long,
    val reminders: List<Int>,
    val done: Boolean = false,
    val repeatWeekly: Boolean = false,
    val attachments: List<String> = emptyList(),
    val doneAt: Long = 0L,
    val archived: Boolean = false,
    val deletedAt: Long = 0L,
    val priority: Int = PRIORITY_MEDIUM,
    val subtasks: List<Subtask> = emptyList(),
    val tags: List<String> = emptyList(),
    val members: List<String> = emptyList(),
    val splitNote: String = ""
)

fun subjectKey(subject: String): String = subject.trim().lowercase()

fun List<String>.addUnique(raw: String): List<String> {
    val v = raw.trim()
    return if (v.isEmpty() || any { it.equals(v, ignoreCase = true) }) this else this + v
}

const val ARCHIVE_DAYS = 7

const val TRASH_DAYS = 7

fun isLinkAttachment(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

val REMIND_OPTIONS = listOf(
    0 to R.string.remind_on_time,
    60 to R.string.remind_1h,
    180 to R.string.remind_3h,
    1440 to R.string.remind_1d,
    4320 to R.string.remind_3d
)

val PRIORITY_OPTIONS = listOf(
    PRIORITY_LOW to R.string.priority_low,
    PRIORITY_MEDIUM to R.string.priority_medium,
    PRIORITY_HIGH to R.string.priority_high
)

fun priorityLabelRes(priority: Int): Int =
    PRIORITY_OPTIONS.firstOrNull { it.first == priority }?.second ?: R.string.priority_medium

val SNOOZE_OPTIONS = listOf(
    15 to R.string.duration_15m,
    30 to R.string.duration_30m,
    60 to R.string.duration_1h,
    180 to R.string.duration_3h
)

fun snoozeLabelRes(minutes: Int): Int =
    SNOOZE_OPTIONS.firstOrNull { it.first == minutes }?.second ?: R.string.duration_1h

fun Task.nextWeekly(now: Long): Task? {
    if (!repeatWeekly) return null
    var next = Instant.ofEpochMilli(deadline).atZone(ZoneId.systemDefault())
    do {
        next = next.plusWeeks(1)
    } while (next.toInstant().toEpochMilli() <= now)
    return copy(
        id = System.currentTimeMillis(),
        deadline = next.toInstant().toEpochMilli(),
        done = false,
        doneAt = 0L,
        archived = false,
        deletedAt = 0L,
        subtasks = subtasks.map { it.copy(done = false) }
    )
}
