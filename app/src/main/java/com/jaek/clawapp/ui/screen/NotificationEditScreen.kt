package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatNotification
import com.jaek.clawapp.model.DeliveryOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationEditScreen(
    existing: CatNotification?,   // null = new
    onSave: (CatNotification) -> Unit,
    onCancel: () -> Unit
) {
    val isNew = existing == null

    // Type
    var typeRepeating by remember { mutableStateOf(existing?.type != "absolute") }

    // Repeating fields
    var initialDelay by remember { mutableStateOf(existing?.initialDelayMinutes?.toString() ?: "60") }
    var maxDelay by remember { mutableStateOf(existing?.maxDelayMinutes?.toString() ?: "240") }

    // Absolute fields
    var absoluteTime by remember { mutableStateOf(existing?.absoluteTime ?: "21:00") }

    // Message
    var message by remember { mutableStateOf(existing?.message ?: "{cats} have been outside.") }

    // Delivery options
    val d = existing?.delivery ?: DeliveryOptions()
    var vibration by remember { mutableStateOf(d.vibration) }
    var meow by remember { mutableStateOf(d.meow) }
    var phoneSound by remember { mutableStateOf(d.phoneSound) }
    var tts by remember { mutableStateOf(d.tts) }
    var ttsText by remember { mutableStateOf(d.ttsText) }
    var bypassSilent by remember { mutableStateOf(d.bypassSilent) }

    val canSave = (vibration || meow || phoneSound || tts) &&
            message.isNotBlank() &&
            if (typeRepeating) initialDelay.toIntOrNull() != null && maxDelay.toIntOrNull() != null
            else absoluteTime.matches(Regex("\\d{2}:\\d{2}"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New Notification" else "Edit Notification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val notif = CatNotification(
                            id = existing?.id ?: "notif-${System.currentTimeMillis()}",
                            type = if (typeRepeating) "repeating" else "absolute",
                            initialDelayMinutes = initialDelay.toIntOrNull() ?: 60,
                            maxDelayMinutes = maxDelay.toIntOrNull() ?: 240,
                            absoluteTime = if (!typeRepeating) absoluteTime else null,
                            message = message,
                            delivery = DeliveryOptions(
                                vibration = vibration,
                                meow = meow,
                                phoneSound = phoneSound,
                                tts = tts,
                                ttsText = ttsText,
                                bypassSilent = bypassSilent
                            )
                        )
                        onSave(notif)
                    }, enabled = canSave) {
                        Text("Save")
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Type selector ---
            SectionLabel("Notification Type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = typeRepeating,
                    onClick = { typeRepeating = true },
                    label = { Text("🔁 Repeating") }
                )
                FilterChip(
                    selected = !typeRepeating,
                    onClick = { typeRepeating = false },
                    label = { Text("⏰ Absolute Time") }
                )
            }

            // --- Type-specific fields ---
            if (typeRepeating) {
                SectionLabel("Timing")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = initialDelay,
                        onValueChange = { initialDelay = it.filter { c -> c.isDigit() } },
                        label = { Text("Initial delay (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = { Text("After last cat goes out") }
                    )
                    OutlinedTextField(
                        value = maxDelay,
                        onValueChange = { maxDelay = it.filter { c -> c.isDigit() } },
                        label = { Text("Max delay (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        supportingText = { Text("Cap on doubling") }
                    )
                }
                Text(
                    text = "Fires at ${initialDelay}m, then ${(initialDelay.toIntOrNull() ?: 60) * 2}m, ${(initialDelay.toIntOrNull() ?: 60) * 4}m … up to ${maxDelay}m, then every ${maxDelay}m.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            } else {
                SectionLabel("Time")
                OutlinedTextField(
                    value = absoluteTime,
                    onValueChange = { absoluteTime = it },
                    label = { Text("Time (HH:MM, 24h)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("21:00") }
                )
                Text(
                    text = "Fires daily at this time if ≥1 cat is outside.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            // --- Message ---
            SectionLabel("Message")
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message template") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
                supportingText = { Text("Use {cats} for cat names") }
            )

            // --- Delivery options ---
            SectionLabel("Delivery (pick any combination)")

            DeliveryToggle(
                emoji = "📳", label = "Vibration",
                checked = vibration, onChecked = { vibration = it }
            )
            DeliveryToggle(
                emoji = "😸", label = "Cat meow sound",
                checked = meow, onChecked = { meow = it }
            )
            DeliveryToggle(
                emoji = "🔔", label = "Phone notification sound",
                checked = phoneSound, onChecked = { phoneSound = it }
            )
            DeliveryToggle(
                emoji = "🗣️", label = "Text-to-speech",
                checked = tts, onChecked = { tts = it }
            )
            if (tts) {
                OutlinedTextField(
                    value = ttsText,
                    onValueChange = { ttsText = it },
                    label = { Text("TTS text") },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    singleLine = false,
                    minLines = 2,
                    supportingText = { Text("Use {cats} for cat names") }
                )
            }

            HorizontalDivider()

            // --- Bypass silent mode ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bypass silent/vibrate mode", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        text = if (bypassSilent) "Uses alarm stream — sounds even when silenced"
                               else "Respects phone ringer mode",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Switch(checked = bypassSilent, onCheckedChange = { bypassSilent = it })
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun DeliveryToggle(emoji: String, label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(emoji, fontSize = 20.sp)
            Text(label, fontSize = 15.sp)
        }
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
