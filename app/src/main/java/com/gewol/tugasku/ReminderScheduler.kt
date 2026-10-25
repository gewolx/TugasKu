package com.gewol.tugasku

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object ReminderScheduler {

    const val EXTRA_ID = "extra_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_SUBJECT = "extra_subject"
    const val EXTRA_LECTURER = "extra_lecturer"
    const val EXTRA_DEADLINE = "extra_deadline"

    // Tiap tugas punya beberapa "slot" alarm: satu per pilihan di REMIND_OPTIONS + satu untuk tunda.
    private const val SNOOZE_SLOT = 15

    private fun requestCode(id: Long, slot: Int) = id.hashCode() * 16 + slot

    /** Jadwalkan semua pengingat tugas (menghapus jadwal lama lebih dulu). */
    fun schedule(context: Context, task: Task) {
        cancel(context, task.id)
        if (task.done || task.deletedAt > 0L) return

        task.reminders.forEach { minutes ->
            val slot = REMIND_OPTIONS.indexOfFirst { it.first == minutes }
            if (slot >= 0) scheduleAt(context, task, task.deadline - minutes * 60_000L, slot)
        }
    }

    /** Tunda pengingat [minutes] menit dari sekarang. */
    fun snooze(context: Context, task: Task, minutes: Int) {
        if (task.done || task.deletedAt > 0L) return
        scheduleAt(context, task, System.currentTimeMillis() + minutes * 60_000L, SNOOZE_SLOT)
    }

    private fun scheduleAt(context: Context, task: Task, triggerAt: Long, slot: Int) {
        if (triggerAt <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, task.id)
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_SUBJECT, task.subject)
            putExtra(EXTRA_LECTURER, task.lecturer)
            putExtra(EXTRA_DEADLINE, task.deadline)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(task.id, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Tidak butuh izin "exact alarm", cukup akurat untuk pengingat tugas.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (slot in 0..SNOOZE_SLOT) {
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(id, slot),
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun rescheduleAll(context: Context, tasks: List<Task>) {
        tasks.forEach { schedule(context, it) }
    }
}
