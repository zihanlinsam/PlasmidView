package com.plasmidview.ui.alignment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.ai.AiClient
import com.plasmidview.data.alignment.AlignmentSession
import com.plasmidview.data.model.*
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

private val baseColors = mapOf(
    'A' to Color(0xFF4CAF50), 'T' to Color(0xFFF44336),
    'G' to Color(0xFFFF9800), 'C' to Color(0xFF2196F3),
    'N' to Color(0xFF9E9E9E),
)
private val CHAR_W = 9.dp
private val HEADER_W = 48.dp
private val matchColors = mapOf(
    '*' to Color(0xFF4CAF50), ':' to Color(0xFF2196F3),
    '.' to Color(0xFFFF9800),
)

/** A contiguous feature segment within one chunk of the alignment. */
private data class ChunkFeat(
    val startChar: Int,   // 0-based char index within this chunk
    val endChar: Int,     // exclusive end char index
    val name: String, val color: Color, val isReverse: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlignmentScreen(onBack: () -> Unit) {
    val r = AlignmentSession.result ?: run { onBack(); return }
    val doc = AlignmentSession.document ?: run { onBack(); return }
    val origQuery = AlignmentSession.originalQuerySeq

    val ctx = LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiLang by prefs.aiLang.collectAsState(initial = "english")
    val aiThinking by prefs.aiThinking.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }

    val pct = if (r.maxScore > 0) r.score.toFloat() / r.maxScore * 100f else 0f
    val pctFormatted = "%.1f".format(pct)
    val strandLabel = if (r.isRevComp) "Reverse" else "Forward"

    // Use the filtered sequence (matching SmithWaterman's internal filter) for correct indices
    val filteredQuery = origQuery.uppercase().filter { it in "ACGTNRYSWKMBDHV" }
    val alignedSeq = if (r.isRevComp) filteredQuery.reverseComplement() else filteredQuery

    // Detect overhangs: compare original query length vs aligned bases
    val alignedBases = r.queryAligned.count { it != '-' }
    val totalQueryLen = alignedSeq.length
    val prefixBases = r.queryMatchStart.coerceIn(0, totalQueryLen)
    val suffixBases = (totalQueryLen - alignedBases - prefixBases).coerceAtLeast(0)

    val qPrefix = if (prefixBases > 0) alignedSeq.take(prefixBases) else ""
    val qSuffix = if (suffixBases > 0) alignedSeq.takeLast(suffixBases) else ""

    // Clip display to good match region using matchLine quality
    val firstGood = r.matchLine.indexOfFirst { it == '*' || it == ':' }
    val lastGood = r.matchLine.indexOfLast { it == '*' || it == ':' }
    val ds = if (firstGood >= 0) firstGood else 0
    val de = if (lastGood > ds) lastGood + 1 else r.matchLine.length
    val refStr = r.refAligned.substring(ds, de)
    val matchStr = r.matchLine.substring(ds, de)
    val queryStr = r.queryAligned.substring(ds, de)

    // Chars per line from screen width
    val screenW = LocalConfiguration.current.screenWidthDp
    val cpl = ((screenW - (HEADER_W.value + 16f)) / CHAR_W.value).toInt().coerceIn(20, 120)

    val refChunks = refStr.chunked(cpl)
    val matchChunks = matchStr.chunked(cpl)
    val queryChunks = queryStr.chunked(cpl)

    // Pre-compute per-chunk feature segments
    val basesBeforeClip = r.refAligned.substring(0, ds).count { it != '-' }
    val chunkFeats = refChunks.mapIndexed { ci, chunk ->
        val prefixChars = refStr.substring(0, ci * cpl)
        val refBasesBefore = prefixChars.count { it != '-' }
        var refPos = r.refStartPos + basesBeforeClip + refBasesBefore

        val segs = mutableListOf<ChunkFeat>()
        var curFeat: Feature? = null
        var segStart = 0

        for (ri in chunk.indices) {
            if (chunk[ri] == '-') continue
            val featHere = doc.features.firstOrNull { f -> f.start <= refPos && f.end > refPos }
            if (featHere != curFeat) {
                if (curFeat != null) {
                    segs.add(ChunkFeat(segStart, ri, curFeat.name,
                        try { Color(android.graphics.Color.parseColor(curFeat.color)) } catch (_: Exception) { Color.Gray },
                        curFeat.strand == Strand.REVERSE))
                }
                curFeat = featHere
                segStart = ri
            }
            refPos++
        }
        if (curFeat != null) {
            segs.add(ChunkFeat(segStart, chunk.length, curFeat.name,
                try { Color(android.graphics.Color.parseColor(curFeat.color)) } catch (_: Exception) { Color.Gray },
                curFeat.strand == Strand.REVERSE))
        }
        segs
    }

    // Sizes
    val charPx = with(density) { CHAR_W.toPx() }
    val lineH = with(density) { 14.sp.toPx() }
    val headerPx = with(density) { HEADER_W.toPx() }
    val trackH = with(density) { 12.dp.toPx() }
    val monoSize = with(density) { 11.sp.toPx() }
    val labelSize = with(density) { 8.sp.toPx() }
    val headerTextSize = with(density) { 9.sp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }

    // Pre-compute dark mode, dash effect, border color for Canvas
    val preIsDark = (MaterialTheme.colorScheme.background.red * 0.299 +
        MaterialTheme.colorScheme.background.green * 0.587 +
        MaterialTheme.colorScheme.background.blue * 0.114) < 0.5f
    val preDashFx = PathEffect.dashPathEffect(floatArrayOf(3f, 2f), 0f)
    val preBorderCol = if (preIsDark) Color.White else Color.Black

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comparison", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { AlignmentSession.clear(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }},
                actions = {
                    if (!aiLoading) {
                        IconButton(onClick = {
                            if (aiResult == null) {
                                scope.launch {
                                    aiLoading = true; aiResult = ""
                                    val client = AiClient(aiUrl, aiKey, aiModel, aiLang, thinkingEnabled = aiThinking)
                                    val prompt = buildString {
                                        appendLine("I performed a local sequence alignment.")
                                        appendLine("Query: ${r.queryName} (${r.queryLen} bp) · Ref: ${r.refName} (${r.refLen} bp)")
                                        appendLine("Score: ${r.score}/${r.maxScore} ($pctFormatted%) · $strandLabel")
                                        appendLine("Matched: ${r.refStartPos + 1}-${r.refEndPos} (${r.refEndPos - r.refStartPos}bp)")
                                        if (qPrefix.isNotEmpty()) appendLine("Extra 5': ${qPrefix.length}bp")
                                        if (qSuffix.isNotEmpty()) appendLine("Extra 3': ${qSuffix.length}bp")
                                        appendLine()
                                        appendLine("Features in region:")
                                        doc.features.filter { f -> f.start < r.refEndPos && f.end > r.refStartPos }.forEach { f ->
                                            appendLine("  ${f.name} (${f.type.label}): ${f.start+1}-${f.end}, ${f.length}bp, ${f.strand.label}")
                                        }
                                        appendLine()
                                        if (aiLang == "chinese") {
                                            appendLine("分析比对结果。错配和缺失落在哪些feature上？是否影响功能？请给出建议。不要用表格。")
                                        } else {
                                            appendLine("Analyze this alignment. Where do mismatches/gaps fall relative to features? Function impact? Recommendations.")
                                        }
                                    }
                                    var buf = StringBuilder()
                                    client.askStream("You are a molecular biology expert.", prompt).collect { token ->
                                        buf.append(token); if (buf.length >= 50) { aiResult = (aiResult ?: "") + buf.toString(); buf = StringBuilder() }
                                    }
                                    aiResult = (aiResult ?: "") + buf.toString()
                                    aiLoading = false
                                }
                            }
                        }) { Icon(Icons.Default.AutoAwesome, "Ask AI") }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ══════ TOP 2/3: Info + Legend + Mutations + AI ══════
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Query", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text("${r.queryName} (${r.queryLen} bp)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ref", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text("${r.refName} (${r.refLen} bp)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                    Text("${r.score}/${r.maxScore} ($pctFormatted%) · $strandLabel", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                if (qPrefix.isNotEmpty() || qSuffix.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    val extra = buildString {
                        if (qPrefix.isNotEmpty()) append("${qPrefix.length}bp 5'")
                        if (qPrefix.isNotEmpty() && qSuffix.isNotEmpty()) append(" + ")
                        if (qSuffix.isNotEmpty()) append("${qSuffix.length}bp 3'")
                    }
                    Text("⚠ Query extends $extra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("* match", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                    Text(": strong", color = Color(0xFF2196F3), style = MaterialTheme.typography.labelSmall)
                    Text(". weak", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelSmall)
                    Text("(gap)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }

                // Mutations / Insertions / Deletions
                val misList = mutableListOf<Pair<Int, Pair<Char, Char>>>()
                val insList = mutableListOf<Int>()
                val delList = mutableListOf<Int>()
                var refPos = r.refStartPos
                var queryPos = 0
                for (i in refStr.indices) {
                    val rc = refStr[i]; val qc = queryStr[i]
                    when {
                        rc == '-' && qc !in " -" -> insList.add(queryPos)          // insertion
                        rc != '-' && qc in " -" -> delList.add(refPos)              // deletion
                        rc != '-' && qc !in " -" && rc != qc -> misList.add(refPos to Pair(rc, qc))  // mismatch
                        else -> {}
                    }
                    if (rc != '-') refPos++
                    if (qc !in " -" && qc != '-') queryPos++
                }
                val hasAny = misList.isNotEmpty() || insList.isNotEmpty() || delList.isNotEmpty()
                if (hasAny) {
                    Spacer(Modifier.height(6.dp))
                    Text("Differences: ${misList.size} mismatch · ${insList.size} insertion · ${delList.size} deletion",
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    var shown = 0
                    // Show mismatches
                    misList.take(10).forEach { (pos, bases) ->
                        val feat = doc.features.firstOrNull { f -> f.start <= pos && f.end > pos }
                        Text("  pos ${pos + 1}: ${bases.first}→${bases.second}${if (feat != null) " (${feat.name})" else ""}", style = MaterialTheme.typography.bodySmall)
                        shown++
                    }
                    // Show insertions
                    insList.take(10).forEach { pos ->
                        Text("  pos ${pos + 1}: +ins", style = MaterialTheme.typography.bodySmall)
                        shown++
                    }
                    // Show deletions
                    delList.take(10).forEach { pos ->
                        val feat = doc.features.firstOrNull { f -> f.start <= pos && f.end > pos }
                        Text("  pos ${pos + 1}: -del${if (feat != null) " (${feat.name})" else ""}", style = MaterialTheme.typography.bodySmall)
                        shown++
                    }
                    val total = misList.size + insList.size + delList.size
                    if (total > 20) Text("  ... +${total - shown} more", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // AI result
                if (aiLoading || aiResult != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("AI Analysis", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            if (aiLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else if (aiResult != null) MarkdownText(markdown = aiResult!!, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ══════ BOTTOM 1/3: Alignment Canvas ══════
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        Box(Modifier.verticalScroll(rememberScrollState())) {
                            Canvas(modifier = Modifier.width(screenW.dp - 8.dp).height(with(density) {
                                (refChunks.size * (trackH + lineH * 3 + gapPx) + ((if (qPrefix.isNotEmpty()) 1 else 0) + (if (qSuffix.isNotEmpty()) 1 else 0)) * (trackH + lineH * 3 + gapPx) + lineH).toDp()
                            })) {
                                val xSeq = headerPx + 4.dp.toPx()
                                val headerPaint = android.graphics.Paint().apply { textSize = headerTextSize; color = Color.Gray.toArgb() }

                                var y = 2.dp.toPx()

                                for (ci in refChunks.indices) {
                                    val refC = refChunks[ci]
                                    val matchC = matchChunks.getOrElse(ci) { "" }
                                    val queryC = queryChunks.getOrElse(ci) { "" }

                                    // ── Track bar ──
                                    val feats = chunkFeats[ci]
                                    feats.forEach { cf ->
                                        val w = (cf.endChar - cf.startChar).toFloat() * charPx
                                        drawRect(cf.color.copy(alpha = 0.65f), Offset(xSeq + cf.startChar * charPx, y), Size(w, trackH))
                                        if (cf.isReverse) {
                                            // Dashed border for reverse-strand features
                                            drawRect(preBorderCol, Offset(xSeq + cf.startChar * charPx - 0.5f, y - 0.5f),
                                                Size(w + 1f, trackH + 1f), style = Stroke(2.dp.toPx(), pathEffect = preDashFx))
                                        }
                                        if (cf.name.isNotBlank()) {
                                            drawContext.canvas.nativeCanvas.drawText(cf.name, xSeq + cf.startChar * charPx + 2.dp.toPx(),
                                                y + trackH - 2.dp.toPx(),
                                                android.graphics.Paint().apply { color = Color.White.toArgb(); textSize = labelSize })
                                        }
                                    }
                                    drawContext.canvas.nativeCanvas.drawText("Track", 0f, y + trackH * 0.7f, headerPaint)

                                    // ── Ref ──
                                    val refY = y + trackH + 2.dp.toPx()
                                    refC.forEachIndexed { ri, ch ->
                                        drawContext.canvas.nativeCanvas.drawText(ch.toString(), xSeq + ri * charPx, refY + lineH * 0.7f,
                                            android.graphics.Paint().apply { color = (baseColors[ch.uppercaseChar()] ?: Color.Gray).toArgb(); textSize = monoSize })
                                    }
                                    drawContext.canvas.nativeCanvas.drawText("Ref", 0f, refY + lineH * 0.7f, headerPaint)

                                    // ── Match ──
                                    val matchY = refY + lineH
                                    matchC.forEachIndexed { mi, ch ->
                                        drawContext.canvas.nativeCanvas.drawText(ch.toString(), xSeq + mi * charPx, matchY + lineH * 0.7f,
                                            android.graphics.Paint().apply { color = (matchColors[ch] ?: Color.Gray.copy(alpha = 0.4f)).toArgb(); textSize = monoSize })
                                    }
                                    drawContext.canvas.nativeCanvas.drawText("Match", 0f, matchY + lineH * 0.7f, headerPaint)

                                    // ── Query ──
                                    val queryY = matchY + lineH
                                    queryC.forEachIndexed { qi, ch ->
                                        val isMis = qi < refC.length && refC[qi] !in " -" && ch !in " -" && refC[qi] != ch
                                        val qCol = if (isMis) Color.Red else (baseColors[ch.uppercaseChar()] ?: Color.Gray)
                                        val p = android.graphics.Paint().apply { color = qCol.toArgb(); textSize = monoSize; if (isMis) isFakeBoldText = true }
                                        drawContext.canvas.nativeCanvas.drawText(ch.toString(), xSeq + qi * charPx, queryY + lineH * 0.7f, p)
                                    }
                                    drawContext.canvas.nativeCanvas.drawText("Query", 0f, queryY + lineH * 0.7f, headerPaint)

                                    y = queryY + lineH + gapPx
                                }

                                // ── Overhangs (4-line format, Track/Ref/Match blank) ──
                                if (qPrefix.isNotEmpty()) {
                                    drawContext.canvas.nativeCanvas.drawText("Track", 0f, y + trackH * 0.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Ref", 0f, y + trackH + 2.dp.toPx() + lineH * 0.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Match", 0f, y + trackH + 2.dp.toPx() + lineH * 1.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Query", 0f, y + trackH + 2.dp.toPx() + lineH * 2.7f, headerPaint)
                                    val qy = y + trackH + 2.dp.toPx() + lineH * 2.7f
                                    qPrefix.forEachIndexed { pi, ch ->
                                        drawContext.canvas.nativeCanvas.drawText(ch.toString(), xSeq + pi * charPx, qy,
                                            android.graphics.Paint().apply { color = (baseColors[ch.uppercaseChar()] ?: Color.Gray).copy(alpha = 0.5f).toArgb(); textSize = monoSize })
                                    }
                                    y += trackH + 2.dp.toPx() + 3*lineH + gapPx
                                }
                                if (qSuffix.isNotEmpty()) {
                                    drawContext.canvas.nativeCanvas.drawText("Track", 0f, y + trackH * 0.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Ref", 0f, y + trackH + 2.dp.toPx() + lineH * 0.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Match", 0f, y + trackH + 2.dp.toPx() + lineH * 1.7f, headerPaint)
                                    drawContext.canvas.nativeCanvas.drawText("Query", 0f, y + trackH + 2.dp.toPx() + lineH * 2.7f, headerPaint)
                                    val qy = y + trackH + 2.dp.toPx() + lineH * 2.7f
                                    val baseX = xSeq + refStr.count { it != '-' } * charPx
                                    qSuffix.forEachIndexed { si, ch ->
                                        drawContext.canvas.nativeCanvas.drawText(ch.toString(), baseX + si * charPx, qy,
                                            android.graphics.Paint().apply { color = (baseColors[ch.uppercaseChar()] ?: Color.Gray).copy(alpha = 0.5f).toArgb(); textSize = monoSize })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
