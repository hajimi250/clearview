package com.chengying.clearview.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val ClearViewTypography = Typography(
    displayLarge = ClearViewType.display,
    displayMedium = ClearViewType.display,
    displaySmall = ClearViewType.display,
    headlineLarge = ClearViewType.display,
    headlineMedium = ClearViewType.title,
    headlineSmall = ClearViewType.title,
    titleLarge = ClearViewType.title,
    titleMedium = ClearViewType.heading,
    titleSmall = ClearViewType.heading,
    bodyLarge = ClearViewType.body,
    bodyMedium = ClearViewType.body,
    bodySmall = ClearViewType.bodySmall,
    labelLarge = ClearViewType.label,
    labelMedium = ClearViewType.label,
    labelSmall = ClearViewType.caption,
)

private val MaterialShapes = Shapes(
    extraSmall = ClearViewShapes.small,
    small = ClearViewShapes.small,
    medium = ClearViewShapes.medium,
    large = ClearViewShapes.card,
    extraLarge = ClearViewShapes.card,
)

@Composable
fun ClearViewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkClearViewColors else LightClearViewColors
    val inverse = if (darkTheme) LightClearViewColors else DarkClearViewColors
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.info,
        onPrimary = colors.onInfo,
        primaryContainer = colors.infoContainer,
        onPrimaryContainer = colors.text1,
        inversePrimary = inverse.info,
        secondary = colors.text2,
        onSecondary = colors.surface1,
        secondaryContainer = colors.surface2,
        onSecondaryContainer = colors.text1,
        tertiary = colors.text2,
        onTertiary = colors.surface1,
        tertiaryContainer = colors.surface2,
        onTertiaryContainer = colors.text1,
        background = colors.background,
        onBackground = colors.text1,
        surface = colors.surface1,
        onSurface = colors.text1,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.text2,
        surfaceTint = colors.transparent,
        inverseSurface = inverse.surface1,
        inverseOnSurface = inverse.text1,
        error = colors.critical,
        onError = colors.onCritical,
        errorContainer = colors.criticalContainer,
        onErrorContainer = colors.text1,
        outline = colors.lineStrong,
        outlineVariant = colors.line,
        scrim = colors.imageCanvas,
        surfaceBright = colors.surface3,
        surfaceDim = colors.background,
        surfaceContainer = colors.surface1,
        surfaceContainerHigh = colors.surface2,
        surfaceContainerHighest = colors.surface3,
        surfaceContainerLow = colors.background,
        surfaceContainerLowest = colors.canvas,
    )
    CompositionLocalProvider(
        LocalClearViewColors provides colors,
        LocalTonalElevationEnabled provides false,
    ) {
        MaterialTheme(colorScheme = scheme, typography = ClearViewTypography, shapes = MaterialShapes, content = content)
    }
}
