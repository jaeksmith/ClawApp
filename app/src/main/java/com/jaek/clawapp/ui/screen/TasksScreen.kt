package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
                title = { Text("🗂 Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lastFetchMs != null) {
                item {
                    val fmt = SimpleDateFormat("h:mm:ss a", Locale.US)
                    Text(
                        "Last updated: ${fmt.format(Date(lastFetchMs))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }

            item {
                Text(
                    "ACTIVE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            if (activeTasks.isEmpty()) {
                item {
                    Text(
                        "No active tasks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                items(activeTasks) { task ->
                    TaskCard(task = task)
                }
            }

            if (recentCompleted.isNotEmpty()) {
                item {
                    Text(
                        "RECENT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }
                items(recentCompleted) { task ->
                    TaskCard(task = task, dimmed = true)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun TaskCard(task: ClawTask, dimmed: Boolean = false) {
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dimmed) 0.4f else 1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$statusEmoji ${task.label}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    effectiveStatus.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor.copy(alpha = alpha),
                    letterSpacing = 0.5.sp
                )
            }

            if (!task.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }

            if (!task.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.notes,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.8f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
                Text(
                    "type:${task.type}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.7f)
                )
                if (task.spawnedAt > 0) {
                    Text(
                        "spawned:${fmt.format(Date(task.spawnedAt * 1000))}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.7f)
                    )
                }
                if (task.effectiveStatus == "running" && task.timeoutAt > 0) {
                    val remaining = task.timeoutAt - System.currentTimeMillis() / 1000
                    if (remaining > 0) {
                        Text(
                            "timeout:${remaining / 60}m",
                            fontSize = 10.sp,
                            color = if (remaining < 300) Color(0xFFFFC107).copy(alpha = alpha) else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.7f)
                        )
                    }
                }
            }
        }
    }
}
