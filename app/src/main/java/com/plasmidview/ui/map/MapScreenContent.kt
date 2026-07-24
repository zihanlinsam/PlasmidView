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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.*
import kotlin.math.*

private const val ARC_W_DP = 12f
private const val TICK_N = 8

@Composable
fun MapScreenContent(docIndex: Int, fontSize: Float = 12f) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var sf by remember { mutableStateOf<Feature?>(null) }
    var sd by remember { mutableStateOf(false) }
    // var scale by remember { mutableFloatStateOf(1f) }
    // var ox by remember { mutableFloatStateOf(0f) }
    // var oy by remember { mutableFloatStateOf(0f) }

    // Use explicit state objects for gesture lambda capture
    val scaleS = remember { mutableFloatStateOf(1f) }
    val oxS = remember { mutableFloatStateOf(0f) }
    val oyS = remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        Text("${doc.features.size} features · ${doc.totalLength} bp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            CircCanvas(doc, scaleS, oxS, oyS) { f -> sf = f; sd = true }
        }
    }
    if (sd && sf != null) FeatDlg(sf!!, doc) { sd = false; sf = null }
}

@Composable
private fun CircCanvas(
    doc: PlasmidDocument, scaleS: MutableFloatState, oxS: MutableFloatState, oyS: MutableFloatState, fontSize: Float = 12f,
    onClick: (Feature) -> Unit
) {
    val len = doc.totalLength.toFloat(); if (len == 0f) return
    val oc = MaterialTheme.colorScheme.outline
    val tc = MaterialTheme.colorScheme.onBackground
    val isDark = (MaterialTheme.colorScheme.background.red * 0.299 + MaterialTheme.colorScheme.background.green * 0.587 + MaterialTheme.colorScheme.background.blue * 0.114) < 0.5f
    val borderColor = if (isDark) Color.White else Color.Black

    Canvas(Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scaleS.floatValue = (scaleS.floatValue * zoom).coerceIn(0.3f, 5f)
                oxS.floatValue += pan.x
                oyS.floatValue += pan.y
            }
        }
        .pointerInput(doc) {
            detectTapGestures { tap ->
                val cs = size; val cx = cs.width / 2f + oxS.floatValue; val cy = cs.height / 2f + oyS.floatValue
                val cr = min(cs.width, cs.height) * 0.42f * scaleS.floatValue
                val d = sqrt((tap.x - cx) * (tap.x - cx) + (tap.y - cy) * (tap.y - cy))
                val hi = cr - ARC_W_DP.dp.toPx() / 2f - 6.dp.toPx()
                val ho = cr + ARC_W_DP.dp.toPx() / 2f + 6.dp.toPx()
                if (d in hi..ho) {
                    var a = Math.toDegrees(atan2((tap.y - cy).toDouble(), (tap.x - cx).toDouble())).toFloat()
                    a = ((a + 90) % 360 + 360) % 360
                    val pos = (a / 360f * len).toInt().coerceIn(0, doc.totalLength)
                    doc.features.firstOrNull { f -> f.start <= pos && pos <= f.end }?.let(onClick)
                }
            }
        }
    ) {
        val cs = size; val cx = cs.width / 2f + oxS.floatValue; val cy = cs.height / 2f + oyS.floatValue
        val cr = min(cs.width, cs.height) * 0.42f * scaleS.floatValue
        val aw = ARC_W_DP.dp.toPx(); val hw = aw / 2f

        // Backbone
        drawCircle(color = oc.copy(alpha = 0.25f), radius = cr, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))

        // Features with two-layer border+fill
        doc.features.forEach { f ->
            if (f.start >= f.end) return@forEach
            val col = try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }
            val sa = (f.start / len) * 360f - 90f; val sw = (f.end - f.start) / len * 360f
            val rev = f.strand == Strand.REVERSE; val nb = f.strand == Strand.NONE

            // Border (wider)
            if (!nb) {
                val dfx = if (rev) PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) else null
                drawArc(borderColor, sa, sw, false, Offset(cx - cr, cy - cr), Size(cr * 2, cr * 2),
                    style = Stroke(width = aw + 4.dp.toPx(), pathEffect = dfx))
            }
            // Fill (on top)
            drawArc(col, sa, sw, false, Offset(cx - cr, cy - cr), Size(cr * 2, cr * 2),
                style = Stroke(width = aw))
        }

        // Ticks (fixed size regardless of zoom)
        for (i in 0 until TICK_N) {
            val a = (i.toFloat() / TICK_N) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val r1 = cr + hw + 2.dp.toPx(); val r2 = cr + hw + 10.dp.toPx()
            drawLine(oc, Offset(cx + r1 * cos(a), cy + r1 * sin(a)), Offset(cx + r2 * cos(a), cy + r2 * sin(a)), 1.dp.toPx())
            val lx = cx + (cr + hw + 16.dp.toPx()) * cos(a); val ly = cy + (cr + hw + 16.dp.toPx()) * sin(a)
            drawContext.canvas.nativeCanvas.drawText("${(i * doc.totalLength / TICK_N)}", lx, ly + 4.dp.toPx(),
                android.graphics.Paint().apply { color = tc.hashCode(); textSize = fontSize.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER })
        }
    }
}

@Composable
private fun FeatDlg(f: Feature, doc: PlasmidDocument, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(f.name.ifBlank { f.type.label }, fontWeight = FontWeight.Bold) },
        text = { Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }))
            Spacer(Modifier.height(8.dp))
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
