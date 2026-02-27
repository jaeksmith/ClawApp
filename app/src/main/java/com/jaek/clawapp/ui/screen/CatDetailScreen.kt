package com.jaek.clawapp.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.model.CatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatDetailScreen(
    cat: CatState,
    onSave: (CatLocation) -> Unit,
    onCancel: () -> Unit
) {
    var pickerVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(cat.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Tappable location icon — toggles picker
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { pickerVisible = !pickerVisible }
            ) {
                Text(
                    text = when (cat.state) {
                        CatLocation.INSIDE -> "🏠"
                        CatLocation.OUTSIDE -> "🌿"
                        CatLocation.UNKNOWN -> "❓"
                    },
                    fontSize = 72.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = cat.state.displayName,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = if (pickerVisible) "tap to close" else "tap to change",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }

            // Animated picker
            AnimatedVisibility(
                visible = pickerVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Where is ${cat.name}?",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        CatLocation.entries.forEach { loc ->
                            LocationRow(
                                emoji = loc.emoji,
                                label = loc.displayName,
                                selected = cat.state == loc,
                                onClick = {
                                    pickerVisible = false
                                    if (cat.state != loc) onSave(loc)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
