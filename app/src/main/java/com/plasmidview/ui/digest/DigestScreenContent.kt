package com.plasmidview.ui.digest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.digest.*
import com.plasmidview.data.digest.RestrictionSearch
import com.plasmidview.data.model.*
import com.plasmidview.ui.common.FeatureDetailDialog

// ── color palette for enzymes (rotated through) ──
private val ENZ_COLORS = listOf(
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5),
    Color(0xFF00BCD4), Color(0xFF009688), Color(0xFFFF5722),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFFCDDC39),
    Color(0xFFFF9800),
)

@Composable
fun DigestScreenContent(docIndex: Int) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    val seq = doc.sequence.replace(Regex("\\s+"), "").uppercase()
    val totalLen = doc.totalLength

    // State
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiLang by prefs.aiLang.collectAsState(initial = "english")
    val aiThinking by prefs.aiThinking.collectAsState(initial = false)
    var enzymeNameList by remember { mutableStateOf<List<RestrictionSearch.EnzymeEntry>>(emptyList()) }
    var enzymeDataJson by remember { mutableStateOf<String?>(null) }
    var selectedEnzymes by remember { mutableStateOf(setOf<String>()) }
    var searchQ by remember { mutableStateOf("") }
    var digestResult by remember { mutableStateOf<DigestResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    var showSelectDialog by remember { mutableStateOf(false) }
    var showAutoPickDialog by remember { mutableStateOf(false) }
    var selFeat by remember { mutableStateOf<Feature?>(null) }
    var showFeatDlg by remember { mutableStateOf(false) }

    // Color assignment for selected enzymes
    val enzColors = remember(selectedEnzymes) {
        selectedEnzymes.sorted().mapIndexed { i, name ->
            name to ENZ_COLORS[i % ENZ_COLORS.size]
        }.toMap()
    }

    // Filtered list
    val filtered = remember(enzymeNameList, searchQ) {
        enzymeNameList.filter { it.name !in selectedEnzymes }.let { list ->
            if (searchQ.isBlank()) list else list.filter {
                it.name.contains(searchQ, ignoreCase = true)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Upper area: circular map ──
        Box(Modifier.weight(2f).fillMaxWidth().padding(8.dp)) {
            DigestMap(
                doc = doc,
                digestResult = digestResult,
                enzColors = enzColors,
                onFeatureTap = { f -> selFeat = f; showFeatDlg = true }
            )
        }

        // ── Button row ──
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { showSelectDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) { Icon(Icons.Default.ContentCut, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Select") }

            FilledTonalButton(
                onClick = { showAutoPickDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) { Text("AutoPick") }
        }

        // Selected enzyme tags
        if (selectedEnzymes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedEnzymes.sorted().forEach { name ->
                        SuggestionChip(
                            onClick = {
                                selectedEnzymes = selectedEnzymes - name
                                digestResult = null  // Recalc needed
                            },
                            label = { Text(name, fontSize = 11.sp) },
                            icon = { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // ── Lower area: results ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    Text(loadingText, modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                digestResult != null -> DigestResultPanel(digestResult!!, enzColors, totalLen)
                else -> Text("Select enzymes or use AutoPick to analyze restriction sites.",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // ── Feature detail dialog ──
    if (showFeatDlg && selFeat != null) {
        FeatureDetailDialog(selFeat!!, doc, onDismiss = { showFeatDlg = false; selFeat = null },
            aiUrl = aiUrl, aiKey = aiKey, aiModel = aiModel, aiLang = aiLang, aiThinking = aiThinking)
    }

    // ── Select Enzyme dialog ──
    if (showSelectDialog) {
        SelectEnzymeDialog(
            enzymeNames = enzymeNameList,
            selected = selectedEnzymes,
            filtered = filtered,
            onSearchChange = { searchQ = it },
            onToggle = { name ->
                selectedEnzymes = if (name in selectedEnzymes) selectedEnzymes - name else selectedEnzymes + name
                digestResult = null
            },
            onConfirm = {
                showSelectDialog = false
                if (selectedEnzymes.isNotEmpty() && seq.isNotEmpty()) {
                    isLoading = true; loadingText = "Calculating digest..."
                }
            },
            onDismiss = { showSelectDialog = false }
        )
    }

    // ── AutoPick dialog ──
    if (showAutoPickDialog) {
        AutoPickDialog(
            enzymeNameList = enzymeNameList,
            sequence = seq,
            onPick = { candidate ->
                showAutoPickDialog = false
                selectedEnzymes = candidate.enzymes.toSet()
                digestResult = null
                isLoading = true; loadingText = "Calculating digest..."
            },
            onDismiss = { showAutoPickDialog = false }
        )
    }

    // Load enzyme name list from assets once on first dialog open
    LaunchedEffect(showSelectDialog, showAutoPickDialog) {
        if ((showSelectDialog || showAutoPickDialog) && enzymeNameList.isEmpty()) {
            enzymeNameList = withContext(Dispatchers.IO) {
                RestrictionSearch.loadEnzymeData(ctx)
            }
        }
    }

    // Calculate digest when triggered
    LaunchedEffect(isLoading) {
        if (seq.isEmpty() || !isLoading) return@LaunchedEffect
        // Don't trigger until loadingText is set
        if (loadingText.startsWith("Calculating")) {
            val result = withContext(Dispatchers.IO) {
                RestrictionSearch.calculateDigest(seq, selectedEnzymes.toList(), enzymeNameList)
            }
            digestResult = result
            isLoading = false
        }
    }
}

// ── Circular plasmid map with features + cut markers ──

private const val ARC_W_DP = 12f
private const val CUT_W_DP = 3f
private val ARROW_LEN = 7.dp
private val LANE_GAP = 4.dp

@Composable
private fun DigestMap(
    doc: com.plasmidview.data.model.PlasmidDocument,
    digestResult: DigestResult?,
    enzColors: Map<String, Color>,
    onFeatureTap: (Feature) -> Unit
) {
    val dens = LocalDensity.current
    val tickCount = 8
    val scaleS = remember { mutableFloatStateOf(1f) }
    val oxS = remember { mutableFloatStateOf(0f) }
    val oyS = remember { mutableFloatStateOf(0f) }
    val tickTextArgb = MaterialTheme.colorScheme.onBackground.toArgb()

    val laneMap = remember(doc) { assignLanes(doc.features, doc.totalLength) }
    val maxLane = laneMap.values.maxOrNull() ?: 0

    val aw = with(dens) { ARC_W_DP.dp.toPx() }
    val step = aw + with(dens) { LANE_GAP.toPx() }
    val tickOuter = maxLane / 2
    val tickOffset = tickOuter * step

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scaleS.floatValue = (scaleS.floatValue * zoom).coerceIn(0.3f, 5f)
                oxS.floatValue += pan.x
                oyS.floatValue += pan.y
            }
        }
        .pointerInput(doc, laneMap) {
            detectTapGestures { tap ->
                val s = scaleS.floatValue
                val ox = oxS.floatValue; val oy = oyS.floatValue
                val cs = size; val cx = cs.width / 2f; val cy = cs.height / 2f
                val awPx0 = ARC_W_DP.dp.toPx()
                val step0 = awPx0 + LANE_GAP.toPx()
                val tickLen0 = 8.dp.toPx(); val labelGap0 = 2.dp.toPx(); val pad0 = 8.dp.toPx()
                val innerMin = ((maxLane + 1) / 2) * step0 + awPx0 / 2f + 24.dp.toPx()
                val lPaint = android.graphics.Paint().apply { textSize = 9.sp.toPx() }
                val labelHalfW = (0 until tickCount).maxOf {
                    lPaint.measureText("${it * doc.totalLength / tickCount}bp")
                } / 2f
                val crBase = fitBaseRadius(
                    cs.width.toFloat(), cs.height.toFloat(), maxLane, step0, awPx0 / 2f,
                    tickLen0, labelGap0, labelHalfW, pad0, innerMin
                ) * s
                val awPx = awPx0 * s
                val stepPx = step0 * s
                val dx = tap.x - cx - ox; val dy = tap.y - cy - oy
                val d = sqrt(dx * dx + dy * dy)

                val hitLane = laneMap.values.distinct().firstOrNull { k ->
                    val lr = laneRadius(k, crBase, stepPx)
                    abs(d - lr) <= awPx / 2f + 6.dp.toPx()
                } ?: return@detectTapGestures

                var a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                a = ((a + 90) % 360 + 360) % 360
                val pos = (a / 360f * doc.totalLength).toInt().coerceIn(0, doc.totalLength)

                doc.features.firstOrNull { f ->
                    (laneMap[f] ?: 0) == hitLane &&
                    if (f.start < f.end) pos in f.start..f.end
                    else pos >= f.start || pos <= f.end
                }?.let(onFeatureTap)
            }
        }
    ) {
        val w = size.width; val h = size.height
        val cx = w / 2f; val cy = h / 2f
        val hw = aw / 2f
        val cutW = CUT_W_DP.dp.toPx()
        val tickLen = 8.dp.toPx(); val labelGap = 2.dp.toPx(); val pad = 8.dp.toPx()
        val innerMin = ((maxLane + 1) / 2) * step + hw + 24.dp.toPx()
        val lPaint = android.graphics.Paint().apply { textSize = 9.sp.toPx() }
        val labelHalfW = (0 until tickCount).maxOf {
            lPaint.measureText("${it * doc.totalLength / tickCount}bp")
        } / 2f
        val crBase = fitBaseRadius(
            w, h, maxLane, step, hw,
            tickLen, labelGap, labelHalfW, pad, innerMin
        )
        val tickR0 = crBase + tickOuter * step + hw
        val aLen = ARROW_LEN.toPx()
        val total = doc.totalLength.toFloat()

        withTransform({
            translate(oxS.floatValue, oyS.floatValue)
            scale(scaleS.floatValue, scaleS.floatValue, Offset(cx, cy))
        }) {
            // ── Main backbone (thin circle, matching Map screen) ──
            drawCircle(color = Color.Gray.copy(alpha = 0.25f), radius = crBase,
                center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))

            // ── Feature arcs (lane-aware) ──
            doc.features.forEach { f ->
                val fcr = laneRadius(laneMap[f] ?: 0, crBase, step)
                if (f.start >= f.end) {
                    drawFeatureArc(f, 0, f.end, total, cx, cy, fcr, aw, hw, aLen)
                    drawFeatureArc(f, f.start, doc.totalLength, total, cx, cy, fcr, aw, hw, aLen)
                    return@forEach
                }
                drawFeatureArc(f, f.start, f.end, total, cx, cy, fcr, aw, hw, aLen)
            }

            // ── Cut site markers (on backbone lane 0) ──
            val innerR = crBase - hw
            val outerR = crBase + hw
            if (digestResult != null) {
                for ((enzName, cuts) in digestResult.cutsByEnzyme) {
                    val color = enzColors[enzName] ?: Color.Gray
                    for (pos in cuts) {
                        val angle = (pos.toFloat() / total) * 360f - 90f
                        val rad = angle * PI / 180.0
                        val x1 = cx + (innerR * cos(rad)).toFloat()
                        val y1 = cy + (innerR * sin(rad)).toFloat()
                        val x2 = cx + (outerR * cos(rad)).toFloat()
                        val y2 = cy + (outerR * sin(rad)).toFloat()
                        drawLine(color, Offset(x1, y1), Offset(x2, y2),
                            strokeWidth = cutW, cap = StrokeCap.Round)
                        val labelR = outerR + 12.dp.toPx()
                        val lx = cx + (labelR * cos(rad)).toFloat()
                        val ly = cy + (labelR * sin(rad)).toFloat()
                        drawContext.canvas.nativeCanvas.drawText("$enzName @${pos+1}",
                            lx, ly,
                            android.graphics.Paint().apply {
                                setColor(color.toArgb())
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                            })
                    }
                }
            }

            // ── Tick marks (pre-shifted past outer lanes) ──
            for (i in 0 until tickCount) {
                val a = (i.toFloat() / tickCount) * 360f - 90f
                val rad = Math.toRadians(a.toDouble())
                val r1 = tickR0 + 2.dp.toPx()
                val r2 = r1 + tickLen
                drawLine(Color.Gray.copy(alpha = 0.5f),
                    Offset(cx + (r1 * cos(rad)).toFloat(), cy + (r1 * sin(rad)).toFloat()),
                    Offset(cx + (r2 * cos(rad)).toFloat(), cy + (r2 * sin(rad)).toFloat()),
                    strokeWidth = 1.5.dp.toPx())
            }
        }

        // ── Tick labels (outside transform, constant size) ──
        for (i in 0 until tickCount) {
            val sc = scaleS.floatValue
            val a = (i.toFloat() / tickCount) * 360f - 90f
            val rad = Math.toRadians(a.toDouble())
            val rTick = (tickR0 + 2.dp.toPx() + tickLen + labelGap + labelHalfW) * sc
            val lx = cx + oxS.floatValue + (rTick * cos(rad)).toFloat()
            val ly = cy + oyS.floatValue + (rTick * sin(rad)).toFloat()
            val bp = (i * doc.totalLength / tickCount)
            drawContext.canvas.nativeCanvas.drawText("${bp}bp", lx, ly,
                android.graphics.Paint().apply {
                    color = tickTextArgb
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                })
        }
    }
        IconButton(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            onClick = { scaleS.floatValue = 1f; oxS.floatValue = 0f; oyS.floatValue = 0f }
        ) {
            Icon(Icons.Default.Home, contentDescription = "Reset view")
        }
    }
}

// ── Result panel ──

@Composable
private fun DigestResultPanel(
    result: DigestResult,
    enzColors: Map<String, Color>,
    totalLen: Int
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("${result.fragmentCount} fragments",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        // Enzyme legend
        result.cutsByEnzyme.forEach { (name, cuts) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(enzColors[name] ?: Color.Gray, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(6.dp))
                Text("$name @ ${cuts.joinToString(", ") { "${it + 1}bp" }}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()

        // Fragment list
        result.fragments.forEachIndexed { idx, f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fragment ${idx + 1}:", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium)
                Text("${f.start + 1} - ${f.end}bp (${f.length}bp)",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ── Single-path feature arc (shared) ──

private fun DrawScope.drawFeatureArc(
    f: Feature, segStart: Int, segEnd: Int, len: Float,
    cx: Float, cy: Float, cr: Float, aw: Float, hw: Float, aLen: Float
) {
    val col = try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }
    val sa = (segStart / len) * 360f - 90f
    val sw = (segEnd - segStart) / len * 360f
    if (sw <= 0f) return

    if (f.strand == Strand.NONE) {
        drawArc(col, sa, sw, false, Offset(cx - cr, cy - cr), Size(cr * 2, cr * 2),
            style = Stroke(width = aw))
        return
    }

    val rev = f.strand == Strand.REVERSE
    val a0 = sa
    val a1 = sa + sw
    val arrowDeg = ((aLen / cr) * (180.0 / PI)).toFloat().coerceAtMost(sw * 0.5f)
    val baseDeg = if (rev) a0 + arrowDeg else a1 - arrowDeg
    val bandSw = sw - arrowDeg
    val ro = cr + hw
    val ri = (cr - hw).coerceAtLeast(1f)

    fun pt(r: Float, deg: Float): Offset {
        val rad = Math.toRadians(deg.toDouble())
        return Offset(
            (cx + r * cos(rad)).toFloat(),
            (cy + r * sin(rad)).toFloat()
        )
    }

    val outerRect = Rect(cx - ro, cy - ro, cx + ro, cy + ro)
    val innerRect = Rect(cx - ri, cy - ri, cx + ri, cy + ri)

    val path = Path().apply {
        if (!rev) {
            arcTo(outerRect, a0, bandSw, forceMoveTo = true)
            lineTo(pt(cr, a1).x, pt(cr, a1).y)
            lineTo(pt(ri, baseDeg).x, pt(ri, baseDeg).y)
            arcTo(innerRect, baseDeg, -bandSw, forceMoveTo = false)
            close()
        } else {
            moveTo(pt(ro, baseDeg).x, pt(ro, baseDeg).y)
            arcTo(outerRect, baseDeg, bandSw, forceMoveTo = false)
            lineTo(pt(ri, a1).x, pt(ri, a1).y)
            arcTo(innerRect, a1, -bandSw, forceMoveTo = false)
            lineTo(pt(cr, a0).x, pt(cr, a0).y)
            close()
        }
    }
    drawPath(path, col)
}

private data class Iv(val f: Feature, val s: Int, val e: Int)

private fun assignLanes(features: List<Feature>, total: Int): Map<Feature, Int> {
    val ivs = features.flatMap { f ->
        if (f.start < f.end) listOf(Iv(f, f.start, f.end))
        else listOf(Iv(f, f.start, total), Iv(f, 0, f.end))
    }.sortedBy { it.s }
    val laneEnd = mutableListOf<Int>()
    val result = mutableMapOf<Feature, Int>()
    for (iv in ivs) {
        if (iv.f in result) continue
        val lane = laneEnd.indexOfFirst { it <= iv.s }
        if (lane == -1) {
            result[iv.f] = laneEnd.size
            laneEnd.add(iv.e)
        } else {
            result[iv.f] = lane
            laneEnd[lane] = maxOf(laneEnd[lane], iv.e)
        }
    }
    return result
}

private fun laneRadius(lane: Int, crBase: Float, step: Float): Float = when {
    lane == 0 -> crBase
    lane % 2 == 1 -> crBase - ((lane + 1) / 2) * step
    else -> crBase + (lane / 2) * step
}

private fun fitBaseRadius(
    w: Float, h: Float, maxLane: Int, step: Float, hw: Float,
    tickLen: Float, labelGap: Float, labelHalfW: Float, pad: Float, innerMin: Float
): Float {
    val reserved = (maxLane / 2) * step + hw + tickLen + labelGap + labelHalfW + pad
    return (min(w, h) / 2f - reserved).coerceAtLeast(innerMin)
}

// ── Select Enzyme Dialog ──

@Composable
private fun SelectEnzymeDialog(
    enzymeNames: List<RestrictionSearch.EnzymeEntry>,
    selected: Set<String>,
    filtered: List<RestrictionSearch.EnzymeEntry>,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQ by remember { mutableStateOf("") }
    val filteredList = remember(enzymeNames, selected, searchQ) {
        enzymeNames.filter { it.name !in selected }.let { list ->
            if (searchQ.isBlank()) list else list.filter {
                it.name.contains(searchQ, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Enzymes", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Search field
                OutlinedTextField(value = searchQ, onValueChange = { onSearchChange(it); searchQ = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search enzyme...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    trailingIcon = if (searchQ.isNotBlank()) {
                        { IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Default.Close, "Clear") } }
                    } else null
                )
                Spacer(Modifier.height(4.dp))

                // Selected tags
                val sortedSelected = selected.sorted()
                if (sortedSelected.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        sortedSelected.forEach { name ->
                            SuggestionChip(
                                onClick = { onToggle(name) },
                                label = { Text(name, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                }

                // Enzyme list (loaded from pre-computed assets)
                Text("${enzymeNames.size} enzymes available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filteredList) { enz ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(enz.name) }.padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = enz.name in selected, onCheckedChange = { onToggle(enz.name) })
                            Column(Modifier.weight(1f)) {
                                Text(enz.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${enz.site} · ${enz.overhang}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = onConfirm, enabled = selected.isNotEmpty()) { Text("Digest (${selected.size})") }
            }
        }
    )
}

// ── AutoPick Dialog ──

@Composable
private fun AutoPickDialog(
    enzymeNameList: List<RestrictionSearch.EnzymeEntry>,
    sequence: String,
    onPick: (AutoPickCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf("single") }
    var targetCount by remember { mutableStateOf("2") }
    var minBp by remember { mutableStateOf("") }
    var maxBp by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<AutoPickCandidate>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var searchTrigger by remember { mutableIntStateOf(0) }

    // Run auto-pick on background thread when triggered
    LaunchedEffect(searchTrigger) {
        if (searchTrigger > 0 && sequence.isNotEmpty()) {
            isLoading = true
            val r = withContext(Dispatchers.IO) {
                RestrictionSearch.autoPick(
                    sequence, mode, targetCount.toIntOrNull() ?: 2,
                    minBp.toIntOrNull() ?: 0, maxBp.toIntOrNull() ?: 0, enzymeNameList
                )
            }
            candidates = r
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AutoPick Enzymes", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Common enzyme tags
                Text("Search in these common enzymes:", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.plasmidview.data.digest.COMMON_ENZYMES.forEach { name ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(name, fontSize = 10.sp) },
                            modifier = Modifier.padding(0.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Mode
                Text("Digest type:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "single", onClick = { mode = "single" },
                        label = { Text("Single") })
                    FilterChip(selected = mode == "double", onClick = { mode = "double" },
                        label = { Text("Double") })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = targetCount, onValueChange = { targetCount = it },
                    label = { Text("Target fragment count") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = minBp, onValueChange = { minBp = it },
                        label = { Text("Min bp (optional)") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxBp, onValueChange = { maxBp = it },
                        label = { Text("Max bp (optional)") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                }

                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Searching...", style = MaterialTheme.typography.bodySmall)
                }

                // Results
                if (candidates != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("Top ${candidates!!.size} results:", style = MaterialTheme.typography.labelMedium)
                    if (candidates!!.isEmpty()) {
                        Text("No matching enzymes found.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(candidates!!) { c ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onPick(c) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(c.enzymes.joinToString(" + "), fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("${c.fragmentCount} fragments · sizes: ${c.fragmentLengths.joinToString(", ")}bp",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        searchTrigger++
                        candidates = null
                    },
                    enabled = !isLoading && candidates == null
                ) { Text(if (isLoading) "Searching..." else "Search") }
            }
        }
    )
}
