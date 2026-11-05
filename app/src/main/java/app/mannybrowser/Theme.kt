package app.mannybrowser

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MannyBlue = Color(0xFF2E6BF0)

private val DarkColors = darkColorScheme(
    primary = MannyBlue,
    background = Color(0xFF101014),
    surface = Color(0xFF17171C),
    surfaceVariant = Color(0xFF232329)
)

private val LightColors = lightColorScheme(
    primary = MannyBlue,
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECECF2)
)

@Composable
fun MannyTheme(darkOverride: Boolean?, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
