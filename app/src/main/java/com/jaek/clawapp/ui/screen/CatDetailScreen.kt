package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var selectedLocation by remember { mutableStateOf(cat.state) }
    val hasChange = selectedLocation != cat.state

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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cat emoji header
            Text(
                text = when (cat.state) {
                    CatLocation.INSIDE -> "🏠"
                    CatLocation.OUTSIDE -> "🌿"
                    CatLocation.UNKNOWN -> "❓"
                },
                fontSize = 56.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Current state label
            Text(
                text = "Where is ${cat.name}?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Location options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    LocationOption(
                        emoji = "🏠",
                        label = "Inside",
                        description = "Cat is indoors",
                        selected = selectedLocation == CatLocation.INSIDE,
                        onClick = { selectedLocation = CatLocation.INSIDE }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    LocationOption(
                        emoji = "🌿",
                        label = "Outside",
                        description = "Cat is outdoors",
                        selected = selectedLocation == CatLocation.OUTSIDE,
                        onClick = { selectedLocation = CatLocation.OUTSIDE }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    LocationOption(
                        emoji = "❓",
                        label = "Unknown",
                        description = "Not sure",
                        selected = selectedLocation == CatLocation.UNKNOWN,
                        onClick = { selectedLocation = CatLocation.UNKNOWN }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(selectedLocation) },
                    modifier = Modifier.weight(1f),
                    enabled = hasChange
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun LocationOption(
    emoji: String,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Text(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}
