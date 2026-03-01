package com.jaek.clawapp.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.jaek.clawapp.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationPoint(
    val lat: Double?,
    val lng: Double?,
    val accuracy: Float?,
    val altitude: Double?,
    val wifiScan: List<String>?,   // BSSIDs of visible networks
    val motion: String?,           // "stationary" | "walking" | "unknown"
    val inferredName: String?,     // filled in by server response
    val locationConfidence: Int?,  // 0–100, how confident the server is in inferredName
    val timestamp: Long
)

class LocationTracker(private val context: Context) : SensorEventListener {

    companion object {
        const val TAG = "LocationTracker"
        const val UPDATE_INTERVAL_MS = 30_000L      // request GPS every 30s
        const val FASTEST_INTERVAL_MS = 10_000L
    }

    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    private val _namedPlaces = MutableStateFlow<List<String>>(emptyList())
    val namedPlaces: StateFlow<List<String>> = _namedPlaces.asStateFlow()

    fun onNamedPlaces(places: List<String>) {
        _namedPlaces.value = places
    }

    // Callback to send WS messages
    var sendWsMessage: ((String) -> Unit)? = null

    private var motionState = "unknown"
    private var stepDetectorAvailable = false
    private var lastStepTime = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewLocation(loc)
        }
    }

    fun start() {
        if (_tracking.value) return
        AppLogger.i(TAG, "Starting location tracking")

        // GPS
        if (hasLocationPermission()) {
            val request = LocationRequest.Builder(UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
            try {
                fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                AppLogger.e(TAG, "Location permission denied", e)
            }
        }

        // Step detector for motion state
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            stepDetectorAvailable = true
        }

        _tracking.value = true
    }

    fun stop() {
        if (!_tracking.value) return
        AppLogger.i(TAG, "Stopping location tracking")
        fusedClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
        _tracking.value = false
    }

    fun saveCurrentAsNamedLocation(name: String) {
        val cur = _currentLocation.value ?: run {
            AppLogger.w(TAG, "No location to save as '$name'")
            return
        }
        val msg = gson.toJson(mapOf(
            "type" to "save_named_location",
            "location" to mapOf(
                "name" to name,
                "lat" to cur.lat,
                "lng" to cur.lng,
                "radiusM" to ((cur.accuracy?.toDouble() ?: 10.0).coerceAtLeast(5.0)),
                "wifiFingerprint" to (cur.wifiScan ?: emptyList<String>())
            )
        ))
        sendWsMessage?.invoke(msg)
        AppLogger.i(TAG, "Saved named location '$name' at ${cur.lat},${cur.lng}")
    }

    /** Called when the relay has matched our location to a named place. */
    fun onInferredLocation(name: String?, confidence: Int?) {
        val cur = _currentLocation.value ?: return
        _currentLocation.value = cur.copy(inferredName = name, locationConfidence = confidence)
        AppLogger.i(TAG, "Inferred location: name=$name confidence=$confidence%")
    }

    fun setTrackingEnabled(enabled: Boolean) {
        if (enabled) start() else stop()
        // Tell server
        sendWsMessage?.invoke(gson.toJson(mapOf("type" to "set_location_tracking", "enabled" to enabled)))
    }

    private fun onNewLocation(loc: android.location.Location) {
        // WiFi scan (BSSIDs + RSSI signal strength in dBm)
        data class WifiAp(val bssid: String, val rssi: Int)
        val wifiAps: List<WifiAp> = try {
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.scanResults.map { WifiAp(it.BSSID, it.level) }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
        val wifiBssids = wifiAps.map { it.bssid }  // keep for backward compat

        // Update motion state (walking if step detected recently)
        val motion = when {
            stepDetectorAvailable && (System.currentTimeMillis() - lastStepTime) < 5000 -> "walking"
            stepDetectorAvailable -> "stationary"
            else -> "unknown"
        }

        val point = LocationPoint(
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = loc.accuracy,
            altitude = if (loc.hasAltitude()) loc.altitude else null,
            wifiScan = wifiBssids.takeIf { it.isNotEmpty() },
            motion = motion,
            inferredName = null,
            locationConfidence = null,
            timestamp = System.currentTimeMillis()
        )

        _currentLocation.value = point

        // Send to relay — include RSSI map for smarter location inference
        val wifiRssi = wifiAps.associate { it.bssid to it.rssi }.takeIf { it.isNotEmpty() }
        val msg = gson.toJson(mapOf(
            "type" to "location_update",
            "lat" to point.lat,
            "lng" to point.lng,
            "accuracy" to point.accuracy,
            "altitude" to point.altitude,
            "wifiScan" to point.wifiScan,
            "wifiRssi" to wifiRssi,   // { bssid -> dBm } e.g. {"aa:bb:cc:dd:ee:ff": -62}
            "motion" to point.motion,
            "timestamp" to point.timestamp
        ))
        sendWsMessage?.invoke(msg)
        AppLogger.i(TAG, "Location: ${loc.latitude.toFloat()},${loc.longitude.toFloat()} acc=${loc.accuracy}m wifi=${wifiAps.size} nets motion=$motion")
    }

    // SensorEventListener — step detection
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            lastStepTime = System.currentTimeMillis()
            motionState = "walking"
        }
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}
