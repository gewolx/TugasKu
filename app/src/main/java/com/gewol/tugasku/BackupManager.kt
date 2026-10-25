package com.gewol.tugasku

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/**
 * Cadangan dan pemulihan data ke satu file JSON.
 *
 * Isi file: pengaturan, semua tugas (termasuk arsip dan sampah), dan foto lampiran
 * (disimpan sebagai teks Base64). Tautan disimpan apa adanya, foto diacu dengan "photo:<nama file>".
 * Bahasa aplikasi tidak ikut dicadangkan.
 */
object BackupManager {

    /** Naikkan angka ini jika format file berubah dan tidak lagi bisa dibaca versi lama. */
    const val FORMAT_VERSION = 1

    private const val APP_ID = "tugasku"
    private const val PHOTO_PREFIX = "photo:"

    enum class Problem { INVALID, TOO_NEW }

    class BackupException(val problem: Problem) : Exception(problem.name)

    /** Tulis cadangan ke [uri]. Panggil dari thread latar belakang. Melempar exception jika gagal. */
    fun export(context: Context, uri: Uri) {
        val store = TaskStore(context)
        val tasks = store.load()

        val photoFiles = linkedMapOf<String, File>()
        val taskArray = JSONArray()
        tasks.forEach { task ->
            val attachments = task.attachments.mapNotNull { a ->
                if (isLinkAttachment(a)) {
                    a
                } else {
                    val file = File(a)
                    if (file.isFile) {
                        photoFiles[file.name] = file
                        PHOTO_PREFIX + file.name
                    } else {
                        null // foto sudah hilang dari penyimpanan
                    }
                }
            }
            taskArray.put(TaskStore.taskToJson(task.copy(attachments = attachments)))
        }

        val colors = JSONObject()
        store.subjectColors.forEach { (k, v) -> colors.put(k, v) }
        val settings = JSONObject()
            .put("themeMode", store.themeMode)
            .put("themeColor", store.themeColor)
            .put("use24Hour", store.use24Hour)
            .put("snoozeMinutes", store.snoozeMinutes)
            .put("defaultReminders", JSONArray(store.defaultReminders))
            .put("archiveDays", store.archiveDays)
            .put("weekStartsSunday", store.weekStartsSunday)
            .put("sortByPriority", store.sortByPriority)
            .put("subjectColors", colors)

        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Cannot open output stream")
        out.use { stream ->
            val w = stream.bufferedWriter(Charsets.UTF_8)
            w.write("{\"app\":\"$APP_ID\",\"version\":$FORMAT_VERSION,\"exportedAt\":${System.currentTimeMillis()},")
            w.write("\"settings\":")
            w.write(settings.toString())
            w.write(",\"tasks\":")
            w.write(taskArray.toString())
            // Foto ditulis satu per satu supaya memori tidak penuh.
            w.write(",\"photos\":{")
            var first = true
            for ((name, file) in photoFiles) {
                if (!first) w.write(",")
                first = false
                w.write(JSONObject.quote(name))
                w.write(":\"")
                w.write(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                w.write("\"")
            }
            w.write("}}")
            w.flush()
        }
    }

    /**
     * Pulihkan dari [uri]: tugas dan pengaturan saat ini DIGANTI. Panggil dari thread latar belakang.
     * Mengembalikan jumlah tugas (di luar sampah) yang dipulihkan, atau melempar [BackupException].
     * Data lama tidak disentuh sebelum seluruh file berhasil dibaca.
     */
    fun import(context: Context, uri: Uri): Int {
        val store = TaskStore(context)
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val restoredPhotos = mutableMapOf<String, String>() // nama di cadangan -> path baru

        var app = ""
        var version = -1
        var settings: JSONObject? = null
        var taskArray: JSONArray? = null

        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw BackupException(Problem.INVALID)
            input.use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8).buffered()).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "app" -> app = reader.nextString()
                            "version" -> version = reader.nextInt()
                            "settings" -> settings = readValue(reader) as? JSONObject
                            "tasks" -> taskArray = readValue(reader) as? JSONArray
                            "photos" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val name = reader.nextName()
                                    val bytes = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    // File(name).name membuang bagian folder, jaga-jaga file cadangan diubah orang lain.
                                    val target = File(dir, "r${stamp}_${File(name).name}")
                                    target.writeBytes(bytes)
                                    restoredPhotos[name] = target.absolutePath
                                }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }

            if (app != APP_ID || version < 1) throw BackupException(Problem.INVALID)
            if (version > FORMAT_VERSION) throw BackupException(Problem.TOO_NEW)
            val array = taskArray ?: throw BackupException(Problem.INVALID)

            val tasks = List(array.length()) { i ->
                val task = TaskStore.taskFromJson(array.getJSONObject(i))
                // Hanya tautan web dan foto yang ikut dipulihkan; path lain diabaikan demi keamanan.
                val attachments = task.attachments.mapNotNull { a ->
                    when {
                        isLinkAttachment(a) -> a
                        a.startsWith(PHOTO_PREFIX) -> restoredPhotos[a.removePrefix(PHOTO_PREFIX)]
                        else -> null
                    }
                }
                task.copy(attachments = attachments)
            }

            // Sampai sini file valid sepenuhnya, baru data lama diganti.
            val old = store.load()
            store.save(tasks)
            settings?.let { applySettings(store, it) }

            old.forEach { ReminderScheduler.cancel(context, it.id) }
            tasks.forEach { ReminderScheduler.schedule(context, it) }

            val inUse = tasks.flatMap { it.attachments }.toSet()
            old.flatMap { it.attachments }
                .filter { !isLinkAttachment(it) && it !in inUse }
                .forEach { runCatching { File(it).delete() } }

            return tasks.count { it.deletedAt == 0L }
        } catch (e: Exception) {
            // Gagal di tengah jalan: buang foto yang sudah terlanjur ditulis.
            restoredPhotos.values.forEach { runCatching { File(it).delete() } }
            if (e is BackupException) throw e
            throw BackupException(Problem.INVALID)
        }
    }

    private fun applySettings(store: TaskStore, s: JSONObject) {
        store.themeMode = s.optInt("themeMode", store.themeMode)
        store.themeColor = s.optInt("themeColor", store.themeColor)
        store.use24Hour = s.optBoolean("use24Hour", store.use24Hour)
        store.snoozeMinutes = s.optInt("snoozeMinutes", store.snoozeMinutes)
        s.optJSONArray("defaultReminders")?.let { a ->
            store.defaultReminders = List(a.length()) { a.getInt(it) }
        }
        store.archiveDays = s.optInt("archiveDays", store.archiveDays)
        store.weekStartsSunday = s.optBoolean("weekStartsSunday", store.weekStartsSunday)
        store.sortByPriority = s.optBoolean("sortByPriority", store.sortByPriority)
        s.optJSONObject("subjectColors")?.let { o ->
            store.subjectColors = o.keys().asSequence().associateWith { o.optInt(it, 0) }
        }
    }

    /** Baca satu nilai JSON apa saja menjadi objek org.json (kecuali foto, yang dibaca terpisah). */
    private fun readValue(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val o = JSONObject()
            r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName()
                o.put(name, readValue(r))
            }
            r.endObject()
            o
        }
        JsonToken.BEGIN_ARRAY -> {
            val a = JSONArray()
            r.beginArray()
            while (r.hasNext()) a.put(readValue(r))
            r.endArray()
            a
        }
        JsonToken.STRING -> r.nextString()
        JsonToken.NUMBER -> {
            val text = r.nextString()
            text.toLongOrNull() ?: text.toDouble()
        }
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NULL -> {
            r.nextNull()
            JSONObject.NULL
        }
        else -> {
            r.skipValue()
            JSONObject.NULL
        }
    }
}
