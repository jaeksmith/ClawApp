package com.jaek.clawapp.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.jaek.clawapp.model.BloodPressureEntry
import com.jaek.clawapp.model.HeartRateEntry
import com.jaek.clawapp.model.WeightEntry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    weightEntries: List<WeightEntry>,
    heartRateEntries: List<HeartRateEntry>,
    bloodPressureEntries: List<BloodPressureEntry>,
    onBack: () -> Unit
) {
    var range by remember { mutableStateOf(HealthRange.MONTH) }
    val today = LocalDate.now()
    val cutoff = today.minusDays(range.days)
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    val mutedColor = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val labelColor = colorScheme.onSurfaceVariant

    val filteredWeight = remember(weightEntries, range) {
        weightEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }
    val filteredHeart = remember(heartRateEntries, range) {
        heartRateEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }
    val filteredBP = remember(bloodPressureEntries, range) {
        bloodPressureEntries.filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(false) }
    }

    // Returns a shared time-axis mapping function for given range params
    val axisStartDay = cutoff.toEpochDay().toFloat()
    val axisEndDay = today.toEpochDay().toFloat()
    val axisDays = (axisEndDay - axisStartDay).coerceAtLeast(1f)

    fun xOfDate(d: LocalDate, padLeft: Float, graphW: Float): Float =
        padLeft + ((d.toEpochDay() - axisStartDay) / axisDays) * graphW

    fun cycleRange() {
        range = when (range) {
            HealthRange.WEEK -> HealthRange.MONTH
            HealthRange.MONTH -> HealthRange.YEAR
            HealthRange.YEAR -> HealthRange.WEEK
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏥 Health") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Showing:", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                HealthRange.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r.label, fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            // ── Weight ──────────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚖️ Weight (lbs)", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val pts = filteredWeight.mapNotNull { e ->
                        runCatching { Pair(LocalDate.parse(e.date), e.weight) }.getOrNull()
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { cycleRange() }) }
                    ) {
                        val w = size.width; val h = size.height
                        val padL = 40f; val padR = 8f; val padT = 8f; val padB = 20f
                        val gW = w - padL - padR; val gH = h - padT - padB

                        if (pts.isEmpty()) {
                            val lbl = textMeasurer.measure("No data", TextStyle(fontSize = 12.sp, color = labelColor))
                            drawText(lbl, topLeft = Offset((w - lbl.size.width) / 2f, (h - lbl.size.height) / 2f))
                            return@Canvas
                        }

                        val vals = pts.map { it.second }
                        val minV = (vals.min() - 2f).coerceAtLeast(0f)
                        val maxV = vals.max() + 2f
                        val rangeV = (maxV - minV).coerceAtLeast(1f)
                        fun yOf(v: Float) = padT + gH - ((v - minV) / rangeV) * gH

                        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
                        for (i in 0..4) {
                            val v = minV + (rangeV * i / 4)
                            val y = yOf(v)
                            drawLine(mutedColor, Offset(padL, y), Offset(w - padR, y), 0.5f)
                            val lbl = textMeasurer.measure(v.toInt().toString(), labelStyle)
                            drawText(lbl, topLeft = Offset(0f, y - lbl.size.height / 2f))
                        }
                        if (pts.size > 1) {
                            val path = Path()
                            pts.forEachIndexed { i, (d, v) ->
                                val x = xOfDate(d, padL, gW); val y = yOf(v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFFBB86FC), style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        pts.forEach { (d, v) ->
                            val x = xOfDate(d, padL, gW); val y = yOf(v)
                            drawCircle(Color.Black, 5f, Offset(x, y))
                            drawCircle(Color(0xFFBB86FC), 4f, Offset(x, y))
                        }
                        val axStyle = TextStyle(fontSize = 8.sp, color = labelColor)
                        listOf(cutoff, cutoff.plusDays(range.days / 2), today).forEach { d ->
                            val lbl = textMeasurer.measure("${d.monthValue}/${d.dayOfMonth}", axStyle)
                            val x = xOfDate(d, padL, gW) - lbl.size.width / 2f
                            drawText(lbl, topLeft = Offset(x.coerceIn(padL, w - padR - lbl.size.width), h - lbl.size.height))
                        }
                    }
                    val lastW = filteredWeight.lastOrNull()
                    if (lastW != null) Text("Latest: ${lastW.weight} lbs · ${lastW.date}", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }

            // ── Heart Rate ──────────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("❤️ Heart Rate (BPM)", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val pts = filteredHeart.mapNotNull { e ->
                        runCatching { Pair(LocalDate.parse(e.date), e.bpm.toFloat()) }.getOrNull()
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { cycleRange() }) }
                    ) {
                        val w = size.width; val h = size.height
                        val padL = 40f; val padR = 8f; val padT = 8f; val padB = 20f
                        val gW = w - padL - padR; val gH = h - padT - padB

                        if (pts.isEmpty()) {
                            val lbl = textMeasurer.measure("No data", TextStyle(fontSize = 12.sp, color = labelColor))
                            drawText(lbl, topLeft = Offset((w - lbl.size.width) / 2f, (h - lbl.size.height) / 2f))
                            return@Canvas
                        }

                        val vals = pts.map { it.second }
                        val minV = (vals.min() - 5f).coerceAtLeast(0f)
                        val maxV = vals.max() + 5f
                        val rangeV = (maxV - minV).coerceAtLeast(1f)
                        fun yOf(v: Float) = padT + gH - ((v - minV) / rangeV) * gH

                        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
                        for (i in 0..4) {
                            val v = minV + (rangeV * i / 4)
                            val y = yOf(v)
                            drawLine(mutedColor, Offset(padL, y), Offset(w - padR, y), 0.5f)
                            val lbl = textMeasurer.measure(v.toInt().toString(), labelStyle)
                            drawText(lbl, topLeft = Offset(0f, y - lbl.size.height / 2f))
                        }
                        if (pts.size > 1) {
                            val path = Path()
                            pts.forEachIndexed { i, (d, v) ->
                                val x = xOfDate(d, padL, gW); val y = yOf(v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFFF44336), style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        pts.forEach { (d, v) ->
                            val x = xOfDate(d, padL, gW); val y = yOf(v)
                            drawCircle(Color.Black, 5f, Offset(x, y))
                            drawCircle(Color(0xFFF44336), 4f, Offset(x, y))
                        }
                        val axStyle = TextStyle(fontSize = 8.sp, color = labelColor)
                        listOf(cutoff, cutoff.plusDays(range.days / 2), today).forEach { d ->
                            val lbl = textMeasurer.measure("${d.monthValue}/${d.dayOfMonth}", axStyle)
                            val x = xOfDate(d, padL, gW) - lbl.size.width / 2f
                            drawText(lbl, topLeft = Offset(x.coerceIn(padL, w - padR - lbl.size.width), h - lbl.size.height))
                        }
                    }
                    val lastH = filteredHeart.lastOrNull()
                    if (lastH != null) Text("Latest: ${lastH.bpm} BPM · ${lastH.date}", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }

            // ── Blood Pressure ──────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colorScheme.surface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🩺 Blood Pressure", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(Color(0xFF2196F3)) }
                            Text("Systolic", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(Color(0xFF00BCD4)) }
                            Text("Diastolic", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val spts = filteredBP.mapNotNull { e ->
                        runCatching { Pair(LocalDate.parse(e.date), e.systolic.toFloat()) }.getOrNull()
                    }
                    val dpts = filteredBP.mapNotNull { e ->
                        runCatching { Pair(LocalDate.parse(e.date), e.diastolic.toFloat()) }.getOrNull()
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .pointerInput(Unit) { detectTapGestures(onTap = { cycleRange() }) }
                    ) {
                        val w = size.width; val h = size.height
                        val padL = 40f; val padR = 8f; val padT = 8f; val padB = 20f
                        val gW = w - padL - padR; val gH = h - padT - padB

                        if (spts.isEmpty() && dpts.isEmpty()) {
                            val lbl = textMeasurer.measure("No data", TextStyle(fontSize = 12.sp, color = labelColor))
                            drawText(lbl, topLeft = Offset((w - lbl.size.width) / 2f, (h - lbl.size.height) / 2f))
                            return@Canvas
                        }

                        val allVals = (spts + dpts).map { it.second }
                        val minV = (allVals.min() - 10f).coerceAtLeast(0f)
                        val maxV = allVals.max() + 10f
                        val rangeV = (maxV - minV).coerceAtLeast(1f)
                        fun yOf(v: Float) = padT + gH - ((v - minV) / rangeV) * gH

                        val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)
                        for (i in 0..4) {
                            val v = minV + (rangeV * i / 4)
                            val y = yOf(v)
                            drawLine(mutedColor, Offset(padL, y), Offset(w - padR, y), 0.5f)
                            val lbl = textMeasurer.measure(v.toInt().toString(), labelStyle)
                            drawText(lbl, topLeft = Offset(0f, y - lbl.size.height / 2f))
                        }

                        fun drawLine2(pts: List<Pair<LocalDate, Float>>, color: Color) {
                            if (pts.size > 1) {
                                val path = Path()
                                pts.forEachIndexed { i, (d, v) ->
                                    val x = xOfDate(d, padL, gW); val y = yOf(v)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            pts.forEach { (d, v) ->
                                val x = xOfDate(d, padL, gW); val y = yOf(v)
                                drawCircle(Color.Black, 5f, Offset(x, y))
                                drawCircle(color, 4f, Offset(x, y))
                            }
                        }
                        drawLine2(spts, Color(0xFF2196F3))
                        drawLine2(dpts, Color(0xFF00BCD4))

                        val axStyle = TextStyle(fontSize = 8.sp, color = labelColor)
                        listOf(cutoff, cutoff.plusDays(range.days / 2), today).forEach { d ->
                            val lbl = textMeasurer.measure("${d.monthValue}/${d.dayOfMonth}", axStyle)
                            val x = xOfDate(d, padL, gW) - lbl.size.width / 2f
                            drawText(lbl, topLeft = Offset(x.coerceIn(padL, w - padR - lbl.size.width), h - lbl.size.height))
                        }
                    }
                    val lastBP = filteredBP.lastOrNull()
                    if (lastBP != null) Text("Latest: ${lastBP.systolic}/${lastBP.diastolic} · ${lastBP.date}", fontSize = 11.sp, color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
