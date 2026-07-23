package com.plasmidview.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.*
import com.plasmidview.data.parser.PlasmidParser
import com.plasmidview.data.parser.SnapGeneBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SUPPORTED_EXT = setOf("dna", "fasta", "fa", "gb", "gbk", "json")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDocument: (Int) -> Unit, onNavTo: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { FileRepository(ctx) }
    val imported by repo.files.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selMode by remember { mutableStateOf(false) }
    var selUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Example button shown in empty state instead of dialog

    // Helper to import files from URIs
    val importFiles: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            uris.forEach { uri ->
                try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                val name = getName(ctx, uri)
                repo.add(FileEntry(name, uri.toString()))
            }
        }
    }

    // Single file picker (multi-select)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.let { importFiles(it) }
    }

    // Folder picker
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true; loadingText = "Scanning folder..."
            try {
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)
                if (docFile != null) {
                    scanFiles(docFile, importFiles, ctx, scope)
                }
            } catch (e: Exception) { errorMsg = e.message }
            isLoading = false; loadingText = ""
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("PlasmidView"); if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) } },
            actions = {
                if (selMode) {
                    IconButton(onClick = {
                        val uris = selUris; selMode = false; selUris = emptySet()
                        scope.launch { repo.remove(uris) }
                    }) { Icon(Icons.Default.Delete, "Delete") }
                    IconButton(onClick = { selMode = false; selUris = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                } else {
                    IconButton(onClick = { try { android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/zihanlinsam/PlasmidView")).let { ctx.startActivity(it) } } catch (_: Exception) { } }) { Text("?", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Display") }, onClick = { showMenu = false; onNavTo("settings_display") })
                            DropdownMenuItem(text = { Text("AI Configuration") }, onClick = { showMenu = false; onNavTo("settings_ai") })
                            DropdownMenuItem(text = { Text("Language") }, onClick = { showMenu = false; onNavTo("settings_lang") })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("About") }, onClick = { showMenu = false; onNavTo("about") })
                        }
                    }
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Two import buttons
            Row(Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FileOpen, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("Import file", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(".fasta .gb .dna", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
                Card(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("Import folder", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Scan for .dna .fasta .gb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // Imported files list
            if (imported.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Imported files (${imported.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { scope.launch { repo.clear() } }) { Text("Clear all") }
                }

                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(imported, key = { it.uri }) { entry ->
                        val selected = entry.uri in selUris
                        Surface(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    if (selMode) {
                                        selUris = if (selected) selUris - entry.uri else selUris + entry.uri
                                        if (selUris.isEmpty()) selMode = false
                                    } else {
                                        scope.launch {
                                            isLoading = true; loadingText = "Loading ${entry.name}..."
                                            try {
                                                val bytes = ctx.contentResolver.openInputStream(Uri.parse(entry.uri))?.readBytes()
                                                if (bytes != null) {
                                                    val cnt = bytes.decodeToString()
                                                    val result = when {
                                                        cnt.trimStart().startsWith("{") -> PlasmidParser.fromJson(cnt)
                                                        cnt.trimStart().startsWith(">") -> ParseResult.Success(PlasmidParser.fromFasta(cnt))
                                                        entry.uri.endsWith(".dna", true) -> SnapGeneBridge.parseBytes(ctx, bytes, entry.name)
                                                        else -> ParseResult.Success(PlasmidParser.fromFasta(cnt))
                                                    }
                                                    when (result) {
                                                        is ParseResult.Success -> {
                                                            if (result.doc.sequence.length < 10) errorMsg = "No valid sequence"
                                                            else { DocumentRepository.documents.add(result.doc); onOpenDocument(DocumentRepository.documents.size - 1) }
                                                        }
                                                        is ParseResult.Error -> errorMsg = result.message
                                                    }
                                                }
                                            } catch (e: Exception) { errorMsg = e.message }
                                            isLoading = false; loadingText = ""
                                        }
                                    }
                                },
                                onLongClick = { selMode = true; selUris = setOf(entry.uri) }
                            ), shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selMode) { Checkbox(checked = selected, onCheckedChange = {
                                    selUris = if (selected) selUris - entry.uri else selUris + entry.uri
                                    if (selUris.isEmpty()) selMode = false
                                }); Spacer(Modifier.width(8.dp)) }
                                Icon(Icons.Default.Description, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Tap to load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Import a file to get started", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                isLoading = true; loadingText = "Loading example..."
                                try {
                                    val bytes = withContext(Dispatchers.IO) {
                                        ctx.assets.open("pMDLgpRRE.dna").use { it.readBytes() }
                                    }
                                    val result = SnapGeneBridge.parseBytes(ctx, bytes, "pMDLgpRRE.dna")
                                    when (result) {
                                        is ParseResult.Success -> {
                                            DocumentRepository.documents.add(result.doc)
                                            onOpenDocument(DocumentRepository.documents.size - 1)
                                        }
                                        is ParseResult.Error -> errorMsg = result.message
                                    }
                                } catch (e: Exception) { errorMsg = e.message }
                                isLoading = false; loadingText = ""
                            }
                        }) { Text("or try the example") }
                    }
                }
            }
        }
    }
    errorMsg?.let { msg ->
        Snackbar(Modifier.padding(16.dp), action = { TextButton(onClick = { errorMsg = null }) { Text("Dismiss") } }) { Text(msg) }
    }



}
private suspend fun scanFiles(docFile: androidx.documentfile.provider.DocumentFile, importFn: (List<Uri>) -> Unit, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
    val files = docFile.listFiles().filter {
        val n = it.name?.lowercase() ?: return@filter false
        SUPPORTED_EXT.any { ext -> n.endsWith(".$ext") }
    }
    if (files.isNotEmpty()) {
        importFn(files.mapNotNull { it.uri })
    }
}

private fun getName(ctx: android.content.Context, uri: Uri): String {
    var n = "unknown"
    ctx.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) { val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) n = it.getString(idx) }
    }
    return n
}
