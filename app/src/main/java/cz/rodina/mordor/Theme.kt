package cz.rodina.mordor

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale

val Parchment = Color(0xFFF3E4C3)
val ParchmentDark = Color(0xFFE2CB98)
val Ink = Color(0xFF3B2A1A)
val InkSoft = Color(0xFF6E5639)
val Gold = Color(0xFFB8860B)
val Blood = Color(0xFF8B1E1E)
val Elf = Color(0xFF3E5B3A)
val River = Color(0xFF4F6F86)
val Shadow = Color(0xFF1E2418)

val MemberColors = listOf(Color(0xFF2E6B3A), Color(0xFF8B1E1E), Color(0xFF2F4F7F), Color(0xFF9A6A12), Color(0xFF6A3D7A), Color(0xFF3D6E6E))

private fun TextStyle.serif() = copy(fontFamily = FontFamily.Serif)

private val base = Typography()
private val SerifTypography = Typography(
    displayLarge = base.displayLarge.serif(), displayMedium = base.displayMedium.serif(), displaySmall = base.displaySmall.serif(),
    headlineLarge = base.headlineLarge.serif(), headlineMedium = base.headlineMedium.serif(), headlineSmall = base.headlineSmall.serif(),
    titleLarge = base.titleLarge.serif(), titleMedium = base.titleMedium.serif(), titleSmall = base.titleSmall.serif(),
    bodyLarge = base.bodyLarge.serif(), bodyMedium = base.bodyMedium.serif(), bodySmall = base.bodySmall.serif(),
    labelLarge = base.labelLarge.serif(), labelMedium = base.labelMedium.serif(), labelSmall = base.labelSmall.serif(),
)

@Composable
fun MordorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blood,
            onPrimary = Parchment,
            secondary = Elf,
            onSecondary = Parchment,
            tertiary = Gold,
            background = Parchment,
            onBackground = Ink,
            surface = Parchment,
            onSurface = Ink,
            surfaceVariant = ParchmentDark,
            onSurfaceVariant = InkSoft,
            outline = InkSoft,
            secondaryContainer = ParchmentDark,
            onSecondaryContainer = Ink,
        ),
        typography = SerifTypography,
        content = content,
    )
}

fun Modifier.parchment() = background(Brush.verticalGradient(listOf(Parchment, Color(0xFFEBD8AF), Parchment)))

private val CZ = Locale("cs", "CZ")
fun Long.fmt(): String = String.format(CZ, "%,d", this)
fun Double.km(): String = String.format(CZ, "%.1f km", this)
