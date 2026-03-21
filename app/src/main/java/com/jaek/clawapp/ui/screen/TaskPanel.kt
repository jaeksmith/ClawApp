package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.ClawTask

@Composable
fun TaskPanel(
    activeTasks: List<ClawTask>,
    recentCompleted: List<ClawTask>,
    onClick: () -> Unit
) {
    val running = activeTasks.count { it.effectiveStatus == "running" }
    val stalled = activeTasks.count { it.effectiveStatus == "stalled" }
    val recentFailed = recentCompleted.count { it.status == "failed" }

    // Only show if there's something worth showing
    if (activeTasks.isEmpty() && recentCompleted.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = when {
            stalled > 0 || recentFailed > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            running > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🗂", fontSize = 14.sp)
                if (running > 0) {
                    Text(
                        "$running running",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (stalled > 0) {
                    Text(
                        "$stalled stalled",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFC107)
                    )
                }
                if (running == 0 && stalled == 0) {
                    val last = recentCompleted.firstOrNull()
                    if (last != null) {
                        val emoji = if (last.status == "failed") "❌" else "✅"
                        Text(
                            "$emoji ${last.label}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                // Show stalled task labels inline
                if (stalled > 0) {
                    activeTasks.filter { it.effectiveStatus == "stalled" }.take(2).forEach { t ->
                        Text(
                            "· ${t.label}",
                            fontSize = 11.sp,
                            color = Color(0xFFFFC107),
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                "tap for details",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
