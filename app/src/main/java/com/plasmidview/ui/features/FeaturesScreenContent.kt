package com.plasmidview.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plasmidview.data.ai.AiClient
import com.plasmidview.data.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreenContent(docIndex: Int) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    val ctx = LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiLang by prefs.aiLang.collectAsState(initial = "english")
    val scope = rememberCoroutineScope()

    var searchQ by remember { mutableStateOf("") }
    var selFeat by remember { mutableStateOf<Feature?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var plasmidAiResult by remember { mutableStateOf<String?>(null) }
    var plasmidAiLoading by remember { mutableStateOf(false) }

    val filtered = remember(searchQ, doc.features) {
        if (searchQ.isBlank()) doc.features
        else doc.features.filter { f -> f.name.contains(searchQ, ignoreCase = true) || f.type.label.contains(searchQ, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        // Header bar with count and AI button
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} / ${doc.features.size} features", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = {
                        scope.launch {
                            plasmidAiLoading = true; plasmidAiResult = ""
                            val client = AiClient(aiUrl, aiKey, aiModel, aiLang)
                            val featDesc = doc.features.joinToString("\n") { f ->
                                "- ${f.name} (${f.type.label}): ${f.start + 1}-${f.end}, ${f.length}bp, strand=${f.strand.label}"
                            }
                            val prompt = "Analyze this plasmid with ${doc.features.size} features:\n$featDesc\n\nDescribe the plasmid's structure and likely function."
                            var buf = StringBuilder()
                            client.askStream("You are a molecular biology expert.", prompt).collect { token ->
                                buf.append(token)
                                if (buf.length >= 50) {
                                    plasmidAiResult = (plasmidAiResult ?: "") + buf.toString()
                                    buf = StringBuilder()
                                }
                            }
                            plasmidAiResult = (plasmidAiResult ?: "") + buf.toString()
                            plasmidAiLoading = false
                        }
                    }) { Icon(Icons.Default.Psychology, "AI analyze plasmid") }
                }
            }
        }

        // Search bar
        OutlinedTextField(value = searchQ, onValueChange = { searchQ = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search features...") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            trailingIcon = if (searchQ.isNotBlank()) { { IconButton(onClick = { searchQ = "" }) { Icon(Icons.Default.Close, "Clear") } } } else null)

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching features", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(filtered, key = { _, f -> "${f.start}-${f.name}" }) { _, feat ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { selFeat = feat; showDetail = true; aiResult = null },
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(4.dp, 32.dp).background(
                                try { Color(android.graphics.Color.parseColor(feat.color)) } catch (_: Exception) { Color.Gray }))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(feat.name.ifBlank { feat.type.label }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${feat.start + 1} - ${feat.end} · ${feat.length} bp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(feat.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    // Plasmid AI result dialog
    if (plasmidAiResult != null || plasmidAiLoading) {
        AlertDialog(
            onDismissRequest = { plasmidAiResult = null },
            title = {
                Column {
                    Text("About this plasmid", fontWeight = FontWeight.Bold)
                    if (plasmidAiLoading) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator()
                    }
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    if (plasmidAiResult != null && plasmidAiResult!!.isNotBlank()) {
                        MarkdownText(markdown = plasmidAiResult!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { plasmidAiResult = null }) { Text("OK") }
                    TextButton(onClick = { plasmidAiResult = null }) { Text("Copy") }
                }
            }
        )
    }

    // Feature detail dialog with Ask AI
    if (showDetail && selFeat != null) {
        val f = selFeat!!
        AlertDialog(
            onDismissRequest = { showDetail = false; selFeat = null; aiResult = null },
            title = { Text(f.name.ifBlank { f.type.label }, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(
                        try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }))
                    Spacer(Modifier.height(8.dp))
                    Text("Type: ${f.type.label}"); Text("Position: ${f.start + 1} - ${f.end}")
                    Text("Length: ${f.length} bp"); Text("Strand: ${f.strand.label}")
                    val e2 = f.end.coerceAtMost(doc.totalLength); val s2 = f.start.coerceAtMost(e2)
                    if (s2 < e2) { Spacer(Modifier.height(8.dp)); val seq = doc.sequence.substring(s2, e2)
                        Text("Sequence (${seq.length} bp):", fontWeight = FontWeight.Bold)
                        Text(seq.take(300) + if (seq.length > 300) "..." else "") }
                    // AI loading bar (animated or themed constant)
                    if (aiLoading) {
                        Spacer(Modifier.height(8.dp))
                        Text("AI Analysis:", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator()
                    } else if (aiResult != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("AI Analysis:", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        // Themed constant bar (completed)
                        Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                        Spacer(Modifier.height(4.dp))
                        MarkdownText(markdown = aiResult!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDetail = false; selFeat = null; aiResult = null }) { Text("Close") }
                    Button(onClick = {
                        if (aiResult == null && !aiLoading) {
                            scope.launch {
                                aiLoading = true; aiResult = ""
                                val client = AiClient(aiUrl, aiKey, aiModel, aiLang)
                                val prompt = "Feature: ${f.name} (${f.type.label}), position ${f.start + 1}-${f.end}, ${f.length}bp, strand=${f.strand.label}. What is this element and what does it do in a plasmid?"
                                var buf = StringBuilder()
                                client.askStream("You are a molecular biology expert specializing in plasmid design.", prompt, fast = true).collect { token ->
                                    buf.append(token)
                                    if (buf.length >= 50) {
                                        aiResult = (aiResult ?: "") + buf.toString()
                                        buf = StringBuilder()
                                    }
                                }
                                aiResult = (aiResult ?: "") + buf.toString()
                                aiLoading = false
                            }
                        }
                    }) {
                        if (aiLoading) { CircularProgressIndicator(Modifier.size(16.dp)) } else { Icon(Icons.Default.Psychology, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Ask AI") }
                    }
                }
            }
        )
    }
}
