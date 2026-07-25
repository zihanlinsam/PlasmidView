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
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.*
import com.plasmidview.ui.common.FeatureDetailDialog
import kotlin.math.*

private val baseColors = mapOf(
    'A' to Color(0xFF4CAF50), 'T' to Color(0xFFF44336),
    'G' to Color(0xFFFF9800), 'C' to Color(0xFF2196F3),
    'N' to Color(0xFF9E9E9E),
)
private val TRACK_H = 16.dp
private val NUM_W_CHARS = 7

private data class Seg(val f: Feature, val s: Int, val e: Int) // 0-based closed interval

@Composable
fun SequenceScreenContent(docIndex: Int, fontSize: Float = 12f) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var searchQ by remember { mutableStateOf("") }
    var selFeat by remember { mutableStateOf<Feature?>(null) }
    var showDlg by remember { mutableStateOf(false) }
    var candFeats by remember { mutableStateOf<List<Feature>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    val seq = remember(doc) { doc.sequence.replace(Regex("\\s+"), "").uppercase() }
    val density = LocalDensity.current
    val ctx = LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val baseCol by prefs.baseCol.collectAsState(initial = true)
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiLang by prefs.aiLang.collectAsState(initial = "english")
    val aiThinking by prefs.aiThinking.collectAsState(initial = false)

    val fontPx = with(density) { fontSize.sp.toPx() }
    val monoPaint = remember(fontPx) { android.graphics.Paint().apply { textSize = fontPx; typeface = android.graphics.Typeface.MONOSPACE } }
    val charW = monoPaint.measureText("A")
    val charWDp = with(density) { charW.toDp() }

    val screenW = LocalConfiguration.current.screenWidthDp
    val numW = (NUM_W_CHARS * charWDp.value).dp
    val availW = screenW - numW.value - 4
    val cpl = (availW / charWDp.value).toInt().coerceIn(8, 70)
    val seqW = (cpl * charWDp.value).dp
    val totalW = numW + seqW
    val lines = remember(seq, cpl) { seq.chunked(cpl) }

    // All segments (cross-origin features split)
    val allSegs = remember(doc.features, doc.totalLength) {
        doc.features.flatMap { f ->
            if (f.start < f.end) listOf(Seg(f, f.start, f.end))
            else listOf(Seg(f, f.start, doc.totalLength - 1), Seg(f, 0, f.end))
        }
    }

    // Pre-compute track layers per line using Segs
    val lineTracks = remember(lines, allSegs) {
        lines.mapIndexed { li, line ->
            val ls = li * cpl; val le = ls + line.length - 1
            val ov = allSegs.filter { seg -> !(seg.e < ls || seg.s > le) }
            if (ov.isEmpty()) emptyList<List<Seg>>() else {
                val sorted = ov.sortedBy { it.s }
                val layers = mutableListOf<MutableList<Seg>>()
                for (seg in sorted) {
                    var placed = false
                    for (l in layers) { if (l.all { it.e < seg.s }) { l.add(seg); placed = true; break } }
                    if (!placed) layers.add(mutableListOf(seg))
                }
                layers.toList()
            }
        }
    }

    // Search index (outside Canvas, cached)
    val hlByLine = remember(searchQ, seq, cpl) {
        val q = searchQ.uppercase()
        if (q.isEmpty()) return@remember emptyMap<Int, Set<Int>>()
        val map = mutableMapOf<Int, MutableSet<Int>>()
        var si = 0
        while (true) {
            val fi = seq.indexOf(q, si)
            if (fi < 0) break
            for (p in fi until fi + q.length) {
                map.getOrPut(p / cpl) { mutableSetOf() }.add(p % cpl)
            }
            si = fi + 1
        }
        map
    }

    val trackPx = with(density) { TRACK_H.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val padPx = with(density) { 4.dp.toPx() }
    val blockHeights = remember(fontPx, trackPx, gapPx, padPx, lineTracks) {
        lines.indices.map { li -> fontPx + gapPx + lineTracks[li].size * trackPx + padPx }
    }
    val totalH = blockHeights.sumOf { it.toDouble() }.toFloat()
    val textArgb = MaterialTheme.colorScheme.onBackground.toArgb()
    val onBgArgb = textArgb

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
                Box(Modifier.width(totalW).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Canvas(Modifier.width(totalW).height(with(density) { totalH.toDp() })
                        .pointerInput(lines, allSegs, blockHeights, lineTracks) {
                            detectTapGestures { tap ->
                                var acc = 2.dp.toPx()
                                for (li in lines.indices) {
                                    val bh = blockHeights[li]
                                    if (tap.y >= acc && tap.y < acc + bh) {
                                        val ls = li * cpl
                                        val ci = ((tap.x - numW.toPx()) / charW).toInt().coerceIn(0, cpl - 1)
                                        val pos = ls + ci
                                        val layers = lineTracks[li]
                                        val layerIdx = ((tap.y - acc - fontPx - 2.dp.toPx()) / trackPx).toInt()

                                        if (layerIdx in layers.indices) {
                                            layers[layerIdx].firstOrNull { seg -> pos in seg.s..seg.e }?.f
                                                ?.let { selFeat = it; showDlg = true }
                                        } else {
                                            val hits = doc.features.filter { f ->
                                                if (f.start < f.end) pos in f.start..f.end
                                                else pos >= f.start || pos <= f.end
                                            }
                                            when (hits.size) {
                                                1 -> { selFeat = hits[0]; showDlg = true }
                                                in 2..Int.MAX_VALUE -> { candFeats = hits; showPicker = true }
                                            }
                                        }
                                        break
                                    }
                                    acc += bh
                                }
                            }
                        }
                    ) {
                        val charPx = charW
                        val numPx = numW.toPx()
                        val lineH = fontPx
                        var yPos = 2.dp.toPx()

                        // Reusable paints (created once per draw)
                        val lineNumPaint = android.graphics.Paint().apply { typeface = android.graphics.Typeface.MONOSPACE }
                        val charPaint = android.graphics.Paint().apply { typeface = android.graphics.Typeface.MONOSPACE }

                        lines.forEachIndexed { li, line ->
                            val lineStart = li * cpl
                            val seqY = yPos + lineH
                            val hlSet = hlByLine[li] ?: emptySet()

                            // Line number
                            lineNumPaint.color = Color.Gray.copy(alpha = 0.6f).toArgb()
                            lineNumPaint.textSize = lineH
                            drawContext.canvas.nativeCanvas.drawText("%6d".format(lineStart + 1), 0f, seqY, lineNumPaint)

                            // Sequence characters
                            charPaint.textSize = lineH
                            line.forEachIndexed { ci, base ->
                                val x = numPx + ci * charPx
                                val gray = Color.Gray.copy(alpha = 0.8f)
                                val charCol = if (baseCol) (baseColors[base] ?: Color.Gray) else gray
                                if (ci in hlSet) {
                                    drawRect(Color.Yellow, Offset(x, yPos), Size(charPx, lineH))
                                }
                                charPaint.color = charCol.toArgb()
                                drawContext.canvas.nativeCanvas.drawText(base.toString(), x, seqY, charPaint)
                            }
                            yPos += lineH + 2.dp.toPx()

                            // Track layers
                            val layers = lineTracks[li]
                            layers.forEach { layer ->
                                val trackY = yPos
                                layer.forEach { seg ->
                                    val s = max(seg.s, lineStart); val e = min(seg.e, lineStart + cpl - 1)
                                    if (s > e) return@forEach
                                    val x1 = numPx + (s - lineStart) * charPx
                                    val x2 = numPx + (e - lineStart + 1) * charPx
                                    val w = x2 - x1; if (w < 2.dp.toPx()) return@forEach
                                    val col = try { Color(android.graphics.Color.parseColor(seg.f.color)) } catch (_: Exception) { Color.Gray }

                                    // Arrow only at the real end of the feature
                                    val showArrow = seg.f.strand != Strand.NONE && w > 6.dp.toPx() &&
                                        when (seg.f.strand) {
                                            Strand.FORWARD -> seg.e <= lineStart + cpl - 1
                                            Strand.REVERSE -> seg.s >= lineStart
                                            else -> false
                                        }

                                    if (showArrow) {
                                        val a = 5.dp.toPx()
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            if (seg.f.strand == Strand.FORWARD) {
                                                moveTo(x1, trackY)
                                                lineTo(x2 - a, trackY)
                                                lineTo(x2, trackY + trackPx / 2f)
                                                lineTo(x2 - a, trackY + trackPx)
                                                lineTo(x1, trackY + trackPx)
                                            } else {
                                                moveTo(x1 + a, trackY)
                                                lineTo(x2, trackY)
                                                lineTo(x2, trackY + trackPx)
                                                lineTo(x1 + a, trackY + trackPx)
                                                lineTo(x1, trackY + trackPx / 2f)
                                            }
                                            close()
                                        }
                                        drawPath(path, col.copy(alpha = 0.6f))
                                    } else {
                                        drawRect(col.copy(alpha = 0.6f), Offset(x1, trackY), Size(w, trackPx))
                                    }

                                    val name = seg.f.name
                                    if (name.isNotBlank() && w > name.length * 4.dp.toPx()) {
                                        drawContext.canvas.nativeCanvas.drawText(name, x1 + 2.dp.toPx(),
                                            trackY + trackPx - 3.dp.toPx(),
                                            android.graphics.Paint().apply { color = onBgArgb; textSize = 9.sp.toPx() })
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

    if (showDlg && selFeat != null) FeatureDetailDialog(selFeat!!, doc, onDismiss = { showDlg = false; selFeat = null },
        aiUrl = aiUrl, aiKey = aiKey, aiModel = aiModel, aiLang = aiLang, aiThinking = aiThinking)

    if (showPicker && candFeats.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPicker = false; candFeats = emptyList() },
            title = { Text("Select Feature", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    candFeats.forEach { f ->
                        TextButton(onClick = { showPicker = false; selFeat = f; showDlg = true }) {
                            Text("${f.name.ifBlank { f.type.label }} — ${f.start + 1}-${f.end} (${f.length}bp)")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false; candFeats = emptyList() }) { Text("Cancel") } }
        )
    }
}
