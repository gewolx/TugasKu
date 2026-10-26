package com.gewol.tugasku

data class TaskTemplate(
    val id: Long,
    val name: String,
    val title: String = "",
    val subject: String = "",
    val lecturer: String = "",
    val note: String = "",
    val priority: Int = PRIORITY_MEDIUM,
    val reminders: List<Int> = emptyList(),
    val tags: List<String> = emptyList(),
    val subtasks: List<String> = emptyList()
)
