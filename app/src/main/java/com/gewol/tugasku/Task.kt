package com.gewol.tugasku

import java.time.Instant
import java.time.ZoneId

/** Satu butir checklist di dalam tugas. */
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
    val deadline: Long,              // epoch millis
    val reminders: List<Int>,        // daftar "menit sebelum tenggat"; kosong = tanpa pengingat
    val done: Boolean = false,
    val repeatWeekly: Boolean = false,
    /** Lampiran: tautan (diawali http/https) atau path file foto di penyimpanan internal aplikasi. */
    val attachments: List<String> = emptyList(),
    /** Kapan ditandai selesai (epoch millis); 0 = belum selesai / data lama. */
    val doneAt: Long = 0L,
    /** Tugas selesai yang sudah dipindah ke arsip. */
    val archived: Boolean = false,
    /** Kapan dipindah ke sampah (epoch millis); 0 = tidak di sampah. */
    val deletedAt: Long = 0L,
    /** Prioritas: [PRIORITY_LOW], [PRIORITY_MEDIUM] (bawaan, juga untuk data lama), atau [PRIORITY_HIGH]. */
    val priority: Int = PRIORITY_MEDIUM,
    /** Checklist subtugas; kosong = tidak ada. */
    val subtasks: List<Subtask> = emptyList()
)

/** Kunci penyimpanan warna mata kuliah: huruf kecil, tanpa spasi di tepi. */
fun subjectKey(subject: String): String = subject.trim().lowercase()

/** Jeda arsip otomatis bawaan (hari). Bisa diubah di Pengaturan. */
const val ARCHIVE_DAYS = 7

/** Tugas di sampah dihapus permanen setelah sekian hari. */
const val TRASH_DAYS = 7

/** True jika lampiran ini adalah tautan web (bukan file foto lokal). */
fun isLinkAttachment(value: String): Boolean =
    value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

/** Pilihan waktu pengingat: menit sebelum tenggat -> id teks (string resource). */
val REMIND_OPTIONS = listOf(
    0 to R.string.remind_on_time,
    60 to R.string.remind_1h,
    180 to R.string.remind_3h,
    1440 to R.string.remind_1d,
    4320 to R.string.remind_3d
)

/** Pilihan prioritas: nilai -> id teks (string resource). Urutan = tampilan di editor. */
val PRIORITY_OPTIONS = listOf(
    PRIORITY_LOW to R.string.priority_low,
    PRIORITY_MEDIUM to R.string.priority_medium,
    PRIORITY_HIGH to R.string.priority_high
)

fun priorityLabelRes(priority: Int): Int =
    PRIORITY_OPTIONS.firstOrNull { it.first == priority }?.second ?: R.string.priority_medium

/** Pilihan lama tunda notifikasi: menit -> id teks (string resource). */
val SNOOZE_OPTIONS = listOf(
    15 to R.string.duration_15m,
    30 to R.string.duration_30m,
    60 to R.string.duration_1h,
    180 to R.string.duration_3h
)

/** Teks lama tunda untuk tombol notifikasi (jatuh ke "1 jam" jika nilainya tidak dikenal). */
fun snoozeLabelRes(minutes: Int): Int =
    SNOOZE_OPTIONS.firstOrNull { it.first == minutes }?.second ?: R.string.duration_1h

/**
 * Untuk tugas mingguan: buat tugas berikutnya (tenggat + 1 minggu, diulang
 * sampai jatuh di masa depan). Mengembalikan null jika tugas tidak berulang.
 */
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
