package com.gewol.tugasku

import android.Manifest
import android.annotation.SuppressLint
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

object NotificationHelper {
    const val CHANNEL_ID = "tugas_reminder"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = LocaleHelper.wrap(context)
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = ctx.getString(R.string.notif_channel_desc) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        NotificationHelper.ensureChannel(context)
        // Context dengan bahasa pilihan pengguna, khusus untuk mengambil teks.
        val ctx = LocaleHelper.wrap(context)

        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val subject = intent.getStringExtra(ReminderScheduler.EXTRA_SUBJECT).orEmpty()
        val lecturer = intent.getStringExtra(ReminderScheduler.EXTRA_LECTURER).orEmpty()
        val deadline = intent.getLongExtra(ReminderScheduler.EXTRA_DEADLINE, 0L)

        val text = buildString {
            append(ctx.getString(R.string.notif_deadline, formatDeadline(deadline, TaskStore(context).use24Hour)))
            if (subject.isNotBlank()) append("\n").append(subject)
            if (lecturer.isNotBlank()) append(" • ").append(lecturer)
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun actionIntent(action: String) = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderScheduler.EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeText = ctx.getString(
            R.string.notif_action_snooze,
            ctx.getString(snoozeLabelRes(TaskStore(context).snoozeMinutes))
        )

        val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, ctx.getString(R.string.notif_action_done), actionIntent(NotificationActionReceiver.ACTION_DONE))
            .addAction(0, snoozeText, actionIntent(NotificationActionReceiver.ACTION_SNOOZE))
            .build()

        NotificationManagerCompat.from(context).notify(id.hashCode(), notif)
    }
}
