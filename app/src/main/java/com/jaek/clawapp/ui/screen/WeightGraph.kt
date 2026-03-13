package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.WeightEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class WeightRange(val label: String, val days: Long) {
    WEEK("week", 7),
    MONTH("month", 30),
    YEAR("year", 365)
}

@Composable
fun WeightGraph(
    entries: List<WeightEntry>,
    onSaveEntry: (date: String, weight: Float, notes: String) -> Unit
) {
    var range by remember { mutableStateOf(WeightRange.YEAR) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var dialogWeight by remember { mutableStateOf("") }
    var dialogNotes by remember { mutableStateOf("") }

    val today = LocalDate.now()
    val cutoff = today.minusDays(range.days)
    val filtered = remember(entries, range) {
        entries.filter {
            try { LocalDate.parse(it.date) >= cutoff } catch (_: Exception) { false }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚖️ Weight",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WeightRange.entries.forEach { r ->
                        FilterChip(
                            selected = range == r,
                            onClick = { range = r },
                            label = { Text(r.label, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data for this period", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val lineColor = colorScheme.primary
                val dotColor = colorScheme.primary
                val todayDotColor = Color(0xFF4CAF50)
                val mutedColor = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                val labelColor = colorScheme.onSurfaceVariant

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .pointerInput(filtered) {
                            detectTapGestures(
                                onTap = {
                                    // Cycle range on tap
                                    range = when (range) {
                                        WeightRange.WEEK -> WeightRange.MONTH
                                        WeightRange.MONTH -> WeightRange.YEAR
                                        WeightRange.YEAR -> WeightRange.WEEK
                                    }
                                },
                                onLongPress = { showEntryDialog = true }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val padLeft = 44f
                    val padRight = 8f
                    val padTop = 8f
                    val padBottom = 20f
                    val graphW = w - padLeft - padRight
                    val graphH = h - padTop - padBottom

                    val weights = filtered.map { it.weight }
                    val minW = (weights.min() - 2f).let { kotlin.math.floor(it.toDouble()).toFloat() }
                    val maxW = (weights.max() + 2f).let { kotlin.math.ceil(it.toDouble()).toFloat() }
                    val rangeW = maxW - minW

                    fun xOf(idx: Int): Float =
                        padLeft + (idx.toFloat() / (filtered.size - 1).coerceAtLeast(1)) * graphW

                    fun yOf(weight: Float): Float =
                        padTop + graphH - ((weight - minW) / rangeW) * graphH

                    // Horizontal guide lines + Y labels
                    val steps = 4
                    for (i in 0..steps) {
                        val labelVal = minW + (rangeW * i / steps)
                        val y = yOf(labelVal)
                        drawLine(mutedColor, Offset(padLeft, y), Offset(w - padRight, y), strokeWidth = 0.5f)
                        val text = labelVal.toInt().toString()
                        val measured = textMeasurer.measure(text, TextStyle(fontSize = 9.sp, color = labelColor))
                        drawText(measured, topLeft = Offset(0f, y - measured.size.height / 2f))
                    }

                    // Line path
                    if (filtered.size > 1) {
                        val path = Path()
                        filtered.forEachIndexed { i, entry ->
                            val x = xOf(i)
                            val y = yOf(entry.weight)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Dots
                    filtered.forEachIndexed { i, entry ->
                        val x = xOf(i)
                        val y = yOf(entry.weight)
                        val isToday = entry.date == today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val dotC = if (isToday) todayDotColor else dotColor
                        drawCircle(Color.Black, radius = 5f, center = Offset(x, y))
                        drawCircle(dotC, radius = 4f, center = Offset(x, y))
                    }

                    // X-axis: show first, middle, last date labels
                    if (filtered.size >= 2) {
                        val indices = when {
                            filtered.size <= 3 -> filtered.indices.toList()
                            else -> listOf(0, filtered.size / 2, filtered.size - 1)
                        }
                        indices.forEach { i ->
                            val dateStr = try {
                                val d = LocalDate.parse(filtered[i].date)
                                if (range == WeightRange.YEAR) "${d.monthValue}/${d.dayOfMonth}"
                                else "${d.monthValue}/${d.dayOfMonth}"
                            } catch (_: Exception) { filtered[i].date }
                            val measured = textMeasurer.measure(dateStr, TextStyle(fontSize = 8.sp, color = labelColor))
                            val x = xOf(i) - measured.size.width / 2f
                            drawText(measured, topLeft = Offset(x.coerceIn(0f, w - measured.size.width), h - measured.size.height))
                        }
                    }
                }
            }

            // Latest weight + tap hint
            val latest = filtered.lastOrNull() ?: entries.lastOrNull()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (latest != null) {
                    Text(
                        "${latest.weight} lbs · ${latest.date}",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "tap=cycle  long press=log",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    // Entry dialog
    if (showEntryDialog) {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val existing = entries.find { it.date == todayStr }

        LaunchedEffect(Unit) {
            dialogWeight = existing?.weight?.toString() ?: ""
            dialogNotes = existing?.notes ?: ""
        }

        AlertDialog(
            onDismissRequest = { showEntryDialog = false },
            title = { Text("Log Weight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(todayStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = dialogWeight,
                        onValueChange = { dialogWeight = it },
                        label = { Text("Weight (lbs)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    OutlinedTextField(
                        value = dialogNotes,
                        onValueChange = { dialogNotes = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = dialogWeight.trim().toFloatOrNull()
                    if (w != null) {
                        onSaveEntry(todayStr, w, dialogNotes.trim())
                        showEntryDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEntryDialog = false }) { Text("Cancel") }
            }
        )
    }
}
