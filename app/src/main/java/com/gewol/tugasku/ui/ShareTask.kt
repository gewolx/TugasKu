package com.gewol.tugasku.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.gewol.tugasku.PRIORITY_MEDIUM
import com.gewol.tugasku.R
import com.gewol.tugasku.Task
import com.gewol.tugasku.isLinkAttachment
import com.gewol.tugasku.priorityLabelRes

/** Susun teks tugas yang enak dibaca di WhatsApp (format tebal pakai *...*). */
fun buildShareText(context: Context, task: Task, use24Hour: Boolean): String = buildString {
    append('*').append(task.title).append("*\n")
    if (task.subject.isNotBlank()) append(task.subject).append('\n')
    if (task.lecturer.isNotBlank()) append(task.lecturer).append('\n')
    append(context.getString(R.string.share_deadline, formatDeadline(task.deadline, use24Hour))).append('\n')
    if (task.priority != PRIORITY_MEDIUM) {
        append(context.getString(R.string.share_priority, context.getString(priorityLabelRes(task.priority)))).append('\n')
    }
    if (task.note.isNotBlank()) append('\n').append(task.note).append('\n')
    if (task.subtasks.isNotEmpty()) {
        append('\n').append(context.getString(R.string.share_subtasks)).append('\n')
        task.subtasks.forEach { append(if (it.done) "- [x] " else "- [ ] ").append(it.title).append('\n') }
    }
    val links = task.attachments.filter { isLinkAttachment(it) }
    if (links.isNotEmpty()) {
        append("\n").append(context.getString(R.string.share_attachments)).append('\n')
        links.forEach { append(it).append('\n') }
    }
    append("\n").append(context.getString(R.string.share_footer))
}

/**
 * Kirim detail tugas lewat WhatsApp. Jika WhatsApp tidak terpasang,
 * tampilkan pilihan aplikasi berbagi bawaan Android.
 */
fun shareTask(context: Context, task: Task, use24Hour: Boolean) {
    val text = buildShareText(context, task, use24Hour)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(
            Intent(send).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_chooser))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
