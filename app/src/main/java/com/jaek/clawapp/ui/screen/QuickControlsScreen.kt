package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.MuteState
import java.text.SimpleDateFormat
import java.util.*

private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickControlsScreen(
    muteState: MuteState,
    onSetMute: (Long?) -> Unit,   // null = unmute
    onBack: () -> Unit
) {
    // Three mutually exclusive modes
    // 0 = Unmuted, 1 = Quick adjust, 2 = Specific time
    val initialMode = if (muteState.isMuted) 1 else 0
    var mode by remember(muteState) { mutableIntStateOf(initialMode) }

    // Working mute-until timestamp (shown in quick-adjust mode)
    var muteUntil by remember(muteState) {
        mutableLongStateOf(
            if (muteState.isMuted) muteState.until ?: System.currentTimeMillis() + 3600_000L
            else System.currentTimeMillis() + 3600_000L
        )
    }

    // Specific time entry
    var specificTimeText by remember { mutableStateOf(
        timeFmt.format(Date(muteState.until ?: System.currentTimeMillis() + 3600_000L))
    ) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Controls", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (muteState.isMuted)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(if (muteState.isMuted) "🔕" else "🔔", fontSize = 28.sp)
                    Column {
                        Text(
                            text = if (muteState.isMuted) "Notifications muted" else "Notifications active",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (muteState.isMuted && muteState.until != null) {
                            Text(
                                text = "Until ${timeFmt.format(Date(muteState.until))}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Text("Mute control", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary)

            // Mode selector — mutually exclusive
            ModeOption(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = "🔔 Unmuted",
                description = "Notifications enabled"
            )
            ModeOption(
                selected = mode == 1,
                onClick = {
                    mode = 1
                    if (!muteState.isMuted) muteUntil = System.currentTimeMillis() + 3600_000L
                },
                label = "🔕 Mute with time adjustment",
                description = if (mode == 1) "Until ${timeFmt.format(Date(muteUntil))}" else "Adjust in increments"
            )

            // Quick adjust buttons (shown when mode == 1)
            if (mode == 1) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Until: ${timeFmt.format(Date(muteUntil))}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        // Hour row
                        AdjustRow(
                            label = "Hours",
                            onMinus = { muteUntil = maxOf(muteUntil - 3600_000L, System.currentTimeMillis() + 60_000L) },
                            onPlus  = { muteUntil += 3600_000L },
                            minusLabel = "−1h", plusLabel = "+1h"
                        )
                        // 10-min row
                        AdjustRow(
                            label = "10 min",
                            onMinus = { muteUntil = maxOf(muteUntil - 600_000L, System.currentTimeMillis() + 60_000L) },
                            onPlus  = { muteUntil += 600_000L },
                            minusLabel = "−10m", plusLabel = "+10m"
                        )
                        // 1-min row
                        AdjustRow(
                            label = "1 min",
                            onMinus = { muteUntil = maxOf(muteUntil - 60_000L, System.currentTimeMillis() + 60_000L) },
                            onPlus  = { muteUntil += 60_000L },
                            minusLabel = "−1m", plusLabel = "+1m"
                        )
                    }
                }
            }

            ModeOption(
                selected = mode == 2,
                onClick = { mode = 2 },
                label = "⏰ Mute until specific time",
                description = "Enter exact time"
            )

            // Specific time entry (shown when mode == 2)
            if (mode == 2) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = specificTimeText,
                            onValueChange = { specificTimeText = it },
                            label = { Text("Mute until (e.g. 10:30 PM)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("Formats: 10:30 PM, 22:30") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Apply button
            Button(
                onClick = {
                    when (mode) {
                        0 -> onSetMute(null)
                        1 -> onSetMute(muteUntil)
                        2 -> {
                            val parsed = parseTime(specificTimeText)
                            if (parsed != null) onSetMute(parsed)
                        }
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(when (mode) {
                    0 -> "Unmute"
                    1 -> "Mute until ${timeFmt.format(Date(muteUntil))}"
                    else -> "Apply"
                })
            }
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, onClick: () -> Unit, label: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AdjustRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.weight(1f)) { Text(minusLabel) }
        Text(label, fontSize = 12.sp, modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        OutlinedButton(onClick = onPlus, modifier = Modifier.weight(1f)) { Text(plusLabel) }
    }
}

/** Parse "10:30 PM" or "22:30" into today's timestamp, rolling to tomorrow if time has passed. */
private fun parseTime(input: String): Long? {
    val formats = listOf("h:mm a", "hh:mm a", "H:mm", "HH:mm")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US)
            val parsed = sdf.parse(input.trim()) ?: continue
            val cal = Calendar.getInstance()
            val timeCal = Calendar.getInstance().apply { time = parsed }
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        } catch (_: Exception) {}
    }
    return null
}
