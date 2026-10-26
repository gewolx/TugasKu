package com.gewol.tugasku

import android.content.Intent

data class SharedTask(val title: String, val note: String, val seq: Long)

object ShareIntake {

    private const val MAX_TITLE = 100

    fun parse(intent: Intent?): SharedTask? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val raw = (intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT))?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val first = lines.first()
        val seq = System.currentTimeMillis()
        return if (first.length <= MAX_TITLE) {
            SharedTask(first, lines.drop(1).joinToString("\n"), seq)
        } else {
            SharedTask(first.take(MAX_TITLE).trimEnd() + "…", raw, seq)
        }
    }
}
