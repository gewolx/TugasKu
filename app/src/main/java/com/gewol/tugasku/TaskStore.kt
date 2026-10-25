package com.gewol.tugasku

import android.content.Context
import com.gewol.tugasku.ui.DEFAULT_THEME_COLOR
import org.json.JSONArray
import org.json.JSONObject

/** Penyimpanan lokal sederhana (SharedPreferences + JSON), tanpa library tambahan. */
class TaskStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("tugasku", Context.MODE_PRIVATE)

    fun load(): List<Task> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> taskFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(taskToJson(it)) }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    /** 0 = ikuti sistem, 1 = terang, 2 = gelap */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** Nomor tema warna (indeks di APP_PALETTES); bawaan = biru ([DEFAULT_THEME_COLOR]). */
    var themeColor: Int
        get() = prefs.getInt(KEY_THEME_COLOR, DEFAULT_THEME_COLOR)
        set(value) = prefs.edit().putInt(KEY_THEME_COLOR, value).apply()

    /** true = format 24 jam (default), false = 12 jam (AM/PM) */
    var use24Hour: Boolean
        get() = prefs.getBoolean(KEY_24H, true)
        set(value) = prefs.edit().putBoolean(KEY_24H, value).apply()

    /** Lama tunda notifikasi, dalam menit (bawaan 60). */
    var snoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, 60)
        set(value) = prefs.edit().putInt(KEY_SNOOZE, value).apply()

    /** Pengingat bawaan untuk tugas baru (menit sebelum tenggat); boleh kosong. */
    var defaultReminders: List<Int>
        get() = prefs.getString(KEY_DEFAULT_REMIND, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: listOf(60)
        set(value) = prefs.edit().putString(KEY_DEFAULT_REMIND, value.joinToString(",")).apply()

    /** Tugas selesai diarsipkan otomatis setelah sekian hari; 0 = tidak pernah. */
    var archiveDays: Int
        get() = prefs.getInt(KEY_ARCHIVE_DAYS, ARCHIVE_DAYS)
        set(value) = prefs.edit().putInt(KEY_ARCHIVE_DAYS, value).apply()

    /** Ringkasan harian: notifikasi pagi berisi tugas hari ini. Aktif secara bawaan. */
    var digestEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIGEST_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_DIGEST_ON, value).apply()

    /** Jam ringkasan harian, dalam menit sejak tengah malam (bawaan 07.00 = 420). */
    var digestMinutes: Int
        get() = prefs.getInt(KEY_DIGEST_TIME, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_DIGEST_TIME, value).apply()

    /** true = minggu di kalender dimulai hari Minggu, false = Senin (bawaan). */
    var weekStartsSunday: Boolean
        get() = prefs.getBoolean(KEY_WEEK_SUNDAY, false)
        set(value) = prefs.edit().putBoolean(KEY_WEEK_SUNDAY, value).apply()

    /** true = daftar diurutkan berdasarkan prioritas, false = berdasarkan tenggat (bawaan). */
    var sortByPriority: Boolean
        get() = prefs.getBoolean(KEY_SORT_PRIORITY, false)
        set(value) = prefs.edit().putBoolean(KEY_SORT_PRIORITY, value).apply()

    /** Warna pilihan pengguna per mata kuliah: [subjectKey] -> indeks palet. Yang tidak ada di sini diwarnai otomatis. */
    var subjectColors: Map<String, Int>
        get() = prefs.getString(KEY_SUBJECT_COLORS, null)?.let { raw ->
            runCatching {
                val o = JSONObject(raw)
                o.keys().asSequence().associateWith { o.getInt(it) }
            }.getOrNull()
        } ?: emptyMap()
        set(value) {
            val o = JSONObject()
            value.forEach { (k, v) -> o.put(k, v) }
            prefs.edit().putString(KEY_SUBJECT_COLORS, o.toString()).apply()
        }

    companion object {
        private const val KEY_TASKS = "tasks"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_THEME_COLOR = "theme_color"
        private const val KEY_24H = "use_24h"
        private const val KEY_SNOOZE = "snooze_minutes"
        private const val KEY_DEFAULT_REMIND = "default_reminders"
        private const val KEY_ARCHIVE_DAYS = "archive_days"
        private const val KEY_DIGEST_ON = "digest_enabled"
        private const val KEY_DIGEST_TIME = "digest_minutes"
        private const val KEY_WEEK_SUNDAY = "week_starts_sunday"
        private const val KEY_SORT_PRIORITY = "sort_by_priority"
        private const val KEY_SUBJECT_COLORS = "subject_colors"

        /** Ubah satu tugas ke JSON. Dipakai penyimpanan lokal dan file cadangan. */
        fun taskToJson(t: Task): JSONObject {
            val subtasks = JSONArray()
            t.subtasks.forEach {
                subtasks.put(
                    JSONObject().put("id", it.id).put("title", it.title).put("done", it.done)
                )
            }
            return JSONObject()
                .put("id", t.id)
                .put("title", t.title)
                .put("note", t.note)
                .put("subject", t.subject)
                .put("lecturer", t.lecturer)
                .put("deadline", t.deadline)
                .put("reminders", JSONArray(t.reminders))
                .put("done", t.done)
                .put("repeat", t.repeatWeekly)
                .put("attachments", JSONArray(t.attachments))
                .put("doneAt", t.doneAt)
                .put("archived", t.archived)
                .put("deletedAt", t.deletedAt)
                .put("priority", t.priority)
                .put("subtasks", subtasks)
        }

        /** Baca satu tugas dari JSON. Field yang tidak ada (data lama) memakai nilai bawaan. */
        fun taskFromJson(o: JSONObject): Task {
            // Data lama hanya punya satu angka "remind"; data baru punya array "reminders".
            val reminders = o.optJSONArray("reminders")
                ?.let { a -> List(a.length()) { a.getInt(it) } }
                ?: listOf(o.optInt("remind", 60))
            val subtasks = o.optJSONArray("subtasks")?.let { a ->
                List(a.length()) { i ->
                    val s = a.getJSONObject(i)
                    Subtask(
                        id = s.getLong("id"),
                        title = s.optString("title"),
                        done = s.optBoolean("done", false)
                    )
                }
            } ?: emptyList()
            return Task(
                id = o.getLong("id"),
                title = o.getString("title"),
                note = o.optString("note"),
                subject = o.optString("subject"),
                lecturer = o.optString("lecturer"),
                deadline = o.getLong("deadline"),
                reminders = reminders,
                done = o.optBoolean("done", false),
                repeatWeekly = o.optBoolean("repeat", false),
                // Data lama belum punya "attachments".
                attachments = o.optJSONArray("attachments")
                    ?.let { a -> List(a.length()) { a.getString(it) } }
                    ?: emptyList(),
                doneAt = o.optLong("doneAt", 0L),
                archived = o.optBoolean("archived", false),
                deletedAt = o.optLong("deletedAt", 0L),
                priority = o.optInt("priority", PRIORITY_MEDIUM).coerceIn(PRIORITY_LOW, PRIORITY_HIGH),
                subtasks = subtasks
            )
        }
    }
}
