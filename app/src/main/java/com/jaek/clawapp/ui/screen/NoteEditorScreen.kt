package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.Note
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: Note?,
    startInEditMode: Boolean,
    onSave: (name: String, content: String, tags: List<String>, priority: Float, show: Boolean) -> Unit,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onArchive: (String) -> Unit,
    onSaveDraft: (id: String, content: String) -> Unit
) {
    val isNew = initialNote == null
    var isEditMode by remember { mutableStateOf(startInEditMode) }
    var name by remember { mutableStateOf(initialNote?.name ?: "") }
    var contentValue by remember { mutableStateOf(TextFieldValue(initialNote?.content ?: "")) }
    var tags by remember { mutableStateOf(initialNote?.tags ?: emptyList<String>()) }
    var priority by remember { mutableStateOf(initialNote?.priority ?: 0.5f) }
    var show by remember { mutableStateOf(initialNote?.show ?: true) }
    var metaExpanded by remember { mutableStateOf(isEditMode) }
    var selectedTab by remember { mutableStateOf(0) } // 0=Visual, 1=Source
    var addTagText by remember { mutableStateOf("") }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var discardConfirmed by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmed by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val noteId = initialNote?.id ?: name

    val dateFmt = SimpleDateFormat("MM/dd/yy HH:mm", Locale.US)

    val originalContent = initialNote?.content ?: ""
    val hasChanges = contentValue.text != originalContent || name != (initialNote?.name ?: "")

    // Auto-save draft every 30s when content changes
    LaunchedEffect(contentValue.text) {
        if (noteId.isNotBlank() && contentValue.text != originalContent) {
            delay(30_000)
            onSaveDraft(noteId, contentValue.text)
            snackbarHostState.showSnackbar("Draft saved")
        }
    }

    fun insertMarkdown(prefix: String, suffix: String = "") {
        val text = contentValue.text
        val sel = contentValue.selection
        val selectedText = if (sel.start != sel.end) text.substring(sel.start, sel.end) else ""
        val insertion = prefix + selectedText + suffix
        val newText = text.substring(0, sel.start) + insertion + text.substring(sel.end)
        val newCursor = sel.start + insertion.length
        contentValue = TextFieldValue(newText, TextRange(newCursor))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            placeholder = { Text("Note name...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(name.ifBlank { "Untitled" }, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditMode && hasChanges) {
                            showDiscardDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // View/Edit toggle
                    TextButton(onClick = {
                        if (isEditMode && hasChanges) {
                            // Save when switching to view
                            onSave(name.ifBlank { noteId }, contentValue.text, tags.toList(), priority, show)
                        }
                        isEditMode = !isEditMode
                        metaExpanded = isEditMode
                    }) {
                        Text(if (isEditMode) "View" else "Edit")
                    }
                    // Overflow
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More")
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            if (initialNote != null) {
                                DropdownMenuItem(
                                    text = { Text(if (initialNote.archived) "Unarchive" else "Archive") },
                                    onClick = { overflowExpanded = false; onArchive(initialNote.id) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    overflowExpanded = false
                                    showDeleteDialog = true
                                    deleteConfirmed = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Tab row (edit mode only)
            if (isEditMode) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Visual") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Source") })
                }

                // Formatting toolbar
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    @Composable fun FmtBtn(label: String, onClick: () -> Unit) {
                        OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text(label, fontSize = 12.sp) }
                    }
                    FmtBtn("B") { insertMarkdown("**", "**") }
                    FmtBtn("I") { insertMarkdown("_", "_") }
                    FmtBtn("H1") { insertMarkdown("# ") }
                    FmtBtn("H2") { insertMarkdown("## ") }
                    FmtBtn("List") { insertMarkdown("- ") }
                    FmtBtn("☑") { insertMarkdown("- [ ] ") }
                }
            }

            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                if (isEditMode && selectedTab == 1) {
                    // Source tab - raw text field
                    OutlinedTextField(
                        value = contentValue,
                        onValueChange = { contentValue = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("Write your note in markdown...") }
                    )
                } else {
                    // Visual tab or view mode — simple styled text display
                    val scrollState = rememberScrollState()
                    if (isEditMode) {
                        OutlinedTextField(
                            value = contentValue,
                            onValueChange = { contentValue = it },
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { Text("Write your note...") }
                        )
                    } else {
                        // Read-only view
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = contentValue.text.ifBlank { "(no content)" },
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(scrollState),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Metadata section
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                TextButton(onClick = { metaExpanded = !metaExpanded }) {
                    Text(if (metaExpanded) "▴ Metadata" else "▾ Metadata")
                }

                if (metaExpanded) {
                    // Tags
                    Text("Tags", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.forEachIndexed { idx, tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag, fontSize = 11.sp) },
                                trailingIcon = {
                                    TextButton(onClick = {
                                        val updated = tags.toMutableList()
                                        updated.removeAt(idx)
                                        tags = updated
                                    }, contentPadding = PaddingValues(0.dp)) {
                                        Text("✕", fontSize = 10.sp)
                                    }
                                },
                                enabled = isEditMode
                            )
                        }
                    }
                    if (isEditMode) {
                        OutlinedTextField(
                            value = addTagText,
                            onValueChange = { addTagText = it },
                            singleLine = true,
                            placeholder = { Text("Add tag, press Enter") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    if (addTagText.isNotBlank()) {
                                        tags = tags + addTagText.trim()
                                        addTagText = ""
                                    }
                                }
                            )
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Priority
                    Text("Priority", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "%.3f".format(priority),
                            onValueChange = { v -> v.toFloatOrNull()?.let { priority = it.coerceIn(0f, 1f) } },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            enabled = isEditMode
                        )
                        Slider(
                            value = priority,
                            onValueChange = { priority = it },
                            valueRange = 0f..1f,
                            steps = 999,
                            modifier = Modifier.weight(1f),
                            enabled = isEditMode
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Show toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show on home", modifier = Modifier.weight(1f))
                        Switch(checked = show, onCheckedChange = { show = it }, enabled = isEditMode)
                    }

                    // Dates (read-only)
                    if (initialNote != null) {
                        Text(
                            "Created: ${dateFmt.format(Date(initialNote.createdAt))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Modified: ${dateFmt.format(Date(initialNote.modifiedAt))}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Save button (edit mode)
                if (isEditMode) {
                    Button(
                        onClick = {
                            onSave(name.ifBlank { noteId }, contentValue.text, tags.toList(), priority, show)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Unsaved changes") },
            text = {
                Column {
                    Text("You have unsaved changes.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = discardConfirmed, onCheckedChange = { discardConfirmed = it })
                        Text("I want to discard my changes", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(name.ifBlank { noteId }, contentValue.text, tags.toList(), priority, show)
                        showDiscardDialog = false
                        onBack()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            if (discardConfirmed) {
                                showDiscardDialog = false
                                onBack()
                            }
                        },
                        enabled = discardConfirmed
                    ) {
                        Text("Discard", color = if (discardConfirmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                    TextButton(onClick = { showDiscardDialog = false }) { Text("Continue Editing") }
                }
            }
        )
    }

    // Delete dialog
    if (showDeleteDialog && initialNote != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = {
                Column {
                    Text("Permanently delete \"${initialNote.name}\"?")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteConfirmed, onCheckedChange = { deleteConfirmed = it })
                        Text("I want to delete this note", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(initialNote.id); showDeleteDialog = false },
                    enabled = deleteConfirmed
                ) {
                    Text("Delete", color = if (deleteConfirmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
