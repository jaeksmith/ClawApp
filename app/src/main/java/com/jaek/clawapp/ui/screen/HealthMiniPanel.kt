package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaek.clawapp.model.BloodPressureEntry
import com.jaek.clawapp.model.HeartRateEntry
import com.jaek.clawapp.model.WeightEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HealthRange(val label: String, val days: Long) {
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthMiniPanel(
    weightEntries: List<WeightEntry>,
    heartRateEntries: List<HeartRateEntry>,
    bloodPressureEntries: List<BloodPressureEntry>,
    onSaveWeight: (date: String, weight: Float) -> Unit,
    onSaveHeartRate: (date: String, bpm: Int) -> Unit,
    onSaveBloodPressure: (date: String, systolic: Int, diastolic: Int) -> Unit,
    onTapGraph: () -> Unit
) {
    if (weightEntries.isEmpty() && heartRateEntries.isEmpty() && bloodPressureEntries.isEmpty()) return

    var range by remember { mutableStateOf(HealthRange.MONTH) }
    var showWeight by remember { mutableStateOf(true) }
    var showHeart by remember { mutableStateOf(true) }
    var showBP by remember { mutableStateOf(true) }
    var showLogDialog by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val cutoff = today.minusDays(range.days)

    val filteredWeight = remember(weightEntries, range) {
        weightEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }
    val filteredHeart = remember(heartRateEntries, range) {
        heartRateEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }
    val filteredBP = remember(bloodPressureEntries, range) {
        bloodPressureEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }

    val colorScheme = MaterialTheme.colorScheme
    val weightColor = Color(0xFFBB86FC)
    val heartColor = Color(0xFFF44336)
    val sysColor = Color(0xFF2196F3)
    val diaColor = Color(0xFF00BCD4)
    val mutedLineColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: series toggles + range button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showWeight,
                        onClick = { showWeight = !showWeight },
                        label = { Text("⚖️", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showHeart,
                        onClick = { showHeart = !showHeart },
                        label = { Text("❤️", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = showBP,
                        onClick = { showBP = !showBP },
                        label = { Text("🩺", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
                TextButton(onClick = {
                    range = when (range) {
                        HealthRange.WEEK -> HealthRange.MONTH
                        HealthRange.MONTH -> HealthRange.YEAR
                        HealthRange.YEAR -> HealthRange.WEEK
                    }
                }) {
                    Text(range.label, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Multi-series canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTapGraph() },
                            onLongPress = { showLogDialog = true }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val padLeft = 8f
                val padRight = 8f
                val padTop = 8f
                val padBottom = 8f
                val graphW = w - padLeft - padRight
                val graphH = h - padTop - padBottom

                val axisStartDay = cutoff.toEpochDay().toFloat()
                val axisEndDay = today.toEpochDay().toFloat()
                val axisDays = (axisEndDay - axisStartDay).coerceAtLeast(1f)

                fun xOfDate(d: LocalDate): Float =
                    padLeft + ((d.toEpochDay() - axisStartDay) / axisDays) * graphW

                // Draw one series independently scaled to fill the Y space
                fun drawSeries(points: List<Pair<LocalDate, Float>>, color: Color) {
                    if (points.isEmpty()) return
                    val vals = points.map { it.second }
                    val span = (vals.max() - vals.min()).coerceAtLeast(1f)
                    val minV = vals.min() - span * 0.1f
                    val maxV = vals.max() + span * 0.1f
                    val rangeV = (maxV - minV).coerceAtLeast(1f)

                    fun yOf(v: Float) = padTop + graphH - ((v - minV) / rangeV) * graphH

                    if (points.size > 1) {
                        val path = Path()
                        points.forEachIndexed { i, (d, v) ->
                            val x = xOfDate(d); val y = yOf(v)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    points.forEach { (d, v) ->
                        val x = xOfDate(d); val y = yOf(v)
                        drawCircle(Color.Black, radius = 4f, center = Offset(x, y))
                        drawCircle(color, radius = 3f, center = Offset(x, y))
                    }
                }

                // Subtle midline
                drawLine(mutedLineColor, Offset(padLeft, h / 2), Offset(w - padRight, h / 2), 0.5f)

                if (showWeight) {
                    drawSeries(
                        filteredWeight.mapNotNull { e ->
                            runCatching { Pair(LocalDate.parse(e.date), e.weight) }.getOrNull()
                        },
                        weightColor
                    )
                }
                if (showHeart) {
                    drawSeries(
                        filteredHeart.mapNotNull { e ->
                            runCatching { Pair(LocalDate.parse(e.date), e.bpm.toFloat()) }.getOrNull()
                        },
                        heartColor
                    )
                }
                if (showBP) {
                    drawSeries(
                        filteredBP.mapNotNull { e ->
                            runCatching { Pair(LocalDate.parse(e.date), e.systolic.toFloat()) }.getOrNull()
                        },
                        sysColor
                    )
                    drawSeries(
                        filteredBP.mapNotNull { e ->
                            runCatching { Pair(LocalDate.parse(e.date), e.diastolic.toFloat()) }.getOrNull()
                        },
                        diaColor
                    )
                }
            }

            // Bottom hint
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "tap=detail  long press=log",
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    // Log dialog
    if (showLogDialog) {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        var checkWeight by remember { mutableStateOf(false) }
        var checkHeart by remember { mutableStateOf(false) }
        var checkBP by remember { mutableStateOf(false) }
        var inputWeight by remember { mutableStateOf(weightEntries.lastOrNull()?.weight?.toString() ?: "") }
        var inputBpm by remember { mutableStateOf("") }
        var inputSys by remember { mutableStateOf("") }
        var inputDia by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Log Health Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        todayStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    // Weight
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkWeight, onCheckedChange = { checkWeight = it })
                        Text("⚖️ Weight")
                    }
                    if (checkWeight) {
                        OutlinedTextField(
                            value = inputWeight,
                            onValueChange = { inputWeight = it },
                            label = { Text("lbs") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Heart rate
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkHeart, onCheckedChange = { checkHeart = it })
                        Text("❤️ Heart Rate")
                    }
                    if (checkHeart) {
                        OutlinedTextField(
                            value = inputBpm,
                            onValueChange = { inputBpm = it },
                            label = { Text("BPM") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Blood pressure
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checkBP, onCheckedChange = { checkBP = it })
                        Text("🩺 Blood Pressure")
                    }
                    if (checkBP) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inputSys,
                                onValueChange = { inputSys = it },
                                label = { Text("Sys") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputDia,
                                onValueChange = { inputDia = it },
                                label = { Text("Dia") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (checkWeight) inputWeight.trim().toFloatOrNull()?.let { onSaveWeight(todayStr, it) }
                    if (checkHeart) inputBpm.trim().toIntOrNull()?.let { onSaveHeartRate(todayStr, it) }
                    if (checkBP) {
                        val s = inputSys.trim().toIntOrNull()
                        val d = inputDia.trim().toIntOrNull()
                        if (s != null && d != null) onSaveBloodPressure(todayStr, s, d)
                    }
                    showLogDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("Cancel") }
            }
        )
    }
}
