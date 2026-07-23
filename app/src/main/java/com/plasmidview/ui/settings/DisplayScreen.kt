package com.plasmidview.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plasmidview.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val baseCol by prefs.baseCol.collectAsState(initial = true)
    val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.AUTO)
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Display") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = themeMode == ThemeMode.AUTO, onClick = { scope.launch { prefs.setThemeMode(com.plasmidview.data.model.ThemeMode.AUTO) } }, label = { Text("Auto") })
                FilterChip(selected = themeMode == ThemeMode.LIGHT, onClick = { scope.launch { prefs.setThemeMode(com.plasmidview.data.model.ThemeMode.LIGHT) } }, label = { Text("Light") })
                FilterChip(selected = themeMode == ThemeMode.DARK, onClick = { scope.launch { prefs.setThemeMode(com.plasmidview.data.model.ThemeMode.DARK) } }, label = { Text("Dark") })
            }
            Spacer(Modifier.height(24.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Colored bases"); Text("Color-code A/T/G/C", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = baseCol, onCheckedChange = { scope.launch { prefs.setBase(it) } })
                }
            }
        }
    }
}
