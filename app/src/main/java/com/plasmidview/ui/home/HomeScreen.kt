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

private val SUPPORTED_EXT = setOf("dna", "gb", "gbk", "fasta", "fa", "json")

private fun extOk(name: String): Boolean = SUPPORTED_EXT.any { name.lowercase().endsWith(".$it") }

private val FOLDER_SUPPORTED = setOf("dna", "gb", "fasta", "fa")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDocument: (Int) -> Unit, onNavTo: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { FileRepository(ctx) }
    val folderRepo = remember { FolderRepository(ctx) }
    val imported by repo.files.collectAsState(initial = emptyList())
    val folderList by folderRepo.folders.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Browsing state
    var browsingFolder by remember { mutableStateOf<FolderEntry?>(null) }
    var folderFiles by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }

    // Prune inaccessible folders on startup
    LaunchedEffect(Unit) {
        folderRepo.pruneInaccessible()
    }

    // Single file picker (multi-select)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.let { scope.launch {
            it.forEach { uri ->
                try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                repo.add(FileEntry(getName(ctx, uri), uri.toString()))
            }
        }}
    }

    // Folder picker for importing a folder
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val name = getFolderName(ctx, uri)
            folderRepo.add(FolderEntry(uri.toString(), name))
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("PlasmidView"); if (isLoading || folderLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) } },
            actions = {
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
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Import buttons row
            Row(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FileOpen, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text("Import file", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Card(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text("Add folder", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // Content area
            if (browsingFolder != null) {
                // ── Folder browsing mode ──
                val folder = browsingFolder!!
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { browsingFolder = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Spacer(Modifier.width(4.dp))
                    Text(folder.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (folderLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (folderFiles.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No supported files (.dna .gb .fasta .fa)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(folderFiles, key = { it.uri }) { entry ->
                            Surface(modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    scope.launch {
                                        isLoading = true; loadingText = "Loading ${entry.name}..."
                                        try {
                                            val uri = Uri.parse(entry.uri)
                                            val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes()
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
                            ), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Folder list + imported files ──
                if (folderList.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Folders (${folderList.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(folderList, key = { it.uri }) { entry ->
                            Surface(modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    browsingFolder = entry
                                    folderLoading = true; folderFiles = emptyList()
                                    scope.launch {
                                        try {
                                            val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, Uri.parse(entry.uri))
                                            if (docFile != null) {
                                                folderFiles = docFile.listFiles().filter { f ->
                                                    f.name?.let { n -> FOLDER_SUPPORTED.any { n.lowercase().endsWith(".$it") } } == true
                                                }.mapNotNull { f -> f.uri?.let { FileEntry(f.name ?: "?", it.toString()) } }
                                            }
                                        } catch (_: Exception) { }
                                        folderLoading = false
                                    }
                                },
                                onLongClick = {
                                    scope.launch { folderRepo.remove(setOf(entry.uri)) }
                                }
                            ), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { scope.launch { folderRepo.remove(setOf(entry.uri)) } }) {
                                        Icon(Icons.Default.Close, "Remove folder", Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (imported.isNotEmpty()) {
                            item { Spacer(Modifier.height(4.dp)); HorizontalDivider(); Spacer(Modifier.height(4.dp)) }
                            item { Text("Files (${imported.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
                        }
                        items(imported, key = { it.uri }) { entry ->
                            Surface(modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
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
                            ), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Add a folder to browse plasmids", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
    errorMsg?.let { msg ->
        Snackbar(Modifier.padding(16.dp), action = { TextButton(onClick = { errorMsg = null }) { Text("Dismiss") } }) { Text(msg) }
    }
}

private fun getFolderName(ctx: android.content.Context, uri: Uri): String {
    try {
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)
        if (doc != null && doc.name != null) return doc.name!!
    } catch (_: Exception) {}
    val path = uri.path ?: return "Folder"
    return path.substringAfterLast("/").ifBlank { "Folder" }
}

private fun getName(ctx: android.content.Context, uri: Uri): String {
    var n = "unknown"
    ctx.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.moveToFirst()) { val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (idx >= 0) n = it.getString(idx) }
    }
    return n
}
