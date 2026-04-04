package com.jaek.clawapp.model

data class Note(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val priority: Float = 0.5f,
    val show: Boolean = true,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val createdBy: String = "user",
    val content: String = ""
)

data class NoteSettings(val maxShowOnHome: Int = 5)
