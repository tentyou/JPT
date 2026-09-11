package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF89CFC8),
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF285D59),
    onSecondaryContainer = Color(0xFFB2F2E9),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF643E00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = TenkenNight,
    onBackground = Color(0xFFE0E7E5),
    surface = TenkenNightSurface,
    onSurface = Color(0xFFE0E7E5),
    surfaceVariant = Color(0xFF3B4A4A),
    onSurfaceVariant = Color(0xFFBECAC7),
    outline = Color(0xFF879492),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = TenkenInk,
    onPrimary = Color.White,
    primaryContainer = TenkenMint,
    onPrimaryContainer = Color(0xFF0D3536),
    secondary = TenkenTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F2EB),
    onSecondaryContainer = Color(0xFF073B38),
    tertiary = TenkenAmber,
    onTertiary = Color.White,
    tertiaryContainer = TenkenSand,
    onTertiaryContainer = Color(0xFF3B2100),
    background = TenkenCanvas,
    onBackground = Color(0xFF172020),
    surface = Color.White,
    onSurface = Color(0xFF172020),
    surfaceVariant = Color(0xFFE5ECEB),
    onSurfaceVariant = Color(0xFF4A5957),
    outline = TenkenOutline,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
