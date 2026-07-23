package com.plasmidview.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.plasmidview.data.model.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7), onPrimaryContainer = Color(0xFF0D3310),
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB), onSecondaryContainer = Color(0xFF00251E),
    background = Color(0xFFF5F5F5), onBackground = Color(0xFF1C1B1F),
    surface = Color.White, onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8E8E8), onSurfaceVariant = Color(0xFF49454F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF81C784), onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF80CBC4), onSecondary = Color(0xFF003733),
    background = Color(0xFF1C1B1F), onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F), onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2D2D), onSurfaceVariant = Color(0xFFCAC4D0),
)

@Composable
fun PlasmidViewTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        useDark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            w.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !useDark
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
