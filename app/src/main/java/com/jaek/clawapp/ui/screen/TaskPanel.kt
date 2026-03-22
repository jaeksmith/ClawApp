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
    val running  = activeTasks.count { it.effectiveStatus == "running" }
    val stalled  = activeTasks.count { it.effectiveStatus == "stalled" }
    val complete = recentCompleted.count { it.status == "complete" }
    val failed   = recentCompleted.count { it.status == "failed" }

    val hasAlert = stalled > 0 || failed > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = when {
            hasAlert -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            running > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "TASKS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                // Always show all non-zero counts
                if (running > 0) StatusChip("⚡ $running", Color(0xFF4CAF50))
                if (stalled > 0) StatusChip("⚠️ $stalled", Color(0xFFFFC107))
                if (failed  > 0) StatusChip("❌ $failed",  Color(0xFFF44336))
                if (complete > 0) StatusChip("✅ $complete", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                if (running == 0 && stalled == 0 && failed == 0 && complete == 0) {
                    Text(
                        "no tasks",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Text(
                "tasks ›",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color
    )
}
