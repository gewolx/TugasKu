package com.gewol.tugasku

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val NOTIF_DEADLINE = 0
const val NOTIF_UPDATE = 1
const val NOTIF_DIGEST = 2

data class NotifRecord(
    val id: Long,
    val type: Int,
    val title: String,
    val message: String,
    val time: Long,
    val taskId: Long = 0L,
    val read: Boolean = false
)

object NotificationHistory {

    private const val PREFS = "tugasku_notif_history"
    private const val KEY_RECORDS = "records"
    private const val KEY_LAST_UPDATE = "last_update_version"
    private const val MAX_RECORDS = 100

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun load(context: Context): List<NotifRecord> {
        val raw = prefs(context).getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                NotifRecord(
                    id = o.getLong("id"),
                    type = o.optInt("type", NOTIF_DEADLINE),
                    title = o.optString("title"),
                    message = o.optString("message"),
                    time = o.optLong("time"),
                    taskId = o.optLong("taskId", 0L),
                    read = o.optBoolean("read", false)
                )
            }.sortedByDescending { it.time }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(context: Context, list: List<NotifRecord>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("type", it.type)
                    .put("title", it.title)
                    .put("message", it.message)
                    .put("time", it.time)
                    .put("taskId", it.taskId)
                    .put("read", it.read)
            )
        }
        prefs(context).edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    @Synchronized
    fun add(context: Context, type: Int, title: String, message: String, taskId: Long = 0L) {
        val list = load(context)
        val now = System.currentTimeMillis()
        val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
        val record = NotifRecord(id, type, title, message, now, taskId, read = false)
        write(context, (listOf(record) + list).take(MAX_RECORDS))
    }

    @Synchronized
    fun recordUpdate(context: Context, versionCode: Long, title: String, message: String): Boolean {
        val p = prefs(context)
        if (versionCode <= p.getLong(KEY_LAST_UPDATE, 0L)) return false
        add(context, NOTIF_UPDATE, title, message)
        p.edit().putLong(KEY_LAST_UPDATE, versionCode).apply()
        return true
    }

    @Synchronized
    fun markRead(context: Context, id: Long) {
        write(context, load(context).map { if (it.id == id) it.copy(read = true) else it })
    }

    @Synchronized
    fun markAllRead(context: Context) {
        write(context, load(context).map { it.copy(read = true) })
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        write(context, load(context).filterNot { it.id == id })
    }

    @Synchronized
    fun clear(context: Context) {
        write(context, emptyList())
    }
}
