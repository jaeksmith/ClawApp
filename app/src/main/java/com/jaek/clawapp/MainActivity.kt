package com.jaek.clawapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.service.ClawService
import com.jaek.clawapp.ui.theme.ClawAppTheme

class MainActivity : ComponentActivity() {

    private var clawService: ClawService? = null
    private var bound = false
    private val connectionState = mutableStateOf(false)
    private val serviceRunning = mutableStateOf(false)

    // TODO: Move to settings/preferences
    private val relayUrl = mutableStateOf("ws://100.126.78.128:18790")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ClawService.LocalBinder
            clawService = binder.getService().also {
                it.onConnectionStateChanged = { connected ->
                    connectionState.value = connected
                }
                // Only configure (and reset WebSocket) if not already connected
                if (!it.isConnected()) {
                    val url = relayUrl.value
                    if (url.isNotBlank()) {
                        it.configure(url)
                    }
                } else {
                    // Sync current connection state to UI
                    connectionState.value = it.isConnected()
                }
            }
            bound = true
            serviceRunning.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clawService = null
            bound = false
            serviceRunning.value = false
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, service still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load saved settings
        val prefs = getSharedPreferences("claw_settings", MODE_PRIVATE)
        relayUrl.value = prefs.getString("relay_url", relayUrl.value) ?: relayUrl.value

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ClawAppTheme {
                ClawAppScreen(
                    isConnected = connectionState.value,
                    isServiceRunning = serviceRunning.value,
                    relayUrl = relayUrl.value,
                    onUrlChange = { relayUrl.value = it },
                    onStartService = { startClawService() },
                    onStopService = { stopClawService() },
                    onTestPing = { testPing() }
                )
            }
        }
    }

    private fun saveSettings() {
        getSharedPreferences("claw_settings", MODE_PRIVATE).edit()
            .putString("relay_url", relayUrl.value)
            .apply()
    }

    private fun startClawService() {
        saveSettings()
        val intent = Intent(this, ClawService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun stopClawService() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        stopService(Intent(this, ClawService::class.java))
        serviceRunning.value = false
        connectionState.value = false
    }

    private fun testPing() {
        clawService?.let {
            val intent = Intent(this, ClawService::class.java).apply {
                action = ClawService.ACTION_PING_PHONE
                putExtra(ClawService.EXTRA_MESSAGE, "Test ping from ClawApp!")
            }
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-bind to service if it's already running (e.g. after app restart/swipe)
        val intent = Intent(this, ClawService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // Unbind but don't stop the service — it keeps running in background
        if (bound) {
            unbindService(connection)
            bound = false
            serviceRunning.value = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClawAppScreen(
    isConnected: Boolean,
    isServiceRunning: Boolean,
    relayUrl: String,
    onUrlChange: (String) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onTestPing: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦀 ClawApp", fontWeight = FontWeight.Bold) },
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
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isServiceRunning) "Background service active" else "Tap Start to connect",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Connection Settings", fontWeight = FontWeight.Medium, fontSize = 16.sp)

                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = onUrlChange,
                        label = { Text("Relay URL (ws://)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isServiceRunning) {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier.weight(1f),
                        enabled = relayUrl.isNotBlank()
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

            // Test Ping Button
            if (isServiceRunning) {
                OutlinedButton(
                    onClick = onTestPing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔔 Test Phone Ping")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version info
            Text(
                text = "ClawApp v0.1.0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
