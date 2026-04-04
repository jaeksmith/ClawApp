package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.Note
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    notes: List<Note>,
    onNoteView: (Note) -> Unit,
    onNoteEdit: (Note) -> Unit,
    onNewNote: () -> Unit,
    onBack: () -> Unit,
    onArchive: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showArchived by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showFilter by remember { mutableStateOf("all") } // all, shown, hidden

    var contextMenuNote by remember { mutableStateOf<Note?>(null) }
    var deleteTargetNote by remember { mutableStateOf<Note?>(null) }
    var deleteConfirmChecked by remember { mutableStateOf(false) }

    val allTags = remember(notes) {
        notes.flatMap { it.tags }.distinct().sorted()
    }

    val filtered = remember(notes, showArchived, searchText, selectedTag, showFilter) {
        notes.filter { note ->
            note.archived == showArchived &&
            (searchText.isBlank() || note.name.contains(searchText, ignoreCase = true) ||
                note.tags.any { it.contains(searchText, ignoreCase = true) }) &&
            (selectedTag == null || note.tags.contains(selectedTag)) &&
            when (showFilter) {
                "shown" -> note.show
                "hidden" -> !note.show
                else -> true
            }
        }.sortedWith(
            compareByDescending<Note> { it.priority }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.name }
        )
    }

    val prioColor = { p: Float ->
        when {
            p > 0.7f -> Color(0xFF4CAF50)
            p >= 0.3f -> Color(0xFFFFC107)
            else -> Color(0xFF888888)
        }
    }

    val fmt = SimpleDateFormat("MM/dd/yy", Locale.US)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNewNote) {
                        Icon(Icons.Default.Add, "New note")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filter row
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !showArchived,
                    onClick = { showArchived = false },
                    label = { Text("Live", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = showArchived,
                    onClick = { showArchived = true },
                    label = { Text("Archived", fontSize = 12.sp) }
                )
                Spacer(Modifier.weight(1f))
                // Show filter
                var showDropdown by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = showFilter != "all",
                        onClick = { showDropdown = true },
                        label = { Text(showFilter.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }
                    )
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                        listOf("all", "shown", "hidden").forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.replaceFirstChar { it.uppercase() }) },
                                onClick = { showFilter = f; showDropdown = false }
                            )
                        }
                    }
                }
            }

            // Tag chips
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("All tags", fontSize = 11.sp) }
                    )
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Search
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search notes...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            // Note list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { note ->
                    var showMenu by remember { mutableStateOf(false) }

                    Box {
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onNoteView(note) },
                                    onLongClick = { showMenu = true }
                                ),
                            headlineContent = {
                                Text(note.name, fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Column {
                                    if (note.tags.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            note.tags.forEach { tag ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        tag,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        fmt.format(Date(note.modifiedAt)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .then(Modifier.wrapContentSize()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(12.dp),
                                        shape = CircleShape,
                                        color = prioColor(note.priority)
                                    ) {}
                                }
                            }
                        )

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("View") }, onClick = { showMenu = false; onNoteView(note) })
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onNoteEdit(note) })
                            if (note.archived) {
                                DropdownMenuItem(text = { Text("Unarchive") }, onClick = { showMenu = false; onUnarchive(note.id) })
                            } else {
                                DropdownMenuItem(text = { Text("Archive") }, onClick = { showMenu = false; onArchive(note.id) })
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    deleteTargetNote = note
                                    deleteConfirmChecked = false
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteTargetNote?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTargetNote = null },
            title = { Text("Delete note?") },
            text = {
                Column {
                    Text("This will permanently delete \"${note.name}\".")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = deleteConfirmChecked,
                            onCheckedChange = { deleteConfirmChecked = it }
                        )
                        Text("I want to delete this note", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(note.id); deleteTargetNote = null },
                    enabled = deleteConfirmChecked
                ) {
                    Text("Delete", color = if (deleteConfirmChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetNote = null }) { Text("Cancel") }
            }
        )
    }
}
