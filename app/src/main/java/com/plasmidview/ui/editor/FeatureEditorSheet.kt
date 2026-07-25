package com.plasmidview.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plasmidview.data.model.*

private val presetColors = listOf(
    "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
    "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548",
    "#607D8B", "#9E9E9E", "#CCFFCC", "#FFFF00", "#FFCCCC",
)

private val allTypes = FeatureType.entries.toList()

fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) { Color.Gray }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureEditorSheet(
    feature: Feature,
    onSave: (Feature) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(feature.name) }
    var type by remember { mutableStateOf(feature.type) }
    var strand by remember { mutableStateOf(feature.strand) }
    var colorHex by remember { mutableStateOf(feature.color) }
    var start by remember { mutableStateOf(feature.start.toString()) }
    var end by remember { mutableStateOf(feature.end.toString()) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentColor = parseColor(colorHex)
    val length = try { end.toInt() - start.toInt() } catch (_: Exception) { 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(feature.name.ifBlank { feature.type.label }, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Color bar (same style as feature detail dialog)
                Box(Modifier.fillMaxWidth().height(4.dp).background(currentColor).padding(bottom = 12.dp))
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(expanded = showTypeMenu, onExpandedChange = { showTypeMenu = it }) {
                    OutlinedTextField(value = type.label, onValueChange = {}, readOnly = true, label = { Text("Type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeMenu) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        allTypes.forEach { t -> DropdownMenuItem(text = { Text(t.label) }, onClick = { type = t; showTypeMenu = false }) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
                if (length > 0) Text("${length} bp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                Text("Strand", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = strand == Strand.FORWARD, onClick = { strand = Strand.FORWARD }, label = { Text("Forward") }, leadingIcon = { if (strand == Strand.FORWARD) Icon(Icons.Default.Check, null, Modifier.size(14.dp)) else null })
                    FilterChip(selected = strand == Strand.REVERSE, onClick = { strand = Strand.REVERSE }, label = { Text("Reverse") }, leadingIcon = { if (strand == Strand.REVERSE) Icon(Icons.Default.Check, null, Modifier.size(14.dp)) else null })
                    FilterChip(selected = strand == Strand.NONE, onClick = { strand = Strand.NONE }, label = { Text("None") }, leadingIcon = { if (strand == Strand.NONE) Icon(Icons.Default.Check, null, Modifier.size(14.dp)) else null })
                }
                Spacer(Modifier.height(12.dp))

                Text("Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                presetColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { hex ->
                            val col = parseColor(hex)
                            val sel = colorHex.equals(hex, ignoreCase = true)
                            Box(Modifier.size(28.dp).clip(CircleShape).background(col)
                                .then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier.border(1.dp, col.copy(alpha = 0.2f), CircleShape))
                                .clickable { colorHex = hex }, contentAlignment = Alignment.Center) {
                                if (sel) Icon(Icons.Default.Check, null, tint = if (col == Color.White || hex == "#FFEB3B") Color.Black else Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null, Modifier.size(14.dp)) }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    val s = try { start.toInt().coerceAtLeast(0) } catch (_: Exception) { feature.start }
                    val e = try { end.toInt().coerceAtLeast(s + 1) } catch (_: Exception) { feature.end }
                    onSave(Feature(name = name, type = type, start = s, end = e, strand = strand, color = colorHex, description = feature.description))
                }) { Text("Save") }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Feature") },
            text = { Text("Are you sure you want to delete \"${feature.name.ifBlank { feature.type.label }}\"?") },
            confirmButton = { Button(onClick = { showDeleteConfirm = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
