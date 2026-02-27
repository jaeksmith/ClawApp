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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isServiceRunning: Boolean,
    relayUrl: String,
    onUrlChange: (String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onLocalTestPing: () -> Unit,
    onServerTestPing: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBack: () -> Unit
) {
    var urlDraft by remember(relayUrl) { mutableStateOf(relayUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            // Connection card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Connection", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("Relay URL (ws://)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (!isServiceRunning) {
                        Button(
                            onClick = {
                                onUrlChange(urlDraft)
                                onStartService()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = urlDraft.isNotBlank()
                        ) {
                            Text("Start Service")
                        }
                    } else {
                        Button(
                            onClick = onStopService,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop Service")
                        }
                    }
                }
            }

            // Notifications card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cat Watch", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    OutlinedButton(onClick = onNotificationsClick, modifier = Modifier.fillMaxWidth()) {
                        Text("🔔 Manage Notifications")
                    }
                }
            }

            // Test ping card (only when service running)
            if (isServiceRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Debug", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                        OutlinedButton(
                            onClick = onLocalTestPing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔔 Test Ping (Local)")
                        }

                        OutlinedButton(
                            onClick = onServerTestPing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📡 Test Ping (Server → Phone)")
                        }
                    }
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
