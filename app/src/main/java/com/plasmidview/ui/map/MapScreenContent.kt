package com.plasmidview.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.*
import com.plasmidview.ui.common.FeatureDetailDialog
import kotlin.math.*

private const val ARC_W_DP = 12f
private const val TICK_N = 8
private val ARROW_LEN = 7.dp      // flatter arrow (was 14dp)
private val LANE_GAP = 4.dp       // gap between concentric lanes

@Composable
fun MapScreenContent(docIndex: Int, fontSize: Float = 12f) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var sf by remember { mutableStateOf<Feature?>(null) }
    var sd by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiLang by prefs.aiLang.collectAsState(initial = "english")
    val aiThinking by prefs.aiThinking.collectAsState(initial = false)

    val scaleS = remember(docIndex) { mutableFloatStateOf(1f) }
    val oxS = remember(docIndex) { mutableFloatStateOf(0f) }
    val oyS = remember(docIndex) { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        Text("${doc.features.size} features · ${doc.totalLength} bp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            CircCanvas(doc, scaleS, oxS, oyS, fontSize) { f -> sf = f; sd = true }
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                onClick = { scaleS.floatValue = 1f; oxS.floatValue = 0f; oyS.floatValue = 0f }
            ) {
                Icon(Icons.Default.Home, contentDescription = "Reset view")
            }
        }
    }
    if (sd && sf != null) FeatureDetailDialog(sf!!, doc, onDismiss = { sd = false; sf = null },
        aiUrl = aiUrl, aiKey = aiKey, aiModel = aiModel, aiLang = aiLang, aiThinking = aiThinking)
}

