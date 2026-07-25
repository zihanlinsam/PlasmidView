package com.plasmidview.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plasmidview.data.ai.AiClient
import com.plasmidview.data.model.*
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Unified feature detail dialog used by Map, Sequence, Features and Digest screens.
 *  If aiUrl is non-empty, an Ask AI button is shown with streaming analysis. */
@Composable
fun FeatureDetailDialog(
    feature: Feature,
    doc: PlasmidDocument,
    onDismiss: () -> Unit,
    aiUrl: String = "",
    aiKey: String = "",
    aiModel: String = "",
    aiLang: String = "english",
    aiThinking: Boolean = false
) {
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    val f = feature

    AlertDialog(
        onDismissRequest = {
            aiResult = null; aiLoading = false; onDismiss()
        },
        title = { Text(f.name.ifBlank { f.type.label }, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Color bar
                Box(Modifier.fillMaxWidth().height(4.dp).background(
                    try { Color(android.graphics.Color.parseColor(f.color)) } catch (_: Exception) { Color.Gray }
                ))
                Spacer(Modifier.height(8.dp))
                Text("Type: ${f.type.label}")
                Text("Position: ${f.start + 1} - ${f.end}")
                Text("Length: ${f.length} bp")
                Text("Strand: ${f.strand.label}")

                // Sequence
                val seq = f.displaySequence(doc)
                if (seq.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Sequence (${seq.length} bp):", fontWeight = FontWeight.Bold)
                    Text(seq.take(300) + if (seq.length > 300) "..." else "")
                }

                // AI analysis (only if configured)
                if (aiUrl.isNotBlank()) {
                    if (aiLoading) {
                        Spacer(Modifier.height(8.dp))
                        Text("AI Analysis:", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator()
                    }
                    if (aiResult != null && aiResult!!.isNotBlank()) {
                        if (!aiLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text("AI Analysis:", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                            Spacer(Modifier.height(4.dp))
                        }
                        MarkdownText(markdown = aiResult!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    aiResult = null; aiLoading = false; onDismiss()
                }) { Text("Close") }

                if (aiUrl.isNotBlank()) {
                    Button(onClick = {
                        if (aiResult == null && !aiLoading) {
                            scope.launch {
                                aiLoading = true; aiResult = ""
                                val client = AiClient(aiUrl, aiKey, aiModel, aiLang, thinkingEnabled = aiThinking)
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
                        } else if (aiResult != null && !aiLoading) {
                            clip.setText(AnnotatedString(aiResult!!))
                        }
                    }) {
                        when {
                            aiLoading -> CircularProgressIndicator(Modifier.size(16.dp))
                            aiResult != null -> {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Copy")
                            }
                            else -> {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Ask AI")
                            }
                        }
                    }
                }
            }
        }
    )
}
