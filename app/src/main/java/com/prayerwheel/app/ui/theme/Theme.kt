package com.prayerwheel.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime

private val LightColorScheme = lightColorScheme(
    primary = Gold40,
    onPrimary = Gold10,
    primaryContainer = Gold90,
    onPrimaryContainer = Gold10,
    secondary = Bronze40,
    onSecondary = Bronze10,
    secondaryContainer = Bronze90,
    onSecondaryContainer = Bronze10,
    tertiary = Crimson40,
    onTertiary = Crimson10,
    tertiaryContainer = Crimson90,
    onTertiaryContainer = Crimson10,
    error = Error80,
    onError = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
)

private val DarkColorScheme = darkColorScheme(
    primary = Gold80,
    onPrimary = Gold20,
    primaryContainer = Gold30,
    onPrimaryContainer = Gold90,
    secondary = Bronze80,
    onSecondary = Bronze20,
    secondaryContainer = Bronze30,
    onSecondaryContainer = Bronze90,
    tertiary = Crimson80,
    onTertiary = Crimson20,
    tertiaryContainer = Crimson30,
    onTertiaryContainer = Crimson90,
    error = Error90,
    onError = Error10,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant60,
)

// Sepia theme - warm brown/tan background, dark brown text, golden accents
private val SepiaColorScheme = lightColorScheme(
    primary = Gold50,
    onPrimary = Gold10,
    primaryContainer = Color(0xFFE8D5A3),
    onPrimaryContainer = Color(0xFF3D2B1F),
    secondary = Bronze50,
    onSecondary = Bronze10,
    secondaryContainer = Color(0xFFD4C4A3),
    onSecondaryContainer = Color(0xFF3D2B1F),
    tertiary = Crimson50,
    onTertiary = Crimson10,
    tertiaryContainer = Color(0xFFE8C4C4),
    onTertiaryContainer = Color(0xFF3D2B1F),
    error = Error80,
    onError = Error10,
    background = Color(0xFFF5E6C8),
    onBackground = Color(0xFF3D2B1F),
    surface = Color(0xFFF5E6C8),
    onSurface = Color(0xFF3D2B1F),
    surfaceVariant = Color(0xFFE8D5A3),
    onSurfaceVariant = Color(0xFF5D4B3F),
    outline = Color(0xFF8B7355),
)

// Dawn-Dusk dynamic theme - follows system time
private fun getDawnDuskColorScheme(hour: Int): androidx.compose.material3.ColorScheme {
    // Dawn: 5-7, Day: 7-17, Dusk: 17-20, Night: 20-5
    return when (hour) {
        in 5..6 -> lightColorScheme(
            primary = Gold30,
            onPrimary = Gold90,
            primaryContainer = Gold80,
            onPrimaryContainer = Gold10,
            secondary = Bronze30,
            onSecondary = Bronze90,
            secondaryContainer = Bronze80,
            onSecondaryContainer = Bronze10,
            tertiary = Crimson30,
            onTertiary = Crimson90,
            tertiaryContainer = Crimson80,
            onTertiaryContainer = Crimson10,
            background = Color(0xFFFAF5EB),
            onBackground = Color(0xFF2D2D2D),
            surface = Color(0xFFFAF5EB),
            onSurface = Color(0xFF2D2D2D),
            surfaceVariant = Color(0xFFE8E0D0),
            onSurfaceVariant = Color(0xFF4D4D4D),
            outline = Color(0xFF8D8D8D),
        )
        in 7..16 -> lightColorScheme(
            primary = Gold40,
            onPrimary = Gold10,
            primaryContainer = Gold90,
            onPrimaryContainer = Gold10,
            secondary = Bronze40,
            onSecondary = Bronze10,
            secondaryContainer = Bronze90,
            onSecondaryContainer = Bronze10,
            tertiary = Crimson40,
            onTertiary = Crimson10,
            tertiaryContainer = Crimson90,
            onTertiaryContainer = Crimson10,
            background = Neutral99,
            onBackground = Neutral10,
            surface = Neutral99,
            onSurface = Neutral10,
            surfaceVariant = NeutralVariant90,
            onSurfaceVariant = NeutralVariant30,
            outline = NeutralVariant50,
        )
        in 17..19 -> darkColorScheme(
            primary = Gold70,
            onPrimary = Gold10,
            primaryContainer = Gold40,
            onPrimaryContainer = Gold90,
            secondary = Bronze70,
            onSecondary = Bronze10,
            secondaryContainer = Bronze40,
            onSecondaryContainer = Bronze90,
            tertiary = Crimson70,
            onTertiary = Crimson10,
            tertiaryContainer = Crimson40,
            onTertiaryContainer = Crimson90,
            background = Color(0xFF1D1D2D),
            onBackground = Color(0xFFE8E0D0),
            surface = Color(0xFF1D1D2D),
            onSurface = Color(0xFFE8E0D0),
            surfaceVariant = Color(0xFF2D2D3D),
            onSurfaceVariant = Color(0xFFC8C0B0),
            outline = Color(0xFF6D6D7D),
        )
        else -> darkColorScheme(
            primary = Gold80,
            onPrimary = Gold20,
            primaryContainer = Gold30,
            onPrimaryContainer = Gold90,
            secondary = Bronze80,
            onSecondary = Bronze20,
            secondaryContainer = Bronze30,
            onSecondaryContainer = Bronze90,
            tertiary = Crimson80,
            onTertiary = Crimson20,
            tertiaryContainer = Crimson30,
            onTertiaryContainer = Crimson90,
            error = Error90,
            onError = Error10,
            background = Neutral10,
            onBackground = Neutral90,
            surface = Neutral10,
            onSurface = Neutral90,
            surfaceVariant = NeutralVariant30,
            onSurfaceVariant = NeutralVariant80,
            outline = NeutralVariant60,
        )
    }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SEPIA,
    DAWN_DUSK,
    SYSTEM
}

private val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun PrayerWheelTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.SEPIA -> SepiaColorScheme
        ThemeMode.DAWN_DUSK -> getDawnDuskColorScheme(LocalTime.now().hour)
        ThemeMode.SYSTEM -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
