package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    relayUrl: String,
    isServiceRunning: Boolean,
    isConnected: Boolean,
    onUrlChange: (String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onTestPing: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // Connection status
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape).background(
                            when {
                                !isServiceRunning -> Color.Gray
                                isConnected -> Color(0xFF4CAF50)
                                else -> Color(0xFFF44336)
                            }
                        )
                    )
                    Column {
                        Text(
                            text = when {
                                !isServiceRunning -> "Service Stopped"
                                isConnected -> "Connected to Claw"
                                else -> "Connecting..."
                            },
                            fontSize = 18.sp, fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isServiceRunning) "Background service active" else "Tap Start to connect",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Relay URL
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = onUrlChange,
                        label = { Text("Relay URL (ws://)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Service control
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isServiceRunning) {
                    Button(onClick = onStartService, modifier = Modifier.weight(1f), enabled = relayUrl.isNotBlank()) {
                        Text("Start Service")
                    }
                } else {
                    Button(
                        onClick = onStopService,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Service")
                    }
                }
            }

            if (isServiceRunning) {
                OutlinedButton(onClick = onTestPing, modifier = Modifier.fillMaxWidth()) {
                    Text("🔔 Test Phone Ping")
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                "ClawApp v0.1.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
