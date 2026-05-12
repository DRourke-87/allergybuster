package com.drourke.allergybuster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Nature theme is always applied — dynamic color is intentionally skipped
// so the spring/summer palette remains consistent across all Android versions.

private val LightColors = lightColorScheme(
    primary                = ForestGreen,
    onPrimary              = OnForestGreen,
    primaryContainer       = SpringLeafContainer,
    onPrimaryContainer     = OnSpringLeaf,

    secondary              = BarkBrown,
    onSecondary            = OnBarkBrown,
    secondaryContainer     = SandyBarkContainer,
    onSecondaryContainer   = OnSandyBark,

    tertiary               = SunlightGold,
    onTertiary             = OnSunlightGold,
    tertiaryContainer      = GoldenMeadow,
    onTertiaryContainer    = OnGoldenMeadow,

    error                  = TerracottaError,
    onError                = OnTerracottaError,
    errorContainer         = RustContainer,
    onErrorContainer       = OnRustContainer,

    background             = ParchmentBg,
    onBackground           = OnSpringLeaf,
    surface                = WarmWhiteSurface,
    onSurface              = OnSpringLeaf,
    surfaceVariant         = SageVariant,
    onSurfaceVariant       = EarthOutline,
    outline                = EarthOutline
)

private val DarkColors = darkColorScheme(
    primary                = TwilightLeaf,
    onPrimary              = OnTwilightLeaf,
    primaryContainer       = DarkCanopy,
    onPrimaryContainer     = OnDarkCanopy,

    secondary              = MoonlitSand,
    onSecondary            = OnMoonlitSand,
    secondaryContainer     = DarkBark,
    onSecondaryContainer   = OnDarkBark,

    tertiary               = EmberGold,
    onTertiary             = OnEmberGold,
    tertiaryContainer      = DeepAmber,
    onTertiaryContainer    = OnDeepAmber,

    error                  = EmberError,
    onError                = OnEmberError,
    errorContainer         = DeepCharError,
    onErrorContainer       = OnDeepChar,

    background             = ForestFloorBg,
    onBackground           = OnDarkCanopy,
    surface                = DarkBarkSurface,
    onSurface              = OnDarkCanopy,
    surfaceVariant         = MutedMoss,
    onSurfaceVariant       = MutedOutline,
    outline                = MutedOutline
)

private val NatureTypography = Typography(
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun AllergyBusterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = NatureTypography,
        content     = content
    )
}
