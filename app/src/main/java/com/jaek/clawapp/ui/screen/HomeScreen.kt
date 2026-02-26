package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.model.CatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isConnected: Boolean,
    isServiceRunning: Boolean,
    cats: Map<String, CatState>,
    onCatClick: (CatState) -> Unit,
    onSettingsClick: () -> Unit
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
                    }
                },
                actions = {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status subtitle
            Text(
                text = when {
                    !isServiceRunning -> "Service stopped — go to Settings to start"
                    isConnected -> "Connected to Claw"
                    else -> "Reconnecting..."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Cat section header
            if (cats.isNotEmpty()) {
                Text(
                    text = "Cat Watch",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                // Sort: regular cats first (alphabetical), outdoorOnly (Cay) last
                val sorted = cats.values
                    .sortedWith(compareBy({ it.outdoorOnly }, { it.name }))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sorted, key = { it.name }) { cat ->
                        CatCard(cat = cat, onClick = { onCatClick(cat) })
                    }
                }
            } else {
                // Waiting for state snapshot
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
                            "Waiting for connection...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CatCard(cat: CatState, onClick: () -> Unit) {
    val (bgColor, locationLabel, locationEmoji) = when (cat.state) {
        CatLocation.INSIDE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "Inside",
            "🏠"
        )
        CatLocation.OUTSIDE -> Triple(
            Color(0xFFE8F5E9),
            "Outside",
            "🌿"
        )
        CatLocation.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            "Unknown",
            "❓"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !cat.outdoorOnly, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Location emoji large
            Text(text = locationEmoji, fontSize = 28.sp)

            // Cat name
            Text(
                text = cat.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Location label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = locationLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (cat.outdoorOnly) {
                    Text(
                        text = "• always out",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
