package com.gewol.tugasku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Menjadwalkan ulang semua pengingat setelah HP restart, setelah aplikasi
 * di-update, atau setelah zona waktu berubah. AlarmManager menghapus semua
 * alarm saat reboot, jadi tanpa ini pengingat tidak akan pernah berbunyi.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                ReminderScheduler.rescheduleAll(context, TaskStore(context).load())
                DigestScheduler.schedule(context)
            }
        }
    }
}
