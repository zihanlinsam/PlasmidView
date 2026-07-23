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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.digest.*
import com.plasmidview.data.digest.RestrictionSearch
import com.plasmidview.data.model.*

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
        val f = selFeat!!
        AlertDialog(onDismissRequest = { showFeatDlg = false; selFeat = null },
            title = { Text(f.name.ifBlank { f.type.label }, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Type: ${f.type.label}"); Text("Position: ${f.start + 1} - ${f.end}")
                    Text("Length: ${f.length} bp"); Text("Strand: ${f.strand.label}")
                }
            },
            confirmButton = { TextButton(onClick = { showFeatDlg = false; selFeat = null }) { Text("Close") } }
        )
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
private const val CUT_W_DP = 3f  // 3x arc width

@Composable
private fun DigestMap(
    doc: com.plasmidview.data.model.PlasmidDocument,
    digestResult: DigestResult?,
    enzColors: Map<String, Color>,
    onFeatureTap: (Feature) -> Unit
) {
    val isDark = (MaterialTheme.colorScheme.background.red * 0.299 +
            MaterialTheme.colorScheme.background.green * 0.587 +
            MaterialTheme.colorScheme.background.blue * 0.114) < 0.5f
    val dens = LocalDensity.current
    val tickCount = 8
    val tickN = 8
    val scaleS = remember { mutableFloatStateOf(1f) }
    val oxS = remember { mutableFloatStateOf(0f) }
    val oyS = remember { mutableFloatStateOf(0f) }

    Canvas(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { c, pan, zoom, _ ->
                scaleS.floatValue = (scaleS.floatValue * zoom).coerceIn(0.5f, 5f)
                oxS.floatValue += pan.x
                oyS.floatValue += pan.y
            }
        }
        .pointerInput(doc.features) {
            detectTapGestures { tap ->
                val scale = scaleS.floatValue
                val ox = oxS.floatValue
                val oy = oyS.floatValue
                val w = size.width.toFloat(); val h = size.height.toFloat()
                val cx = w / 2; val cy = h / 2
                // Invert transform to get logical coordinates
                val invX = (tap.x - ox - cx) / scale + cx
                val invY = (tap.y - oy - cy) / scale + cy
                val radius = min(w, h) / 2 * 0.42f
                val innerR = radius - ARC_W_DP.dp.toPx() / 2
                val outerR = radius + ARC_W_DP.dp.toPx() / 2
                val dx = invX - cx; val dy = invY - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist < innerR || dist > outerR) return@detectTapGestures
                // Match drawArc angle convention: 0° = 3-o'clock, +90° offset in draw
                // atan2 gives 0° at 3-o'clock; add 90° to align with drawArc's -90
                var angle = kotlin.math.atan2(dy, dx) + kotlin.math.PI / 2
                if (angle < 0) angle += 2 * kotlin.math.PI
                val pos = ((angle / (2 * kotlin.math.PI)) * doc.totalLength).toInt().coerceIn(0, doc.totalLength - 1)
                doc.features.firstOrNull { f -> f.start <= pos && pos <= f.end }?.let(onFeatureTap)
            }
        }
    ) {
        // Apply zoom/pan transform to all drawing
        drawContext.transform.run {
            translate(oxS.floatValue, oyS.floatValue)
            scale(scaleS.floatValue, scaleS.floatValue, androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2))
        }
        val w = size.width; val h = size.height
        val cx = w / 2; val cy = h / 2
        val radius = min(w, h) / 2 * 0.42f
        val arcW = with(dens) { ARC_W_DP.dp.toPx() }
        val cutW = with(dens) { CUT_W_DP.dp.toPx() }
        val innerR = radius - arcW / 2
        val outerR = radius + arcW / 2
        val total = doc.totalLength.toFloat()

        // ── Outer ring (backbone) ──
        drawArc(Color.Gray.copy(alpha = 0.3f), 0f, 360f, false,
            Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
            style = Stroke(arcW))

        // ── Feature arcs ──
        val isDarkCanvas = isDark
        doc.features.forEach { f ->
            val s = f.start.toFloat(); val e = f.end.toFloat()
            // No border/none strand → no border
            val applyBorder = f.strand != Strand.NONE
            val dashed = f.strand == Strand.REVERSE
            val col = try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }

            // Feature arc (1dp inset from ring edges)
            val fArcW = arcW - 2.dp.toPx()
            val fInnerR = radius - fArcW / 2
            val sAngle = (s / total) * 360f - 90f
            val sweep = ((e - s) / total) * 360f
            drawArc(col.copy(alpha = 0.65f), sAngle, sweep, false,
                Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
                style = Stroke(fArcW))

            // Border
            if (applyBorder && sweep > 0.5f) {
                val bCol = if (isDarkCanvas) Color.White else Color.Black
                val bArcW = 2.dp.toPx()
                val eff = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
                drawArc(bCol, sAngle - 0.3f, sweep + 0.6f, false,
                    Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2),
                    style = Stroke(bArcW, pathEffect = eff))
            }
        }

        // ── Cut site markers ──
        if (digestResult != null) {
            for ((enzName, cuts) in digestResult.cutsByEnzyme) {
                val color = enzColors[enzName] ?: Color.Gray
                for (pos in cuts) {
                    val angle = (pos.toFloat() / total) * 360f - 90f
                    val rad = angle * Math.PI / 180.0
                    // Line from innerR to outerR at angle
                    val x1 = cx + (innerR * kotlin.math.cos(rad)).toFloat()
                    val y1 = cy + (innerR * kotlin.math.sin(rad)).toFloat()
                    val x2 = cx + (outerR * kotlin.math.cos(rad)).toFloat()
                    val y2 = cy + (outerR * kotlin.math.sin(rad)).toFloat()
                    // Draw as a thick line
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = cutW,
                        cap = StrokeCap.Round)
                    // Label
                    val labelR = outerR + 12.dp.toPx()
                    val lx = cx + (labelR * kotlin.math.cos(rad)).toFloat()
                    val ly = cy + (labelR * kotlin.math.sin(rad)).toFloat()
                    drawContext.canvas.nativeCanvas.drawText("$enzName @${pos+1}",
                        lx, ly,
                        android.graphics.Paint().apply {
                            setColor(color.hashCode())
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        })
                }
            }
        }

        // ── Tick marks ──
        for (i in 0 until tickCount) {
            val a = (i.toFloat() / tickCount) * 360f - 90f
            val rad = Math.toRadians(a.toDouble())
            val tickR1 = radius + arcW / 2 + 3.dp.toPx()
            val tickR2 = tickR1 + 6.dp.toPx()
            val x1 = cx + (tickR1 * kotlin.math.cos(rad)).toFloat()
            val y1 = cy + (tickR1 * kotlin.math.sin(rad)).toFloat()
            val x2 = cx + (tickR2 * kotlin.math.cos(rad)).toFloat()
            val y2 = cy + (tickR2 * kotlin.math.sin(rad)).toFloat()
            drawLine(Color.Gray.copy(alpha = 0.5f), Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.5.dp.toPx())
            // Label
            val labelR = tickR2 + 8.dp.toPx()
            val lx = cx + (labelR * kotlin.math.cos(rad)).toFloat()
            val ly = cy + (labelR * kotlin.math.sin(rad)).toFloat()
            val bp = (i * doc.totalLength / tickCount)
            drawContext.canvas.nativeCanvas.drawText("${bp}bp", lx, ly,
                android.graphics.Paint().apply {
                    color = Color.Gray.copy(alpha = 0.6f).hashCode()
                    textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                })
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

    // Run auto-pick on background thread when triggered
    LaunchedEffect(isLoading) {
        if (isLoading && sequence.isNotEmpty()) {
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
                                    shape = RoundedCornerShape(8.dp),
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
                Button(onClick = {
                    isLoading = true
                    candidates = null
                    // LaunchedEffect will run the calculation
                }) { Text("Search") }
            }
        }
    )
}
