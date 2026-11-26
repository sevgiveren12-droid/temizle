package app.temizle

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF4DD6B0),
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF8AB4FF),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA4B2),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
    outline = Color(0xFF2E3744)
)

@Composable
fun TemizleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
