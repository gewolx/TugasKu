package com.gewol.tugasku

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.gewol.tugasku.ui.relativeLabel

/** Widget layar utama: menampilkan sampai 5 tugas aktif dengan tenggat terdekat. */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    companion object {
        private val ROWS = intArrayOf(R.id.w_row_1, R.id.w_row_2, R.id.w_row_3, R.id.w_row_4, R.id.w_row_5)
        private val TITLES = intArrayOf(R.id.w_title_1, R.id.w_title_2, R.id.w_title_3, R.id.w_title_4, R.id.w_title_5)
        private val INFOS = intArrayOf(R.id.w_info_1, R.id.w_info_2, R.id.w_info_3, R.id.w_info_4, R.id.w_info_5)

        /** Panggil setiap kali data tugas berubah. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val now = System.currentTimeMillis()
            // Context dengan bahasa pilihan pengguna, khusus untuk mengambil teks.
            val ctx = LocaleHelper.wrap(context)
            val active = TaskStore(context).load().filter { !it.done && it.deletedAt == 0L }.sortedBy { it.deadline }
            val shown = active.take(ROWS.size)

            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            views.setTextViewText(
                R.id.w_count,
                ctx.resources.getQuantityString(R.plurals.widget_count, active.size, active.size)
            )
            views.setTextViewText(R.id.w_empty, ctx.getString(R.string.widget_empty))
            views.setViewVisibility(R.id.w_empty, if (active.isEmpty()) View.VISIBLE else View.GONE)

            for (i in ROWS.indices) {
                val task = shown.getOrNull(i)
                if (task == null) {
                    views.setViewVisibility(ROWS[i], View.GONE)
                    continue
                }
                views.setViewVisibility(ROWS[i], View.VISIBLE)
                views.setTextViewText(TITLES[i], task.title)
                val info = buildString {
                    append(relativeLabel(ctx, task.deadline, now))
                    if (task.subject.isNotBlank()) append(" • ").append(task.subject)
                }
                views.setTextViewText(INFOS[i], info)
            }

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.w_root, open)
            manager.updateAppWidget(id, views)
        }
    }
}
