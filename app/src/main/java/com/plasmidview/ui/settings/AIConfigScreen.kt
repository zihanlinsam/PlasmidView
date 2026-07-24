package com.plasmidview.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plasmidview.data.ai.AiClient
import com.plasmidview.data.model.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIConfigScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(ctx) }
    val aiUrl by prefs.aiBaseUrl.collectAsState(initial = "")
    val aiKey by prefs.aiApiKey.collectAsState(initial = "")
    val aiModel by prefs.aiModel.collectAsState(initial = "")
    val aiThinking by prefs.aiThinking.collectAsState(initial = false)
    var testResult by remember { mutableStateOf<String?>(null) }
    var testLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(title = { Text("AI Configuration") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(value = aiUrl, onValueChange = { scope.launch { prefs.setAiUrl(it) } }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = aiKey, onValueChange = { scope.launch { prefs.setAiKey(it) } }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = aiModel, onValueChange = { scope.launch { prefs.setAiModel(it) } }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Deep Thinking", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Enables chain-of-thought reasoning (slower but more thorough)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = aiThinking, onCheckedChange = { scope.launch { prefs.setAiThinking(it) } })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                testLoading = true; testResult = null
                scope.launch {
                    val client = AiClient(aiUrl, aiKey, aiModel, thinkingEnabled = aiThinking)
                    val result = withContext(Dispatchers.IO) { client.ask("You are a helpful assistant.", "Say 'OK' if you can hear me.", fast = true) }
                    testResult = if (result.startsWith("OK") || result.contains("OK")) "Connection OK" else "Connection ERROR! $result"
                    testLoading = false
                }
            }, enabled = !testLoading) { if (testLoading) { CircularProgressIndicator(Modifier.size(16.dp)) } else { Text("Test Connection") } }
            if (testResult != null) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Text(testResult!!, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
