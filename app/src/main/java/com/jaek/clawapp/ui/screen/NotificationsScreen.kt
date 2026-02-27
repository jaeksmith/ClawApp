package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatNotification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<CatNotification>,
    onAdd: () -> Unit,
    onEdit: (CatNotification) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cat Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Add notification")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No notifications configured", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Button(onClick = onAdd) { Text("Add one") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    NotificationCard(notif = notif, onEdit = { onEdit(notif) }, onDelete = { onDelete(notif.id) })
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notif: CatNotification,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (notif.type == "repeating") "🔁 Repeating" else "⏰ Absolute",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = when (notif.type) {
                        "repeating" -> "After ${notif.initialDelayMinutes}m, doubles to ${notif.maxDelayMinutes}m max"
                        else -> "Daily at ${notif.absoluteTime ?: "??:??"}"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                // Delivery summary (guard against Gson null-injection on non-nullable fields)
                val delivery = notif.delivery
                val deliveryIcons = buildList {
                    if (delivery?.vibration == true) add("📳")
                    if (delivery?.meow == true) add("😸")
                    if (delivery?.phoneSound == true) add("🔔")
                    if (delivery?.tts == true) add("🗣️")
                }
                if (deliveryIcons.isNotEmpty()) {
                    Text(
                        text = deliveryIcons.joinToString(" "),
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = "\"${notif.message}\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
