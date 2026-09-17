package com.example.jarvisai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisai.domain.model.AppThemeMode

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = JarvisOnPrimary,
    primaryContainer = JarvisPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = JarvisPrimaryDark,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurfaceVariant,
    onSecondaryContainer = JarvisTextPrimary,
    tertiary = JarvisAccentGreen,
    onTertiary = Color.Black,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    surfaceTint = JarvisPrimary,
    outline = JarvisBorder,
    outlineVariant = JarvisBorderSubtle,
    error = JarvisAccentRed,
    onError = Color.White
)

private val JarvisAmoledColorScheme = darkColorScheme(
    primary = JarvisPrimary,
    onPrimary = JarvisOnPrimary,
    primaryContainer = Color(0xFF00363A),
    onPrimaryContainer = JarvisPrimaryLight,
    secondary = JarvisPrimaryDark,
    onSecondary = Color.White,
    background = Color.Black,
    onBackground = JarvisTextPrimary,
    surface = Color(0xFF080808),
    onSurface = JarvisTextPrimary,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = JarvisTextSecondary,
    outline = Color(0xFF222222),
    outlineVariant = Color(0xFF181818),
    error = JarvisAccentRed,
    onError = Color.White
)

private val JarvisCyberColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F28),
    primaryContainer = Color(0xFF0D2538),
    onPrimaryContainer = Color(0xFF80D8FF),
    secondary = Color(0xFF2979FF),
    onSecondary = Color.White,
    background = Color(0xFF0B0F19),
    onBackground = JarvisTextPrimary,
    surface = Color(0xFF111827),
    onSurface = JarvisTextPrimary,
    surfaceVariant = Color(0xFF1F293D),
    onSurfaceVariant = JarvisTextSecondary,
    outline = Color(0xFF2B3A52),
    outlineVariant = Color(0xFF1A2332),
    error = JarvisAccentRed,
    onError = Color.White
)

val JarvisTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp,
        color = JarvisTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 1.sp,
        color = JarvisTextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = JarvisTextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = JarvisTextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = JarvisTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextTertiary
    )
)

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun JarvisAiTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK_JARVIS,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK_JARVIS -> JarvisDarkColorScheme
        AppThemeMode.AMOLED_BLUE -> JarvisAmoledColorScheme
        AppThemeMode.CYBER_TACTICAL -> JarvisCyberColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
