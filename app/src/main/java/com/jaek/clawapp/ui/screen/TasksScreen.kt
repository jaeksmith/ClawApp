package com.jaek.clawapp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.ClawTask
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    activeTasks: List<ClawTask>,
    recentCompleted: List<ClawTask>,
    lastFetchMs: Long?,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (lastFetchMs != null) {
                item {
                    val fmt = SimpleDateFormat("h:mm:ss a", Locale.US)
                    Text(
                        "Updated: ${fmt.format(Date(lastFetchMs))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
            }

            item {
                SectionLabel("ACTIVE")
            }

            if (activeTasks.isEmpty()) {
                item {
                    Text(
                        "No active tasks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            } else {
                items(activeTasks, key = { it.id }) { task ->
                    CollapsibleTaskCard(task = task, initiallyExpanded = task.effectiveStatus == "stalled")
                }
            }

            if (recentCompleted.isNotEmpty()) {
                item { SectionLabel("RECENT", modifier = Modifier.padding(top = 8.dp)) }
                items(recentCompleted, key = { it.id }) { task ->
                    CollapsibleTaskCard(task = task, dimmed = true)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun CollapsibleTaskCard(task: ClawTask, dimmed: Boolean = false, initiallyExpanded: Boolean = false) {
    var expanded by remember(task.id) { mutableStateOf(initiallyExpanded) }

    val effectiveStatus = task.effectiveStatus
    val statusColor = when (effectiveStatus) {
        "running"  -> Color(0xFF4CAF50)
        "stalled"  -> Color(0xFFFFC107)
        "complete" -> MaterialTheme.colorScheme.onSurfaceVariant
        "failed"   -> Color(0xFFF44336)
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusEmoji = when (effectiveStatus) {
        "running"  -> "⚡"
        "stalled"  -> "⚠️"
        "complete" -> "✅"
        "failed"   -> "❌"
        else       -> "❓"
    }
    val alpha = if (dimmed) 0.6f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dimmed) 0.35f else 0.7f)
        )
    ) {
        // Header row — always visible, tap to expand
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(statusEmoji, fontSize = 13.sp)
                Text(
                    task.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    effectiveStatus.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor.copy(alpha = alpha),
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Expandable detail body
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(2.dp))

                if (!task.description.isNullOrBlank()) {
                    Text(
                        task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }

                if (!task.notes.isNullOrBlank()) {
                    Text(
                        task.notes,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.8f)
                    )
                }

                val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetaChip("type:${task.type}", alpha)
                    if (task.spawnedAt > 0) MetaChip("spawned:${fmt.format(Date(task.spawnedAt * 1000))}", alpha)
                    if (effectiveStatus == "running" && task.timeoutAt > 0) {
                        val remaining = task.timeoutAt - System.currentTimeMillis() / 1000
                        if (remaining > 0) {
                            Text(
                                "timeout:${remaining / 60}m",
                                fontSize = 10.sp,
                                color = (if (remaining < 300) Color(0xFFFFC107) else
                                    MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, alpha: Float) {
    Text(
        text,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.65f)
    )
}
