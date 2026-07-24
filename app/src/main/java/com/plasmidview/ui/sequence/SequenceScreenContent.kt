package com.plasmidview.ui.sequence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.*
import kotlin.math.*

private val baseColors = mapOf(
    'A' to Color(0xFF4CAF50), 'T' to Color(0xFFF44336),
    'G' to Color(0xFFFF9800), 'C' to Color(0xFF2196F3),
    'N' to Color(0xFF9E9E9E),
)
private val CHAR_W = 7.6f.dp
private val TRACK_H = 16.dp
private val NUM_W_CHARS = 7

@Composable
fun SequenceScreenContent(docIndex: Int, fontSize: Float = 12f) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var searchQ by remember { mutableStateOf("") }
    var selFeat by remember { mutableStateOf<Feature?>(null) }
    var showDlg by remember { mutableStateOf(false) }
    val seq = doc.sequence.replace(Regex("\\s+"), "").uppercase()
    val density = LocalDensity.current
    val ctx = LocalContext.current
    val prefs = remember { com.plasmidview.data.model.AppPreferences(ctx) }
    val baseCol by prefs.baseCol.collectAsState(initial = true)

    val screenW = LocalConfiguration.current.screenWidthDp
    val numW = (NUM_W_CHARS * CHAR_W.value).dp
    val availW = screenW - numW.value - 4
    val cpl = (availW / CHAR_W.value).toInt().coerceIn(8, 70)
    val seqW = (cpl * CHAR_W.value).dp
    val totalW = numW + seqW
    val lines = seq.chunked(cpl)

    // Pre-compute track layers
    val lineTracks = remember(lines, doc.features) {
        lines.mapIndexed { li, line ->
            val ls = li * cpl; val le = ls + line.length - 1  // 0-based
            val ov = doc.features.filter { f -> !(f.end < ls || f.start > le) }
            if (ov.isEmpty()) emptyList<List<Feature>>() else {
                val sorted = ov.sortedBy { it.start }
                val layers = mutableListOf<MutableList<Feature>>()
                for (f in sorted) {
                    var placed = false
                    for (l in layers) { if (l.all { it.end < f.start }) { l.add(f); placed = true; break } }
                    if (!placed) layers.add(mutableListOf(f))
                }
                layers.toList()
            }
        }
    }

    // Pre-compute block heights for each line (shared between drawing and tap detection)
    val fontPx = with(density) { fontSize.sp.toPx() }
    val trackPx = with(density) { TRACK_H.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val padPx = with(density) { 4.dp.toPx() }
    val blockHeights = remember(fontPx, trackPx, gapPx, padPx, lineTracks) {
        lines.indices.map { li -> fontPx + gapPx + lineTracks[li].size * trackPx + padPx }
    }
    val totalH = blockHeights.sumOf { it.toDouble() }.toFloat()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = searchQ, onValueChange = { searchQ = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search sequence...") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            trailingIcon = if (searchQ.isNotBlank()) { { IconButton(onClick = { searchQ = "" }) { Icon(Icons.Default.Close, "Clear") } } } else null)

        if (seq.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No sequence data") }
        } else {
            val hScroll = rememberScrollState()
            LaunchedEffect(Unit) { hScroll.scrollTo(with(density) { numW.roundToPx() }) }

            Row(Modifier.horizontalScroll(hScroll)) {
            val isDarkCanvas = (MaterialTheme.colorScheme.background.red * 0.299 + MaterialTheme.colorScheme.background.green * 0.587 + MaterialTheme.colorScheme.background.blue * 0.114) < 0.5f
                Box(Modifier.width(totalW).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(totalW).height(with(density) { totalH.toDp() }).pointerInput(lines, doc.features, blockHeights) {
                        detectTapGestures { tap ->
                            var acc = 0f
                            for (li in lines.indices) {
                                val bh = blockHeights[li]
                                if (tap.y >= acc && tap.y < acc + bh) {
                                    val inSeq = tap.y < acc + fontPx + 2.dp.toPx()
                                    val ls = li * cpl  // 0-based line start
                                    val ci = ((tap.x - numW.toPx()) / CHAR_W.toPx()).toInt().coerceIn(0, cpl - 1)
                                    val pos = ls + ci  // 0-based position
                                    doc.features.firstOrNull { f -> f.start <= pos && pos <= f.end }?.let {
                                        selFeat = it; showDlg = true
                                    }
                                    break
                                }
                                acc += bh
                            }
                        }
                    }) {
                        val charPx = CHAR_W.toPx()
                        val numPx = numW.toPx()
                        val lineH = fontPx
                        val searchQry = searchQ.uppercase()
                        val dashFx = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                        var yPos = 2.dp.toPx()

                        lines.forEachIndexed { li, line ->
                            val lineStart = li * cpl  // 0-based

                            // === Sequence line ===
                            val seqY = yPos + lineH
                            drawContext.canvas.nativeCanvas.drawText("%6d".format(lineStart + 1), 0f, seqY,
                                android.graphics.Paint().apply { color = Color.Gray.copy(alpha = 0.6f).hashCode(); textSize = lineH })

                            // Pre-compute highlight positions
                            val hlPos = mutableSetOf<Int>()
                            if (searchQry.isNotEmpty()) {
                                var si = 0
                                while (true) {
                                    val fi = line.indexOf(searchQry, si)
                                    if (fi < 0) break
                                    for (p in fi until fi + searchQry.length) hlPos.add(p)
                                    si = fi + 1
                                }
                            }
                            line.forEachIndexed { ci, base ->
                                val x = numPx + ci * charPx
                                val gray = Color.Gray.copy(alpha = 0.8f)
                                val col = if (baseCol) (baseColors[base] ?: Color.Gray) else gray
                                if (ci in hlPos) {
                                    drawRect(Color.Yellow, Offset(x, yPos), Size(charPx, lineH))
                                }
                                drawContext.canvas.nativeCanvas.drawText(base.toString(), x, seqY,
                                    android.graphics.Paint().apply { color = col.hashCode(); textSize = lineH })
                            }
                            yPos += lineH + 2.dp.toPx()

                            // === Tracks ===
                            val layers = lineTracks[li]
                            layers.forEachIndexed { _, layer ->
                                val trackY = yPos
                                layer.forEach { f ->
                                    val s = max(f.start, lineStart); val e = min(f.end, lineStart + cpl - 1)
                                    if (s >= e) return@forEach
                                    val x1 = numPx + ((s - lineStart).toFloat() / cpl) * (cpl * charPx)
                                    val x2 = numPx + ((e - lineStart + 1).toFloat() / cpl) * (cpl * charPx)
                                    val w = x2 - x1; if (w < 2.dp.toPx()) return@forEach
                                    val col = try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }

                                    // Border rect (slightly larger) for direction indication
                                    val borderW = 2.dp.toPx()
                                    if (f.strand != Strand.NONE) {
                                        val bCol = if (isDarkCanvas) Color.White else Color.Black
                                        val bFx = if (f.strand == Strand.REVERSE) dashFx else null
                                        drawRect(bCol, Offset(x1 - borderW/2, trackY - borderW/2),
                                            Size(w + borderW, trackPx + borderW),
                                            style = Stroke(borderW, pathEffect = bFx))
                                    }
                                    // Fill rect
                                    drawRect(col.copy(alpha = 0.6f), Offset(x1, trackY), Size(w, trackPx))

                                    // Feature name
                                    val name = f.name
                                    if (name.isNotBlank() && w > name.length * 4.dp.toPx()) {
                                        drawContext.canvas.nativeCanvas.drawText(name, x1 + 2.dp.toPx(),
                                            trackY + trackPx - 3.dp.toPx(),
                                            android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 9.sp.toPx() })
                                    }
                                }
                                yPos += trackPx
                            }
                            yPos += 4.dp.toPx()
                        }
                    }
                }
            }
        }
    }

    if (showDlg && selFeat != null) FeatDlg(selFeat!!, doc) { showDlg = false; selFeat = null }
}

@Composable
private fun FeatDlg(f: Feature, doc: com.plasmidview.data.model.PlasmidDocument, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(f.name.ifBlank { f.type.label }, fontWeight = FontWeight.Bold) },
        text = { Column {
            Text("Type: ${f.type.label}"); Text("Position: ${f.start + 1} - ${f.end}")
            Text("Length: ${f.length} bp"); Text("Strand: ${f.strand.label}")
            val seq = f.displaySequence(doc)
            if (seq.isNotEmpty()) { Spacer(Modifier.height(8.dp))
                Text("Sequence (${seq.length} bp):", fontWeight = FontWeight.Bold); Text(seq.take(300) + if (seq.length > 300) "..." else "") }
        }},
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(onClick = { val seq = f.displaySequence(doc)
                    if (seq.isNotEmpty()) clip.setText(AnnotatedString(seq)) }) { Text("Copy") }
            }
        }
    )
}
