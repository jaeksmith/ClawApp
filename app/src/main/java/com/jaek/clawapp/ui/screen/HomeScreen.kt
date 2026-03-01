package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatLocation
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.jaek.clawapp.model.CatImages
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.model.MuteState
import com.jaek.clawapp.service.LocationPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    isServiceRunning: Boolean,
    cats: Map<String, CatState>,
    muteState: MuteState = MuteState(),
    currentLocation: LocationPoint? = null,
    locationTracking: Boolean = false,
    namedPlaces: List<String> = emptyList(),
    onCatClick: (CatState) -> Unit,
    onSettingsClick: () -> Unit,
    onQuickControlsClick: () -> Unit = {},
    onConfirmLocation: (confirmedName: String) -> Unit = {}
) {
    var showLocationDialog by remember { mutableStateOf(false) }
    var locationCorrectionText by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🦀 ClawApp", fontWeight = FontWeight.Bold)
                        // Connection dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isServiceRunning -> Color.Gray
                                        isConnected -> Color(0xFF4CAF50)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                        )
                        // Location badge — tappable, shows inferred name + confidence
                        if (locationTracking) {
                            val inferredName = currentLocation?.inferredName
                            val confidence = currentLocation?.locationConfidence
                            val locLabel = when {
                                inferredName != null && confidence != null -> "📍 $inferredName ($confidence%)"
                                inferredName != null -> "📍 $inferredName"
                                currentLocation != null -> "📍 ±${currentLocation.accuracy?.toInt() ?: "?"}m"
                                else -> "📍 …"
                            }
                            val badgeColor = when {
                                currentLocation == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                inferredName != null && (confidence ?: 0) >= 60 -> Color(0xFF4CAF50)
                                inferredName != null -> Color(0xFFFFC107)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            }
                            Text(
                                text = locLabel,
                                fontSize = 11.sp,
                                color = badgeColor,
                                modifier = Modifier.clickable(enabled = currentLocation != null) {
                                    locationCorrectionText = inferredName ?: ""
                                    showLocationDialog = true
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onQuickControlsClick) {
                        Text(if (muteState.isMuted) "🔕" else "🔔", fontSize = 18.sp)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
        ) {
            // Mute banner (shown when muted)
            if (muteState.isMuted && muteState.until != null) {
                val fmt = SimpleDateFormat("h:mm a", Locale.US)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onQuickControlsClick() },
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "🔕 Notifications muted until ${fmt.format(Date(muteState.until))}  ·  tap to change",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Cat bar — single horizontal row of equal-width squares
            if (cats.isNotEmpty()) {
                val sorted = cats.values
                    .sortedWith(compareBy({ it.outdoorOnly }, { it.name }))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sorted.forEach { cat ->
                        CatTile(
                            cat = cat,
                            onClick = { onCatClick(cat) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnected) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = when {
                                !isServiceRunning -> "Go to Settings to start"
                                else -> "Waiting for connection..."
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // Location confirm/correct dialog
    if (showLocationDialog) {
        val inferredName = currentLocation?.inferredName
        val confidence = currentLocation?.locationConfidence
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("📍 Your Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (inferredName != null) {
                        Text(
                            text = "Detected: $inferredName" + if (confidence != null) " ($confidence%)" else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Location not recognized." +
                                (currentLocation?.accuracy?.let { " GPS ±${it.toInt()}m." } ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Known places as tappable chips
                    if (namedPlaces.isNotEmpty()) {
                        Text("Select a known place:", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            namedPlaces.forEach { place ->
                                val isSelected = locationCorrectionText == place
                                androidx.compose.material3.FilterChip(
                                    selected = isSelected,
                                    onClick = { locationCorrectionText = place },
                                    label = { Text(place, fontSize = 13.sp) }
                                )
                            }
                        }
                    }
                    Text("Or type a new name:", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    OutlinedTextField(
                        value = locationCorrectionText,
                        onValueChange = { locationCorrectionText = it },
                        label = { Text("Location name") },
                        placeholder = { Text("e.g. home, bro's place") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = locationCorrectionText.trim()
                        if (name.isNotEmpty()) onConfirmLocation(name)
                        showLocationDialog = false
                    },
                    enabled = locationCorrectionText.isNotBlank()
                ) { Text(if (locationCorrectionText.trim() == inferredName) "✓ Confirm" else "Save Location") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CatTile(
    cat: CatState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (cat.state) {
        CatLocation.INSIDE -> MaterialTheme.colorScheme.primaryContainer
        CatLocation.OUTSIDE -> Color(0xFFE8F5E9)
        CatLocation.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    }
    val emoji = when (cat.state) {
        CatLocation.INSIDE -> "🏠"
        CatLocation.OUTSIDE -> "🌿"
        CatLocation.UNKNOWN -> "❓"
    }

    val imageRes = CatImages.getDrawableRes(cat.name)

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = !cat.outdoorOnly, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cat photo (fills tile, slightly dimmed by state overlay)
            if (imageRes != null) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = cat.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (cat.state == CatLocation.INSIDE) 0.75f else 1f
                )
            }
            // State emoji overlay (bottom-left) + name (bottom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = emoji, fontSize = if (imageRes != null) 12.sp else 20.sp, textAlign = TextAlign.Center)
                Text(
                    text = cat.name,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
