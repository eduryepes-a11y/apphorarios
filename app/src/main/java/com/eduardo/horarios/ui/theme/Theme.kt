package com.eduardo.horarios.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E3FF),
    onPrimaryContainer = Color(0xFF1B1464),
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5F1),
    onSecondaryContainer = Color(0xFF00403A),
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3E0),
    onTertiaryContainer = Color(0xFF5C0F0F),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF15162B),
    surface = Color.White,
    onSurface = Color(0xFF15162B),
    surfaceVariant = Color(0xFFEEEEF6),
    onSurfaceVariant = Color(0xFF62647A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9FD),
    surfaceContainer = Color(0xFFF2F2F8),
    surfaceContainerHigh = Color(0xFFEDEDF5),
    surfaceContainerHighest = Color(0xFFE7E7F0),
    outline = Color(0xFFC9CADB),
    outlineVariant = Color(0xFFE3E3EE),
    error = Color(0xFFE5484D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D94FF),
    onPrimary = Color(0xFF1B1464),
    primaryContainer = Color(0xFF3A30A8),
    onPrimaryContainer = Color(0xFFE6E3FF),
    secondary = Color(0xFF5FDDD0),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF0B4F48),
    onSecondaryContainer = Color(0xFFD5F5F1),
    tertiary = Color(0xFFFF8A8A),
    onTertiary = Color(0xFF4A0B0B),
    tertiaryContainer = Color(0xFF6B2323),
    onTertiaryContainer = Color(0xFFFFE3E0),
    background = Color(0xFF0E0F1C),
    onBackground = Color(0xFFE8E8F4),
    surface = Color(0xFF181929),
    onSurface = Color(0xFFE8E8F4),
    surfaceVariant = Color(0xFF232437),
    onSurfaceVariant = Color(0xFFA6A8C0),
    surfaceContainerLowest = Color(0xFF0B0C17),
    surfaceContainerLow = Color(0xFF141524),
    surfaceContainer = Color(0xFF1C1D2E),
    surfaceContainerHigh = Color(0xFF232437),
    surfaceContainerHighest = Color(0xFF2B2C40),
    outline = Color(0xFF4A4C66),
    outlineVariant = Color(0xFF2E3046),
    error = Color(0xFFFF6B6F),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

@Composable
fun HorariosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Iconos de la barra de estado: oscuros sobre fondo claro o claros sobre fondo oscuro. */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val darkNavIcons = !isSystemInDarkTheme()
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkNavIcons
    }
}
