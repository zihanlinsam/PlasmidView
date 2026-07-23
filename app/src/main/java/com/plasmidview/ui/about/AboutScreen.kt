package com.plasmidview.ui.about

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    val iconBitmap = remember {
        try {
            ctx.assets.open("app_icon.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top 1/5: app icon centered
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.size(120.dp))
                } else {
                    Text("PlasmidView", style = MaterialTheme.typography.headlineMedium)
                }
            }

            HorizontalDivider()

            // Content
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("PlasmidView", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("v1.0-beta3", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                Text("Author", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Sam Lin (Zihan Lin)", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("zihanlinsammy@qq.com", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                Text("GitHub", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("github.com/zihanlinsam/PlasmidView", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                Text("Tech Stack", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Kotlin · Jetpack Compose · Material You · Chaquopy · MiMo AI", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Restriction engine: Pure Kotlin + REBASE (1,088 enzymes)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                Text("License", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("GNU Affero General Public License v3.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))

                Text("Acknowledgments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Biopython — REBASE restriction enzyme data", style = MaterialTheme.typography.bodyMedium)
                Text("sgffp — SnapGene .dna file parser", style = MaterialTheme.typography.bodyMedium)
                Text("REBASE — Restriction Enzyme Database", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
