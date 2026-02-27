package com.jaek.clawapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.service.ClawService
import com.jaek.clawapp.ui.screen.CatDetailScreen
import com.jaek.clawapp.ui.screen.HomeScreen
import com.jaek.clawapp.ui.screen.SettingsScreen
import com.jaek.clawapp.ui.theme.ClawAppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Screen { HOME, SETTINGS, CAT_DETAIL }

class MainActivity : ComponentActivity() {

    private var clawService: ClawService? = null
    private var bound = false
    private val connectionState = mutableStateOf(false)
    private val serviceRunning = mutableStateOf(false)
    private val catsState = mutableStateOf<Map<String, CatState>>(emptyMap())
    private var connectionCollectJob: Job? = null
    private var catsCollectJob: Job? = null

    private val relayUrl = mutableStateOf("ws://100.126.78.128:18790")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as ClawService.LocalBinder
            val svc = binder.getService()
            clawService = svc

            connectionCollectJob?.cancel()
            connectionCollectJob = lifecycleScope.launch {
                svc.connectionState.collect { connectionState.value = it }
            }

            catsCollectJob?.cancel()
            catsCollectJob = lifecycleScope.launch {
                svc.catRepository.cats.collect { catsState.value = it }
            }

            if (!svc.isConnected()) {
                val url = relayUrl.value
                if (url.isNotBlank()) svc.configure(url)
            }

            bound = true
            serviceRunning.value = true
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            connectionCollectJob?.cancel()
            catsCollectJob?.cancel()
            connectionCollectJob = null
            catsCollectJob = null
            clawService = null
            bound = false
            serviceRunning.value = false
            connectionState.value = false
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("claw_settings", MODE_PRIVATE)
        relayUrl.value = prefs.getString("relay_url", relayUrl.value) ?: relayUrl.value

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ClawAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var selectedCat by remember { mutableStateOf<CatState?>(null) }

                // System back button returns to HOME instead of desktop
                BackHandler(enabled = currentScreen != Screen.HOME) {
                    currentScreen = Screen.HOME
                }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        isConnected = connectionState.value,
                        isServiceRunning = serviceRunning.value,
                        cats = catsState.value,
                        onCatClick = { cat ->
                            selectedCat = cat
                            currentScreen = Screen.CAT_DETAIL
                        },
                        onSettingsClick = { currentScreen = Screen.SETTINGS }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        isServiceRunning = serviceRunning.value,
                        relayUrl = relayUrl.value,
                        onUrlChange = { relayUrl.value = it },
                        onStartService = { startClawService() },
                        onStopService = { stopClawService() },
                        onLocalTestPing = { localTestPing() },
                        onServerTestPing = { serverTestPing() },
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.CAT_DETAIL -> {
                        val cat = selectedCat
                        if (cat != null) {
                            CatDetailScreen(
                                cat = cat,
                                onSave = { newLocation ->
                                    clawService?.catRepository?.setCatState(cat.name, newLocation)
                                    currentScreen = Screen.HOME
                                },
                                onCancel = { currentScreen = Screen.HOME }
                            )
                        } else {
                            currentScreen = Screen.HOME
                        }
                    }
                }
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
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun stopClawService() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        stopService(Intent(this, ClawService::class.java))
        serviceRunning.value = false
        connectionState.value = false
    }

    /** Triggers ping locally via the service (no server round-trip) */
    private fun localTestPing() {
        val intent = Intent(this, ClawService::class.java).apply {
            action = ClawService.ACTION_PING_PHONE
            putExtra(ClawService.EXTRA_MESSAGE, "Local test ping from ClawApp!")
        }
        startService(intent)
    }

    /** Asks server to send a ping command back to this device */
    private fun serverTestPing() {
        clawService?.requestServerTestPing()
    }

    override fun onStart() {
        super.onStart()
        if (!bound) {
            val intent = Intent(this, ClawService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }
}
