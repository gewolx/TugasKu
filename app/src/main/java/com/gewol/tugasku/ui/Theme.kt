package com.gewol.tugasku.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.gewol.tugasku.R

const val DEFAULT_THEME_COLOR = 1

val Amber = Color(0xFFF59E0B)
val Teal = Color(0xFF14B8A6)

private val ErrorLight = Color(0xFFE5484D)
private val ErrorDark = Color(0xFFFF6B70)
private val InkLight = Color(0xFF14151A)

private fun lightPalette(
    primary: Long,
    container: Long,
    onContainer: Long,
    background: Long,
    variant: Long,
    onVariant: Long,
    outline: Long
) = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color.White,
    primaryContainer = Color(container),
    onPrimaryContainer = Color(onContainer),
    secondary = Teal,
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(onContainer),
    tertiaryContainer = Color(container),
    onTertiaryContainer = Color(onContainer),
    background = Color(background),
    onBackground = InkLight,
    surface = Color.White,
    onSurface = InkLight,
    surfaceVariant = Color(variant),
    onSurfaceVariant = Color(onVariant),
    outline = Color(outline),
    outlineVariant = Color(outline),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(background),
    surfaceContainerHigh = Color(variant),
    surfaceContainerHighest = Color(variant),
    error = ErrorLight
)

private fun darkPalette(
    primary: Long,
    onPrimary: Long,
    container: Long,
    onContainer: Long,
    background: Long,
    surface: Long,
    variant: Long,
    onVariant: Long,
    outline: Long,
    ink: Long
) = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(container),
    onPrimaryContainer = Color(onContainer),
    secondary = Color(0xFF2DD4BF),
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(onContainer),
    tertiaryContainer = Color(container),
    onTertiaryContainer = Color(onContainer),
    background = Color(background),
    onBackground = Color(ink),
    surface = Color(surface),
    onSurface = Color(ink),
    surfaceVariant = Color(variant),
    onSurfaceVariant = Color(onVariant),
    outline = Color(outline),
    outlineVariant = Color(outline),
    surfaceContainerLowest = Color(background),
    surfaceContainerLow = Color(surface),
    surfaceContainer = Color(surface),
    surfaceContainerHigh = Color(variant),
    surfaceContainerHighest = Color(variant),
    error = ErrorDark
)

class AppPalette(
    val nameRes: Int,
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme,
    val hero: List<Color>
)

