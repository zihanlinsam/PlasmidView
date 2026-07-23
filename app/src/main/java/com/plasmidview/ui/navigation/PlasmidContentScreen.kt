package com.plasmidview.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plasmidview.data.model.DocumentRepository
import com.plasmidview.ui.map.MapScreenContent
import com.plasmidview.ui.sequence.SequenceScreenContent
import com.plasmidview.ui.features.FeaturesScreenContent
import com.plasmidview.ui.digest.DigestScreenContent

enum class PlasmidTab(val label: String, val icon: ImageVector) {
    MAP("Map", Icons.Default.Dashboard),
    SEQUENCE("Sequence", Icons.Default.Code),
    FEATURES("Features", Icons.Default.ListAlt),
    DIGEST("Digest", Icons.Default.ContentCut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlasmidContentScreen(docIndex: Int, onBack: () -> Unit) {
    val doc = DocumentRepository.documents.getOrNull(docIndex) ?: return
    var selectedTab by remember { mutableStateOf(PlasmidTab.MAP) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    var showMenu by remember { mutableStateOf(false) }

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
                    // Font size menu only visible on Sequence tab
                    if (selectedTab == PlasmidTab.SEQUENCE) {
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
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
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
