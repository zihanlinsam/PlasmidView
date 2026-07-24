package com.plasmidview.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.alignment.AlignmentSession
import com.plasmidview.data.alignment.SmithWaterman
import com.plasmidview.data.model.*
import com.plasmidview.ui.map.MapScreenContent
import com.plasmidview.ui.sequence.SequenceScreenContent
import com.plasmidview.ui.features.FeaturesScreenContent
import com.plasmidview.ui.digest.DigestScreenContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlasmidTab(val label: String, val icon: ImageVector) {
    MAP("Map", Icons.Default.Dashboard),
    SEQUENCE("Sequence", Icons.Default.Code),
    FEATURES("Features", Icons.Default.ListAlt),
    DIGEST("Digest", Icons.Default.ContentCut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlasmidContentScreen(docIndex: Int, onBack: () -> Unit, onNavigateToAlign: () -> Unit = {}) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var selectedTab by remember { mutableStateOf(PlasmidTab.MAP) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    var showMenu by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var alignLoading by remember { mutableStateOf(false) }

    val alignFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            alignLoading = true
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val content = bytes.decodeToString()
                val refSeq = doc.sequence.replace(Regex("\\s+"), "").uppercase()
                val (qName, qSeq) = parseFirstFasta(content)
                val best = withContext(Dispatchers.IO) {
                    val fwd = SmithWaterman.align(qSeq, refSeq, qName, doc.name)
                    val rev = SmithWaterman.align(qSeq.reverseComplement(), refSeq, "$qName (rev-comp)", doc.name, isRevComp = true)
                    val fwdScore = fwd?.score ?: 0
                    val revScore = rev?.score ?: 0
                    if (fwdScore >= revScore) fwd else rev
                }
                if (best != null) {
                    AlignmentSession.result = best
                    AlignmentSession.document = doc
                    AlignmentSession.originalQuerySeq = qSeq
                    onNavigateToAlign()
                }
            } catch (_: Exception) { }
            alignLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedTab == PlasmidTab.SEQUENCE) {
                        IconButton(onClick = {
                            alignFilePicker.launch(arrayOf("text/plain", "*/*"))
                        }) {
                            if (alignLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Compare, contentDescription = "Compare")
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Font size")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text("Font size", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Slider(value = fontSize, onValueChange = { fontSize = it },
                                        valueRange = 8f..20f, steps = 11, modifier = Modifier.width(180.dp))
                                    Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                PlasmidTab.entries.forEach { tab ->
                    val outIcon = when (tab) {
                        PlasmidTab.MAP -> Icons.Outlined.Dashboard
                        PlasmidTab.SEQUENCE -> Icons.Outlined.Code
                        PlasmidTab.FEATURES -> Icons.Outlined.ListAlt
                        PlasmidTab.DIGEST -> Icons.Outlined.ContentCut
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(if (selectedTab == tab) tab.icon else outIcon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(200),
            modifier = Modifier.padding(padding)
        ) { tab ->
            when (tab) {
                PlasmidTab.MAP -> MapScreenContent(docIndex, fontSize)
                PlasmidTab.SEQUENCE -> SequenceScreenContent(docIndex, fontSize)
                PlasmidTab.FEATURES -> FeaturesScreenContent(docIndex)
                PlasmidTab.DIGEST -> DigestScreenContent(docIndex)
            }
        }
    }
}

private fun parseFirstFasta(content: String): Pair<String, String> {
    val seq = StringBuilder()
    var name = "Query"
    for (line in content.lines()) {
        when {
            line.startsWith(">") -> {
                if (seq.isEmpty()) {
                    name = line.substringAfter(">").trim().split("\\s+".toRegex(), limit = 2).firstOrNull() ?: "Query"
                }
            }
            line.isNotBlank() -> seq.append(line.replace(Regex("[^a-zA-Z]"), "").uppercase())
        }
    }
    return Pair(name, seq.toString())
}
