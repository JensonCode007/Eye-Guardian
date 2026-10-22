package com.eyeguardian.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF78E4B0),
    secondary = Color(0xFF67C9FF),
    tertiary = Color(0xFFFFA7C7),
    background = Color(0xFF0B1724),
    surface = Color(0xFF14263B),
    onSurface = Color(0xFFF1F7FF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF087A58),
    onPrimary = Color.White,
    secondary = Color(0xFF3178C6),
    onSecondary = Color.White,
    tertiary = Color(0xFFE9769F),
    background = Color(0xFFF1F6F3),
    surface = Color.White,
    onBackground = Color(0xFF123B2A),
    onSurface = Color(0xFF17372B)
)

@Composable
fun EyeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalEyeGuardDark provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
