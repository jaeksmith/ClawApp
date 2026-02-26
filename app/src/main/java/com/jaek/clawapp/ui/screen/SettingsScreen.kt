package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onTestPing: () -> Unit,
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isServiceRunning) {
                            Button(
                                onClick = {
                                    onUrlChange(urlDraft)
                                    onStartService()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = urlDraft.isNotBlank()
                            ) {
                                Text("Start Service")
                            }
                        } else {
                            Button(
                                onClick = onStopService,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Stop Service")
                            }
                        }
                    }
                }
            }

            // Test ping card
            if (isServiceRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Debug", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        OutlinedButton(
                            onClick = onTestPing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🔔 Test Phone Ping")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "ClawApp v0.1.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            )
        }
    }
}
