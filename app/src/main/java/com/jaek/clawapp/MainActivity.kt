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
import com.jaek.clawapp.model.CatNotification
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.model.MuteState
import com.jaek.clawapp.service.LocationPoint
import com.jaek.clawapp.service.ClawService
import com.jaek.clawapp.AppLogger
import com.jaek.clawapp.ui.screen.*
import com.jaek.clawapp.ui.theme.ClawAppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Screen {
    HOME, SETTINGS, CAT_DETAIL,
    NOTIFICATIONS, NOTIFICATION_EDIT,
    QUICK_CONTROLS, LOG
}

class MainActivity : ComponentActivity() {

    private var clawService: ClawService? = null
    private var bound = false
    private val connectionState = mutableStateOf(false)
    private val serviceRunning = mutableStateOf(false)
    private val catsState = mutableStateOf<Map<String, CatState>>(emptyMap())
    private val notificationsState = mutableStateOf<List<CatNotification>>(emptyList())
    private val muteState = mutableStateOf(MuteState())
    private val locationState = mutableStateOf<LocationPoint?>(null)
    private val locationTracking = mutableStateOf(true)
    private var connectionCollectJob: Job? = null
    private var locationCollectJob: Job? = null
    private var catsCollectJob: Job? = null
    private var notifsCollectJob: Job? = null
    private var muteCollectJob: Job? = null

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
            notifsCollectJob?.cancel()
            notifsCollectJob = lifecycleScope.launch {
                svc.catRepository.notifications.collect { notificationsState.value = it }
            }
            muteCollectJob?.cancel()
            muteCollectJob = lifecycleScope.launch {
                svc.catRepository.mute.collect { muteState.value = it }
            }
            locationCollectJob?.cancel()
            locationCollectJob = lifecycleScope.launch {
                svc.locationTracker.currentLocation.collect { locationState.value = it }
            }
            // Read initial tracking pref
            val prefs = getSharedPreferences("claw_settings", MODE_PRIVATE)
            locationTracking.value = prefs.getBoolean("location_tracking_enabled", true)

            if (!svc.isConnected()) {
                val url = relayUrl.value
                if (url.isNotBlank()) svc.configure(url)
            }
            bound = true
            serviceRunning.value = true
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            listOf(connectionCollectJob, catsCollectJob, notifsCollectJob, muteCollectJob, locationCollectJob).forEach { it?.cancel() }
            connectionCollectJob = null; catsCollectJob = null; notifsCollectJob = null; muteCollectJob = null; locationCollectJob = null
            clawService = null; bound = false
            serviceRunning.value = false; connectionState.value = false
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
        // Location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            ), 42)
        }

        setContent {
            ClawAppTheme {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var selectedCat by remember { mutableStateOf<CatState?>(null) }
                var editingNotif by remember { mutableStateOf<CatNotification?>(null) }
                var isNewNotif by remember { mutableStateOf(false) }

                // Context-aware back navigation
                BackHandler(enabled = currentScreen != Screen.HOME) {
                    currentScreen = when (currentScreen) {
                        Screen.NOTIFICATION_EDIT -> Screen.NOTIFICATIONS
                        Screen.NOTIFICATIONS    -> Screen.SETTINGS
                        Screen.LOG              -> Screen.SETTINGS
                        else                    -> Screen.HOME
                    }
                }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        isConnected = connectionState.value,
                        isServiceRunning = serviceRunning.value,
                        cats = catsState.value,
                        muteState = muteState.value,
                        currentLocation = locationState.value,
                        locationTracking = locationTracking.value,
                        onCatClick = { cat -> selectedCat = cat; currentScreen = Screen.CAT_DETAIL },
                        onSettingsClick = { currentScreen = Screen.SETTINGS },
                        onQuickControlsClick = { currentScreen = Screen.QUICK_CONTROLS }
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        isServiceRunning = serviceRunning.value,
                        relayUrl = relayUrl.value,
                        onUrlChange = { relayUrl.value = it },
                        onStartService = { startClawService() },
                        onStopService = { stopClawService() },
                        onLocalTestPing = { localTestPing() },
                        onServerTestPing = { serverTestPing() },
                        onNotificationsClick = { currentScreen = Screen.NOTIFICATIONS },
                        onLogClick = { currentScreen = Screen.LOG },
                        onBack = { currentScreen = Screen.HOME }
                    )

                    Screen.CAT_DETAIL -> {
                        val cat = selectedCat
                        if (cat != null) {
                            CatDetailScreen(
                                cat = cat,
                                onSave = { newLoc ->
                                    clawService?.catRepository?.setCatState(cat.name, newLoc)
                                    currentScreen = Screen.HOME
                                },
                                onCancel = { currentScreen = Screen.HOME }
                            )
                        } else currentScreen = Screen.HOME
                    }

                    Screen.NOTIFICATIONS -> NotificationsScreen(
                        notifications = notificationsState.value,
                        onAdd = {
                            editingNotif = null; isNewNotif = true
                            currentScreen = Screen.NOTIFICATION_EDIT
                        },
                        onEdit = { notif ->
                            editingNotif = notif; isNewNotif = false
                            currentScreen = Screen.NOTIFICATION_EDIT
                        },
                        onDelete = { id -> clawService?.catRepository?.removeNotification(id) },
                        onBack = { currentScreen = Screen.SETTINGS }
                    )

                    Screen.NOTIFICATION_EDIT -> NotificationEditScreen(
                        existing = if (isNewNotif) null else editingNotif,
                        onSave = { notif ->
                            val repo = clawService?.catRepository
                            if (isNewNotif) repo?.addNotification(notif)
                            else repo?.updateNotification(notif)
                            currentScreen = Screen.NOTIFICATIONS
                        },
                        onCancel = { currentScreen = Screen.NOTIFICATIONS }
                    )

                    Screen.LOG -> LogScreen(onBack = { currentScreen = Screen.SETTINGS })

                    Screen.QUICK_CONTROLS -> QuickControlsScreen(
                        muteState = muteState.value,
                        locationTracking = locationTracking.value,
                        onSetMute = { until -> clawService?.catRepository?.setMute(until) },
                        onSetLocationTracking = { enabled ->
                            locationTracking.value = enabled
                            clawService?.setLocationTracking(enabled)
                        },
                        onBack = { currentScreen = Screen.HOME }
                    )
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
        if (bound) { unbindService(serviceConnection); bound = false }
        stopService(Intent(this, ClawService::class.java))
        serviceRunning.value = false; connectionState.value = false
    }

    private fun localTestPing() {
        startService(Intent(this, ClawService::class.java).apply {
            action = ClawService.ACTION_PING_PHONE
            putExtra(ClawService.EXTRA_MESSAGE, "Local test ping from ClawApp!")
        })
    }

    private fun serverTestPing() { clawService?.requestServerTestPing() }

    override fun onStart() {
        super.onStart()
        if (!bound) bindService(Intent(this, ClawService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (bound) { unbindService(serviceConnection); bound = false }
        super.onDestroy()
    }
}
