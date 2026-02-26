package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.ui.ClawViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ClawViewModel,
    isConnected: Boolean,
    isServiceRunning: Boolean,
    onCatClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val catsMap by (viewModel.cats ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val cats = catsMap.values.toList().sortedBy { it.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦀 ClawApp", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Connection status strip
            ConnectionStatusBar(isConnected, isServiceRunning)

            // Cat status bar
            if (cats.isNotEmpty()) {
                CatStatusBar(cats = cats, onCatClick = onCatClick)
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "No cats configured",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Placeholder sections for future widgets
            StatusSection(title = "📅 Upcoming", content = "Nothing scheduled")
            StatusSection(title = "📋 Todos", content = "Nothing pending")
        }
    }
}

@Composable
fun ConnectionStatusBar(isConnected: Boolean, isServiceRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
        Text(
            text = when {
                !isServiceRunning -> "Service not running"
                isConnected -> "Connected"
                else -> "Connecting..."
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun CatStatusBar(cats: List<CatState>, onCatClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Cats",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cats, key = { it.name }) { cat ->
                    CatButton(cat = cat, onClick = { onCatClick(cat.name) })
                }
            }
        }
    }
}

@Composable
fun CatButton(cat: CatState, onClick: () -> Unit) {
    val locationColor = when (cat.state) {
        CatLocation.INSIDE -> Color(0xFF4CAF50)
        CatLocation.OUTSIDE -> Color(0xFFFF9800)
        CatLocation.UNKNOWN -> Color(0xFF9E9E9E)
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, locationColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cat emoji placeholder (will be replaced with image when available)
            Text("🐱", fontSize = 28.sp)
            Text(
                text = cat.name.take(6),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Location indicator — bottom right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cat.state.emoji,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }

        // Outdoor-only indicator — top left
        if (cat.outdoorOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF607D8B).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text("∞", fontSize = 8.sp, color = Color.White)
            }
        }
    }
}

@Composable
fun StatusSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(content, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
