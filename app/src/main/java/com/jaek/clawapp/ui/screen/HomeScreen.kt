package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.model.MuteState
import com.jaek.clawapp.service.LocationPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    isServiceRunning: Boolean,
    cats: Map<String, CatState>,
    muteState: MuteState = MuteState(),
    currentLocation: LocationPoint? = null,
    onCatClick: (CatState) -> Unit,
    onSettingsClick: () -> Unit,
    onQuickControlsClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🦀 ClawApp", fontWeight = FontWeight.Bold)
                        // Location badge
                        if (currentLocation != null) {
                            val locLabel = currentLocation.inferredName
                                ?: "📍 ${currentLocation.accuracy?.let { "±${it.toInt()}m" } ?: ""}"
                            Text(
                                text = "📍 $locLabel",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
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

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = !cat.outdoorOnly, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = cat.name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