val APP_PALETTES = listOf(

    AppPalette(
        nameRes = R.string.theme_color_purple,
        swatch = Color(0xFF5B5BD6),
        light = lightPalette(0xFF5B5BD6, 0xFFE7E7FF, 0xFF1B1B66, 0xFFF6F7FB, 0xFFECEEF5, 0xFF5E6272, 0xFFC4C7D4),
        dark = darkPalette(0xFF9494FF, 0xFF14143D, 0xFF2A2B5C, 0xFFE2E2FF, 0xFF0E0F14, 0xFF1A1B23, 0xFF262833, 0xFFA6A9B8, 0xFF4A4D5E, 0xFFEDEEF3),
        hero = listOf(Color(0xFF5B5BD6), Color(0xFF8B5CF6))
    ),

    AppPalette(
        nameRes = R.string.theme_color_blue,
        swatch = Color(0xFF1B6FF5),
        light = lightPalette(0xFF1B6FF5, 0xFFDBEAFE, 0xFF0B2A66, 0xFFF3F7FD, 0xFFE6EDF8, 0xFF55627A, 0xFFC3CDE0),
        dark = darkPalette(0xFF8AB4FF, 0xFF06214D, 0xFF1E3A6B, 0xFFD6E6FF, 0xFF0B1018, 0xFF151C28, 0xFF222C3D, 0xFFA3B0C7, 0xFF3E4C66, 0xFFE8EEF8),
        hero = listOf(Color(0xFF1558D6), Color(0xFF3B8BFF))
    ),

    AppPalette(
        nameRes = R.string.theme_color_teal,
        swatch = Color(0xFF0F8F86),
        light = lightPalette(0xFF0F8F86, 0xFFD5F5F1, 0xFF023B37, 0xFFF1F8F8, 0xFFE1EFEE, 0xFF566B69, 0xFFBCD3D1),
        dark = darkPalette(0xFF5EEAD4, 0xFF02332E, 0xFF0F4A44, 0xFFCCFBF1, 0xFF0A1211, 0xFF121D1C, 0xFF1E2C2B, 0xFFA0B8B5, 0xFF3A5350, 0xFFE3F0EE),
        hero = listOf(Color(0xFF0F766E), Color(0xFF14B8A6))
    ),

    AppPalette(
        nameRes = R.string.theme_color_green,
        swatch = Color(0xFF1E8E4E),
        light = lightPalette(0xFF1E8E4E, 0xFFD9F5E3, 0xFF073B1D, 0xFFF2F8F4, 0xFFE3EFE7, 0xFF566A5D, 0xFFBFD3C6),
        dark = darkPalette(0xFF6EDC9A, 0xFF04301A, 0xFF14472B, 0xFFD3F7E1, 0xFF0B120E, 0xFF141D18, 0xFF202C25, 0xFFA2B5A9, 0xFF3D5246, 0xFFE6F0EA),
        hero = listOf(Color(0xFF15803D), Color(0xFF22A55A))
    ),

    AppPalette(
        nameRes = R.string.theme_color_orange,
        swatch = Color(0xFFEA580C),
        light = lightPalette(0xFFEA580C, 0xFFFFE8D6, 0xFF5C2200, 0xFFFBF7F4, 0xFFF3ECE6, 0xFF6B6058, 0xFFD8CCC3),
        dark = darkPalette(0xFFFFB27A, 0xFF3D1A00, 0xFF5A2A0A, 0xFFFFE0CC, 0xFF120E0B, 0xFF1E1814, 0xFF2D2520, 0xFFB8AAA0, 0xFF574A40, 0xFFF0E9E4),
        hero = listOf(Color(0xFFD9480F), Color(0xFFF2711C))
    ),

    AppPalette(
        nameRes = R.string.theme_color_pink,
        swatch = Color(0xFFD6336C),
        light = lightPalette(0xFFD6336C, 0xFFFFDDE9, 0xFF5C0A28, 0xFFFDF4F7, 0xFFF5E6EC, 0xFF6E5560, 0xFFDCC3CD),
        dark = darkPalette(0xFFFF8FB5, 0xFF4D0A25, 0xFF6B1F3F, 0xFFFFD9E6, 0xFF140B0F, 0xFF21151A, 0xFF31202A, 0xFFBBA3AE, 0xFF5C4450, 0xFFF3E8ED),
        hero = listOf(Color(0xFFBE2A5E), Color(0xFFE04C86))
    ),

    AppPalette(
        nameRes = R.string.theme_color_red,
        swatch = Color(0xFFDC2626),
        light = lightPalette(0xFFDC2626, 0xFFFEE2E2, 0xFF5C0A0A, 0xFFFDF5F5, 0xFFF5E8E8, 0xFF6E5858, 0xFFDCC4C4),
        dark = darkPalette(0xFFFF8A8A, 0xFF4D0606, 0xFF6B1A1A, 0xFFFFDAD6, 0xFF140B0B, 0xFF211515, 0xFF312020, 0xFFBBA3A3, 0xFF5C4444, 0xFFF3E8E8),
        hero = listOf(Color(0xFFB91C1C), Color(0xFFEF4444))
    ),

    AppPalette(
        nameRes = R.string.theme_color_amber,
        swatch = Color(0xFFD97706),
        light = lightPalette(0xFFB45309, 0xFFFEF3C7, 0xFF4A2A00, 0xFFFCF9F3, 0xFFF3EDE0, 0xFF6B6350, 0xFFD9CFB8),
        dark = darkPalette(0xFFFBBF24, 0xFF3D2A00, 0xFF5A3F0A, 0xFFFDE9B0, 0xFF120F0A, 0xFF1E1A12, 0xFF2D281C, 0xFFB8AF98, 0xFF574E3A, 0xFFF0ECE2),
        hero = listOf(Color(0xFFB45309), Color(0xFFD97706))
    ),

    AppPalette(
        nameRes = R.string.theme_color_sky,
        swatch = Color(0xFF0284C7),
        light = lightPalette(0xFF0284C7, 0xFFE0F2FE, 0xFF042F4A, 0xFFF2F8FC, 0xFFE3EEF5, 0xFF566878, 0xFFBFD2E0),
        dark = darkPalette(0xFF7DD3FC, 0xFF032B44, 0xFF0B4A6F, 0xFFD3EEFF, 0xFF0A1118, 0xFF121C26, 0xFF1D2A38, 0xFFA0B4C6, 0xFF3A5068, 0xFFE6EFF7),
        hero = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9))
    ),

    AppPalette(
        nameRes = R.string.theme_color_violet,
        swatch = Color(0xFF7C3AED),
        light = lightPalette(0xFF7C3AED, 0xFFEDE4FF, 0xFF2E1065, 0xFFF8F5FD, 0xFFEEE8F7, 0xFF645A78, 0xFFCFC4E0),
        dark = darkPalette(0xFFC4A5FF, 0xFF2A0F5C, 0xFF42278A, 0xFFEBDDFF, 0xFF100C16, 0xFF1B1624, 0xFF2A2338, 0xFFB3A8C6, 0xFF50446A, 0xFFF0EBF7),
        hero = listOf(Color(0xFF6D28D9), Color(0xFFA855F7))
    ),

    AppPalette(
        nameRes = R.string.theme_color_brown,
        swatch = Color(0xFF8D5A3B),
        light = lightPalette(0xFF8D5A3B, 0xFFF6E4D8, 0xFF3E1F0D, 0xFFFAF6F2, 0xFFF0E7DF, 0xFF6B5D53, 0xFFD6C6BA),
        dark = darkPalette(0xFFE0B596, 0xFF3A1E0C, 0xFF5A3A24, 0xFFF6E0D0, 0xFF120E0B, 0xFF1D1713, 0xFF2C241E, 0xFFB8ABA0, 0xFF55473C, 0xFFF0E9E3),
        hero = listOf(Color(0xFF7A4A2E), Color(0xFFA8714F))
    ),

    AppPalette(
        nameRes = R.string.theme_color_slate,
        swatch = Color(0xFF475569),
        light = lightPalette(0xFF475569, 0xFFE2E8F0, 0xFF0F172A, 0xFFF5F7FA, 0xFFE8ECF1, 0xFF5B6573, 0xFFC6CDD8),
        dark = darkPalette(0xFFB4C0D3, 0xFF1B2433, 0xFF334155, 0xFFE2E8F0, 0xFF0E1116, 0xFF171B22, 0xFF242A33, 0xFFA6AEBB, 0xFF444D5B, 0xFFEAEDF2),
        hero = listOf(Color(0xFF334155), Color(0xFF64748B))
    )
)

val PALETTE_DISPLAY_ORDER: List<Int> =
    listOf(DEFAULT_THEME_COLOR) + APP_PALETTES.indices.filter { it != DEFAULT_THEME_COLOR }

val LocalHeroGradient = staticCompositionLocalOf { APP_PALETTES[DEFAULT_THEME_COLOR].hero }

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun TugasKuTheme(themeMode: Int, themeColor: Int = DEFAULT_THEME_COLOR, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val palette = APP_PALETTES.getOrElse(themeColor) { APP_PALETTES[DEFAULT_THEME_COLOR] }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalHeroGradient provides palette.hero) {
        MaterialTheme(
            colorScheme = if (dark) palette.dark else palette.light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
