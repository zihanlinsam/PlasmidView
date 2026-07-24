package com.plasmidview.ui.about

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // App icon
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = "App icon",
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("PlasmidView", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("v2.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // Info rows
            AboutRow("Developer", "Sam Lin (Zihan Lin)")
            Spacer(Modifier.height(12.dp))
            AboutRow("Email", "zihanlinsammy@qq.com")
            Spacer(Modifier.height(12.dp))
            AboutRow("GitHub", "github.com/zihanlinsam/PlasmidView")
            Spacer(Modifier.height(12.dp))
            AboutRow("Tech Stack", "Kotlin · Jetpack Compose · Material You · Chaquopy")
            Spacer(Modifier.height(12.dp))
            AboutRow("Restriction Engine", "Pure Kotlin + REBASE (1,088 enzymes)")
            Spacer(Modifier.height(12.dp))
            AboutRow("License", "GNU Affero General Public License v3.0")
            Spacer(Modifier.height(20.dp))

            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // Privacy note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "All data is processed locally on your device. " +
                            "AI analysis is sent to your configured API endpoint only when you explicitly trigger it.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(20.dp))

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Acknowledgments",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Biopython — REBASE data · sgffp — .dna parser\nREBASE — Restriction Enzyme Database",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "© 2026 Sam Lin",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}
