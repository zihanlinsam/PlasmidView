package com.plasmidview
import androidx.compose.runtime.getValue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.plasmidview.data.model.AppPreferences
import com.plasmidview.ui.navigation.PlasmidNavGraph
import com.plasmidview.ui.theme.PlasmidViewTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPreferences(this)

        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = com.plasmidview.data.model.ThemeMode.AUTO)
            PlasmidViewTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PlasmidNavGraph()
                }
            }
        }
    }
}
