package com.jaek.clawapp.repository

import android.util.Log
import com.google.gson.Gson
import com.jaek.clawapp.model.Note
import com.jaek.clawapp.model.NoteSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteRepository {
    companion object {
        private const val TAG = "NoteRepository"
    }

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _settings = MutableStateFlow(NoteSettings())
    val settings: StateFlow<NoteSettings> = _settings.asStateFlow()

    var sendWsMessage: ((String) -> Unit)? = null
    var baseHttpUrl: String? = null
    var authToken: String? = null

    private val gson = Gson()
    private val httpClient = OkHttpClient()

    // ── Apply server data ────────────────────────────────────────────────────

    fun applyNotesList(raw: List<Any?>) {
        _notes.value = raw.mapNotNull { parseNote(it) }
    }

    fun applySettings(raw: Map<String, Any?>) {
        val maxShow = (raw["maxShowOnHome"] as? Double)?.toInt() ?: 5
        _settings.value = NoteSettings(maxShowOnHome = maxShow)
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parseNote(raw: Any?): Note? {
        val m = raw as? Map<String, Any?> ?: return null
        return try {
            Note(
                id = m["id"] as? String ?: return null,
                name = m["name"] as? String ?: m["id"] as? String ?: return null,
                tags = (m["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                priority = (m["priority"] as? Double)?.toFloat() ?: 0.5f,
                show = m["show"] as? Boolean ?: true,
                archived = m["archived"] as? Boolean ?: false,
                createdAt = (m["createdAt"] as? Double)?.toLong() ?: 0L,
                modifiedAt = (m["modifiedAt"] as? Double)?.toLong() ?: 0L,
                createdBy = m["createdBy"] as? String ?: "user",
                content = m["content"] as? String ?: ""
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse note: ${e.message}")
            null
        }
    }

    // ── WS mutations ──────────────────────────────────────────────────────────

    fun createNote(
        name: String,
        content: String,
        tags: List<String> = emptyList(),
        priority: Float = 0.5f,
        show: Boolean = true,
        createdBy: String = "user"
    ) {
        val msg = gson.toJson(mapOf(
            "type" to "create_note",
            "name" to name.ifBlank { null },
            "content" to content,
            "meta" to mapOf("tags" to tags, "priority" to priority, "show" to show, "createdBy" to createdBy)
        ))
        sendWsMessage?.invoke(msg)
    }

    fun updateNote(
        id: String,
        content: String,
        name: String? = null,
        tags: List<String>? = null,
        priority: Float? = null,
        show: Boolean? = null
    ) {
        val meta = mutableMapOf<String, Any?>()
        if (name != null) meta["name"] = name
        if (tags != null) meta["tags"] = tags
        if (priority != null) meta["priority"] = priority
        if (show != null) meta["show"] = show

        val msg = gson.toJson(mapOf(
            "type" to "update_note",
            "id" to id,
            "content" to content,
            "meta" to meta
        ))
        sendWsMessage?.invoke(msg)

        // Optimistic update
        _notes.value = _notes.value.map { note ->
            if (note.id == id) {
                note.copy(
                    content = content,
                    name = name ?: note.name,
                    tags = tags ?: note.tags,
                    priority = priority ?: note.priority,
                    show = show ?: note.show,
                    modifiedAt = System.currentTimeMillis()
                )
            } else note
        }
    }

    fun archiveNote(id: String) {
        sendWsMessage?.invoke(gson.toJson(mapOf("type" to "archive_note", "id" to id)))
    }

    fun unarchiveNote(id: String) {
        sendWsMessage?.invoke(gson.toJson(mapOf("type" to "unarchive_note", "id" to id)))
    }

    fun deleteNote(id: String) {
        sendWsMessage?.invoke(gson.toJson(mapOf("type" to "delete_note", "id" to id)))
    }

    fun saveDraft(id: String, content: String) {
        sendWsMessage?.invoke(gson.toJson(mapOf(
            "type" to "save_draft",
            "id" to id,
            "content" to content
        )))
    }

    // ── HTTP fetch (for full content) ─────────────────────────────────────────

    suspend fun fetchNote(id: String): Note? = withContext(Dispatchers.IO) {
        val url = baseHttpUrl ?: return@withContext null
        val token = authToken ?: return@withContext null
        try {
            val request = Request.Builder()
                .url("$url/notes/$id")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(body, Map::class.java) as? Map<String, Any?> ?: return@withContext null
            val meta = parsed["meta"] as? Map<String, Any?> ?: return@withContext null
            val content = parsed["content"] as? String ?: ""
            parseNote(meta)?.copy(content = content)
        } catch (e: Exception) {
            Log.e(TAG, "fetchNote failed: ${e.message}")
            null
        }
    }

    // ── Name generation helper ────────────────────────────────────────────────

    fun generateName(): String {
        val prefix = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val existing = _notes.value.map { it.id }.filter { it.startsWith("$prefix.") }
        var n = 1
        while (existing.contains("$prefix.${n.toString().padStart(3, '0')}")) n++
        return "$prefix.${n.toString().padStart(3, '0')}"
    }
}
