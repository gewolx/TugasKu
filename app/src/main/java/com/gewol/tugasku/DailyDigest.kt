package com.gewol.tugasku

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gewol.tugasku.ui.formatDeadline
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DigestScheduler {

    private const val REQUEST_CODE = 7001

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DigestReceiver::class.java),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context) {
        val store = TaskStore(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.cancel(pi)
        if (!store.digestEnabled) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(store.digestMinutes), pi)
    }

    private fun nextTrigger(minutesOfDay: Int): Long {
        val zone = ZoneId.systemDefault()
        var t = LocalDate.now(zone).atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(zone)
        if (t.toInstant().toEpochMilli() <= System.currentTimeMillis()) {
            t = LocalDate.now(zone).plusDays(1).atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(zone)
        }
        return t.toInstant().toEpochMilli()
    }
}

class DigestReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        DigestScheduler.schedule(context)

        val store = TaskStore(context)
        if (!store.digestEnabled) return

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val active = store.load().filter { !it.done && !it.archived && it.deletedAt == 0L }
        val overdue = active.filter { it.deadline < now }
        val dueToday = active.filter {
            it.deadline >= now && Instant.ofEpochMilli(it.deadline).atZone(zone).toLocalDate() == today
        }
        if (overdue.isEmpty() && dueToday.isEmpty()) return

        val ctx = LocaleHelper.wrap(context)
        val parts = buildList {
            if (dueToday.isNotEmpty()) {
                add(ctx.resources.getQuantityString(R.plurals.digest_due_today, dueToday.size, dueToday.size))
            }
            if (overdue.isNotEmpty()) add(ctx.getString(R.string.digest_overdue, overdue.size))
        }
        val summary = parts.joinToString(" • ")

        val listed = (overdue + dueToday).sortedBy { it.deadline }
        val shown = listed.take(4)
        val full = buildString {
            append(summary)
            shown.forEach {
                append("\n• ").append(it.title)
                append(" (").append(formatDeadline(it.deadline, store.use24Hour)).append(")")
            }
            if (listed.size > shown.size) {
                append("\n").append(ctx.getString(R.string.digest_more, listed.size - shown.size))
            }
        }
        val title = ctx.getString(R.string.digest_title)

        NotificationHistory.add(context, NOTIF_DIGEST, title, full)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context, ctx)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    private fun ensureChannel(context: Context, ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.digest_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = ctx.getString(R.string.digest_channel_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "tugas_digest"
        private const val NOTIF_ID = 7001
    }
}
