package it.healthsync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Spruce = Color(0xFF1F6B5A)
private val SpruceDark = Color(0xFF8FD4C2)
private val Paper = Color(0xFFF6F3EC)
private val Ink = Color(0xFF1C1B18)
private val Clay = Color(0xFF8A5A2A)

private val LightColors = lightColorScheme(
    primary = Spruce,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDE4),
    onPrimaryContainer = Color(0xFF06281F),
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E4D4),
    onSecondaryContainer = Color(0xFF2C1B0A),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFCF7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E2D8),
    onSurfaceVariant = Color(0xFF4A463F),
    error = Color(0xFF9B2C2C),
    onError = Color.White,
    errorContainer = Color(0xFFF8D6D2),
    onErrorContainer = Color(0xFF3F0A0A),
    outline = Color(0xFFD4CBBC),
)

private val DarkColors = darkColorScheme(
    primary = SpruceDark,
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF13483C),
    onPrimaryContainer = Color(0xFFD5EDE4),
    secondary = Color(0xFFE7C3A1),
    onSecondary = Color(0xFF412D16),
    secondaryContainer = Color(0xFF5A3E22),
    onSecondaryContainer = Color(0xFFF3E4D4),
    background = Color(0xFF141411),
    onBackground = Color(0xFFE8E4DC),
    surface = Color(0xFF1E1C18),
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF333028),
    onSurfaceVariant = Color(0xFFD0C8BA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF5C564C),
)

@Composable
fun SaluteSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
