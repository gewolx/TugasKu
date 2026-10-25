package com.gewol.tugasku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Menangani tombol "Selesai" dan "Tunda" pada notifikasi pengingat (lama tunda dari Pengaturan). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id == -1L) return

        val store = TaskStore(context)
        val tasks = store.load()
        val task = tasks.find { it.id == id } ?: return

        when (intent.action) {
            ACTION_DONE -> {
                val next = task.nextWeekly(System.currentTimeMillis())
                val updated = tasks.map {
                    if (it.id == id) it.copy(done = true, repeatWeekly = false, doneAt = System.currentTimeMillis()) else it
                } + listOfNotNull(next)
                store.save(updated)
                ReminderScheduler.cancel(context, id)
                next?.let { ReminderScheduler.schedule(context, it) }
            }
            ACTION_SNOOZE -> ReminderScheduler.snooze(context, task, store.snoozeMinutes)
        }

        NotificationManagerCompat.from(context).cancel(id.hashCode())
        TaskWidgetProvider.refresh(context)
    }

    companion object {
        const val ACTION_DONE = "com.gewol.tugasku.ACTION_DONE"
        const val ACTION_SNOOZE = "com.gewol.tugasku.ACTION_SNOOZE"
    }
}
