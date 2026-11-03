package it.palestra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colori principali dell'app: da mantenere sempre. */
object Pal {
    val Accent = Color(0xFFFFBE0B)      // primario
    val Secondary = Color(0xFF2A2312)   // secondario (superfici, card)
    val OnAccent = Color(0xFF1A1505)
    val Background = Color(0xFF0F0D08)
    val Surface = Color(0xFF16120A)
    val SurfaceLow = Color(0xFF1C170C)
    val Outline = Color(0xFF4A3F24)
    val OutlineSoft = Color(0xFF3A311B)
    val Text = Color(0xFFF5F0E6)
    val TextSoft = Color(0xFFD9D0BC)
    val Muted = Color(0xFFB8AE98)
    val Dim = Color(0xFF8F8672)
    val Failed = Color(0xFFE5604A)
    val OnFailed = Color(0xFF1A0A05)
}

private val colors = darkColorScheme(
    primary = Pal.Accent,
    onPrimary = Pal.OnAccent,
    primaryContainer = Pal.Secondary,
    onPrimaryContainer = Pal.Accent,
    secondary = Pal.Accent,
    onSecondary = Pal.OnAccent,
    secondaryContainer = Pal.Secondary,
    onSecondaryContainer = Pal.Text,
    tertiary = Pal.Accent,
    background = Pal.Background,
    onBackground = Pal.Text,
    surface = Pal.Background,
    onSurface = Pal.Text,
    surfaceVariant = Pal.Secondary,
    onSurfaceVariant = Pal.Muted,
    surfaceContainerLowest = Pal.Background,
    surfaceContainerLow = Pal.SurfaceLow,
    surfaceContainer = Pal.Surface,
    surfaceContainerHigh = Pal.Secondary,
    surfaceContainerHighest = Pal.Secondary,
    outline = Pal.Outline,
    outlineVariant = Pal.OutlineSoft,
    error = Pal.Failed,
    onError = Pal.OnFailed,
)

private val typography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

val LabelCaps = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Pal.Muted)

@Composable
fun PalestraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
