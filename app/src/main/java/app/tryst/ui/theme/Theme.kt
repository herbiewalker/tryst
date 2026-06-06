package app.tryst.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tryst.core.prefs.ThemeMode

private val BrandDarkColors = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = OnPurplePrimaryDark,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = OnPurpleContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = OnGreenSecondaryDark,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = OnGreenContainerDark,
    tertiary = GreenTertiaryDark,
    onTertiary = OnGreenTertiaryDark,
    tertiaryContainer = GreenTertiaryContainerDark,
    onTertiaryContainer = OnGreenTertiaryContainerDark,
)

private val BrandLightColors = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = OnPurplePrimaryLight,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = OnPurpleContainerLight,
    secondary = GreenSecondaryLight,
    onSecondary = OnGreenSecondaryLight,
    secondaryContainer = GreenContainerLight,
    onSecondaryContainer = OnGreenContainerLight,
    tertiary = GreenTertiaryLight,
    onTertiary = OnGreenTertiaryLight,
    tertiaryContainer = GreenTertiaryContainerLight,
    onTertiaryContainer = OnGreenTertiaryContainerLight,
)

@Composable
fun TrystTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Brand palette is the default; opt into Material You per-user in Settings.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BrandDarkColors
        else -> BrandLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