@Composable
private fun CircCanvas(
    doc: PlasmidDocument, scaleS: MutableFloatState, oxS: MutableFloatState, oyS: MutableFloatState,
    fontSize: Float = 12f, onClick: (Feature) -> Unit
) {
    val len = doc.totalLength.toFloat(); if (len <= 0f) return
    val oc = MaterialTheme.colorScheme.outline
    val tickTextArgb = MaterialTheme.colorScheme.onBackground.toArgb()

    // Lane assignment — computed once per document
    val laneMap = remember(doc) { assignLanes(doc.features, doc.totalLength) }
    val maxLane = laneMap.values.maxOrNull() ?: 0

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
                val cs = size; val cx = cs.width / 2f; val cy = cs.height / 2f
                val awPx0 = ARC_W_DP.dp.toPx()
                val step0 = awPx0 + LANE_GAP.toPx()
                val tickLen0 = 10.dp.toPx(); val labelGap0 = 6.dp.toPx(); val pad0 = 8.dp.toPx()
                val innerMin = ((maxLane + 1) / 2) * step0 + awPx0 / 2f + 24.dp.toPx()
                val lPaint = android.graphics.Paint().apply { textSize = fontSize.sp.toPx() }
                val labelHalfW = (0 until TICK_N).maxOf {
                    lPaint.measureText("${it * doc.totalLength / TICK_N}bp")
                } / 2f
                val crBase = fitBaseRadius(
                    cs.width.toFloat(), cs.height.toFloat(), maxLane, step0, awPx0 / 2f,
                    tickLen0, labelGap0, labelHalfW, pad0, innerMin
                ) * s
                val awPx = awPx0 * s
                val step = step0 * s
                val dx = tap.x - cx - oxS.floatValue
                val dy = tap.y - cy - oyS.floatValue
                val d = sqrt(dx * dx + dy * dy)

                // Determine which lane ring was tapped — nearest lane with tight tolerance
                val hitLane = laneMap.values.distinct()
                    .map { k -> k to abs(d - laneRadius(k, crBase, step)) }
                    .filter { (_, dist) -> dist <= awPx / 2f + 2.dp.toPx() }
                    .minByOrNull { (_, dist) -> dist }?.first
                    ?: return@detectTapGestures

                var a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                a = ((a + 90) % 360 + 360) % 360
                val pos = (a / 360f * len).toInt().coerceIn(0, doc.totalLength)

                // Match feature in that lane; handle cross-origin correctly
                doc.features.firstOrNull { f ->
                    (laneMap[f] ?: 0) == hitLane &&
                    if (f.start < f.end) pos in f.start..f.end
                    else pos >= f.start || pos <= f.end
                }?.let(onClick)
            }
        }
    ) {
        val cs = size; val cx = cs.width / 2f; val cy = cs.height / 2f
        val aw = ARC_W_DP.dp.toPx()
        val hw = aw / 2f
        val aLen = ARROW_LEN.toPx()
        val step = aw + LANE_GAP.toPx()

        val tickOuter = maxLane / 2
        val tickLen = 8.dp.toPx(); val labelGap = 2.dp.toPx(); val pad = 8.dp.toPx()
        val innerMin = ((maxLane + 1) / 2) * step + hw + 24.dp.toPx()
        val lPaint = android.graphics.Paint().apply { textSize = fontSize.sp.toPx() }
        val labelHalfW = (0 until TICK_N).maxOf {
            lPaint.measureText("${it * doc.totalLength / TICK_N}bp")
        } / 2f
        val crBase = fitBaseRadius(
            cs.width, cs.height, maxLane, step, hw,
            tickLen, labelGap, labelHalfW, pad, innerMin
        )
        val tickR0 = crBase + tickOuter * step + hw

        withTransform({
            translate(oxS.floatValue, oyS.floatValue)
            scale(scaleS.floatValue, scaleS.floatValue, Offset(cx, cy))
        }) {
            // Backbone at lane 0
            drawCircle(color = oc.copy(alpha = 0.25f), radius = crBase,
                center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))

            // Features — each on its own lane radius
            doc.features.forEach { f ->
                val fcr = laneRadius(laneMap[f] ?: 0, crBase, step)
                if (f.start >= f.end) {
                    drawFeatureArc(f, 0, f.end, len, cx, cy, fcr, aw, hw, aLen)
                    drawFeatureArc(f, f.start, doc.totalLength, len, cx, cy, fcr, aw, hw, aLen)
                    return@forEach
                }
                drawFeatureArc(f, f.start, f.end, len, cx, cy, fcr, aw, hw, aLen)
            }

            // Tick marks — shifted outward past the outermost lane
            for (i in 0 until TICK_N) {
                val a = (i.toFloat() / TICK_N) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val r1 = tickR0 + 2.dp.toPx()
                val r2 = r1 + tickLen
                drawLine(oc, Offset(cx + r1 * cos(a), cy + r1 * sin(a)),
                    Offset(cx + r2 * cos(a), cy + r2 * sin(a)), 1.5.dp.toPx())
            }
        }

        // Tick labels outside transform — also shifted
        for (i in 0 until TICK_N) {
            val sc = scaleS.floatValue
            val a = (i.toFloat() / TICK_N) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val rTick = (tickR0 + 2.dp.toPx() + tickLen + labelGap + labelHalfW) * sc
            val lx = cx + oxS.floatValue + rTick * cos(a)
            val ly = cy + oyS.floatValue + rTick * sin(a)
            drawContext.canvas.nativeCanvas.drawText(
                "${(i * doc.totalLength / TICK_N)}bp",
                lx, ly + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = tickTextArgb
                    textSize = fontSize.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

/** Draw one feature as a single filled path: band + arrow tip at one end. */
private fun DrawScope.drawFeatureArc(
    f: Feature, segStart: Int, segEnd: Int, len: Float,
    cx: Float, cy: Float, cr: Float, aw: Float, hw: Float, aLen: Float
) {
    val col = try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }
    val sa = (segStart / len) * 360f - 90f
    val sw = (segEnd - segStart) / len * 360f
    if (sw <= 0f) return

    // No-direction feature: just a stroke arc
    if (f.strand == Strand.NONE) {
        drawArc(col, sa, sw, false, Offset(cx - cr, cy - cr), Size(cr * 2, cr * 2),
            style = Stroke(width = aw))
        return
    }

    val rev = f.strand == Strand.REVERSE
    val a0 = sa
    val a1 = sa + sw

    // Arrow occupies angle aLen / cr, capped at half the sweep
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
            // Forward: tip at clockwise end a1
            arcTo(outerRect, a0, bandSw, forceMoveTo = true)
            lineTo(pt(cr, a1).x, pt(cr, a1).y)
            lineTo(pt(ri, baseDeg).x, pt(ri, baseDeg).y)
            arcTo(innerRect, baseDeg, -bandSw, forceMoveTo = false)
            close()
        } else {
            // Reverse: tip at counter-clockwise start a0
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

// ── Lane assignment ────────────────────────────────────────────

private data class Iv(val f: Feature, val s: Int, val e: Int)

/** Greedy interval-colouring. Splits cross-origin features into two
 *  virtual intervals but assigns only one lane. */
private fun assignLanes(features: List<Feature>, total: Int): Map<Feature, Int> {
    val ivs = features.flatMap { f ->
        if (f.start < f.end) listOf(Iv(f, f.start, f.end))
        else listOf(Iv(f, f.start, total), Iv(f, 0, f.end))
    }.sortedBy { it.s }

    val laneEnd = mutableListOf<Int>()
    val result = mutableMapOf<Feature, Int>()
    for (iv in ivs) {
        if (iv.f in result) continue     // second virtual segment → reuse assigned lane
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

/** Lane → radius.  Lane 0 = main ring.  Odd lanes shrink inward,
 *  even lanes grow outward, alternating. */
private fun laneRadius(lane: Int, crBase: Float, step: Float): Float = when {
    lane == 0 -> crBase
    lane % 2 == 1 -> crBase - ((lane + 1) / 2) * step    // 1→inner-1, 3→inner-2
    else -> crBase + (lane / 2) * step                     // 2→outer-1, 4→outer-2
}

/** Back-calculate base radius to fit all lanes, ticks and labels inside the screen. */
private fun fitBaseRadius(
    w: Float, h: Float, maxLane: Int, step: Float, hw: Float,
    tickLen: Float, labelGap: Float, labelHalfW: Float, pad: Float, innerMin: Float
): Float {
    val reserved = (maxLane / 2) * step + hw + tickLen + labelGap + labelHalfW + pad
    return (min(w, h) / 2f - reserved).coerceAtLeast(innerMin)
}

