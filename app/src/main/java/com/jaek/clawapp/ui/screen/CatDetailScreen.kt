package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.ui.ClawViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatDetailScreen(
    catName: String,
    viewModel: ClawViewModel,
    onBack: () -> Unit
) {
    val catsMap by (viewModel.cats ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val cat = catsMap[catName]

    // Editable state
    var selectedLocation by remember(cat) { mutableStateOf(cat?.state ?: CatLocation.UNKNOWN) }
    val hasChanges = cat != null && selectedLocation != cat.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(catName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cat image placeholder
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("🐱", fontSize = 72.sp)
            }

            // Cat name
            Text(catName, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            if (cat?.outdoorOnly == true) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF607D8B).copy(alpha = 0.2f)
                ) {
                    Text(
                        "Outdoor only",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        color = Color(0xFF607D8B)
                    )
                }
            }

            // Location selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Location",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CatLocation.entries.forEach { location ->
                            val isSelected = selectedLocation == location
                            val isDisabled = cat?.outdoorOnly == true
                            LocationChip(
                                location = location,
                                isSelected = isSelected,
                                isDisabled = isDisabled,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (!isDisabled) selectedLocation = location
                                }
                            )
                        }
                    }
                }
            }

            // Last state change time
            cat?.stateSetAt?.let { ts ->
                val timeStr = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(ts))
                Text(
                    "Since $timeStr",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel / Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (hasChanges) {
                            viewModel.setCatState(catName, selectedLocation)
                        }
                        onBack()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = cat?.outdoorOnly != true
                ) {
                    Text(if (hasChanges) "Save" else "Done")
                }
            }
        }
    }
}

@Composable
fun LocationChip(
    location: CatLocation,
    isSelected: Boolean,
    isDisabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) when (location) {
        CatLocation.INSIDE -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        CatLocation.OUTSIDE -> Color(0xFFFF9800).copy(alpha = 0.15f)
        CatLocation.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
    } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val borderColor = if (isSelected) when (location) {
        CatLocation.INSIDE -> Color(0xFF4CAF50)
        CatLocation.OUTSIDE -> Color(0xFFFF9800)
        CatLocation.UNKNOWN -> Color(0xFF9E9E9E)
    } else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = !isDisabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(location.emoji, fontSize = 22.sp)
        Text(
            location.displayName,
            fontSize = 11.sp,
            color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}
