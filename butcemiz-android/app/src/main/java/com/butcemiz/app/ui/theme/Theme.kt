package com.butcemiz.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.butcemiz.app.prefs.ThemeMode

/** Gelir/gider/birikim gibi anlamsal renkler */
@Immutable
data class SemanticColors(
    val income: Color,
    val expense: Color,
    val gold: Color,
    val warning: Color,
    val transfer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val cardBorder: Color,
) {
    val heroBrush: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
}

private val LightSemantic = SemanticColors(
    income = Color(0xFF12A150),
    expense = Color(0xFFE5484D),
    gold = Color(0xFFC98A10),
    warning = Color(0xFFE38B00),
    transfer = Color(0xFF4F5BD5),
    heroStart = Color(0xFF0F8A70),
    heroEnd = Color(0xFF0B4F66),
    cardBorder = Color(0x0F0B2A22),
)

private val DarkSemantic = SemanticColors(
    income = Color(0xFF4ADE80),
    expense = Color(0xFFFF7A7E),
    gold = Color(0xFFF5C26B),
    warning = Color(0xFFFFB648),
    transfer = Color(0xFFA5ADFF),
    heroStart = Color(0xFF0D6B58),
    heroEnd = Color(0xFF0A3647),
    cardBorder = Color(0x14FFFFFF),
)

val LocalSemantic = staticCompositionLocalOf { LightSemantic }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE5),
    onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF3B6B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EEE5),
    onSecondaryContainer = Color(0xFF0B3B31),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9C2),
    onTertiaryContainer = Color(0xFF3D2600),
    error = Color(0xFFD93A3F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF410004),
    background = Color(0xFFF4F6F5),
    onBackground = Color(0xFF141B19),
    surface = Color(0xFFF4F6F5),
    onSurface = Color(0xFF141B19),
    surfaceVariant = Color(0xFFE3E9E6),
    onSurfaceVariant = Color(0xFF59645F),
    outline = Color(0xFF8A9590),
    outlineVariant = Color(0xFFD5DCD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFB),
    surfaceContainer = Color(0xFFEFF3F1),
    surfaceContainerHigh = Color(0xFFE9EEEC),
    surfaceContainerHighest = Color(0xFFE3E9E6),
    inverseSurface = Color(0xFF2A3230),
    inverseOnSurface = Color(0xFFEDF2F0),
    inversePrimary = Color(0xFF5CD6B4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5CD6B4),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF0B5847),
    onPrimaryContainer = Color(0xFFB8F2E0),
    secondary = Color(0xFFA9D2C6),
    onSecondary = Color(0xFF113A31),
    secondaryContainer = Color(0xFF24493F),
    onSecondaryContainer = Color(0xFFC6EFE2),
    tertiary = Color(0xFFF5C26B),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF5C4100),
    onTertiaryContainer = Color(0xFFFFE9C2),
    error = Color(0xFFFFB3AE),
    onError = Color(0xFF68000A),
    errorContainer = Color(0xFF93000F),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFE1E7E4),
    surface = Color(0xFF0E1412),
    onSurface = Color(0xFFE1E7E4),
    surfaceVariant = Color(0xFF2B3431),
    onSurfaceVariant = Color(0xFFA7B2AD),
    outline = Color(0xFF6F7A76),
    outlineVariant = Color(0xFF38423E),
    surfaceContainerLowest = Color(0xFF151C1A),
    surfaceContainerLow = Color(0xFF181F1D),
    surfaceContainer = Color(0xFF1C2422),
    surfaceContainerHigh = Color(0xFF232B29),
    surfaceContainerHighest = Color(0xFF2B3431),
    inverseSurface = Color(0xFFE1E7E4),
    inverseOnSurface = Color(0xFF2A3230),
    inversePrimary = Color(0xFF0E7C66),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Tutarlar için eşit genişlikli rakamlar */
val AmountStyle = TextStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)

@Composable
fun ButcemizTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalSemantic provides if (dark) DarkSemantic else LightSemantic) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object Palette {
    /** Kategori/hesap/üye renk seçenekleri */
    val choices: List<Long> = listOf(
        0xFF0E7C66, 0xFF16A34A, 0xFF65A30D, 0xFF0D9488, 0xFF0891B2, 0xFF0284C7, 0xFF2563EB, 0xFF4F46E5,
        0xFF7C3AED, 0xFF9333EA, 0xFFC026D3, 0xFFDB2777, 0xFFE11D48, 0xFFDC2626, 0xFFEA580C, 0xFFF59E0B,
        0xFFB7791F, 0xFF92400E, 0xFF475569, 0xFF64748B,
    )
    val members: List<Long> = listOf(0xFF2563EB, 0xFFDB2777, 0xFF16A34A, 0xFFEA580C, 0xFF7C3AED, 0xFF0891B2)
}
