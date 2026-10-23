package com.TopMob.bookmt.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.TopMob.bookmt.domain.model.ReaderTheme

val LocalComfortPalette = staticCompositionLocalOf { ComfortPalettes.Day }

/** Palette ownership is explicit; all reader children inherit the reading palette. */
@Composable
fun BookMTTheme(
    theme: ReaderTheme = if (isSystemInDarkTheme()) ReaderTheme.NIGHT else ReaderTheme.DAY,
    content: @Composable () -> Unit,
) {
    val palette = ComfortPalettes.forTheme(theme)
    val scheme = remember(palette) { palette.materialColors() }
    CompositionLocalProvider(LocalComfortPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography, shapes = ComfortShapes) {
            CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(palette.accent, palette.selection)) {
                content()
            }
        }
    }
}

private val ComfortShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp),
)

internal fun ComfortPalette.materialColors(): ColorScheme = lightColorScheme(
    primary = accent, onPrimary = onAccent, primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
    secondary = accent, onSecondary = onAccent, secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer,
    tertiary = accent, onTertiary = onAccent, tertiaryContainer = accentContainer, onTertiaryContainer = onAccentContainer,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = surfaceInset, onSurfaceVariant = secondaryText,
    surfaceDim = background, surfaceBright = surfaceRaised,
    surfaceContainerLowest = background, surfaceContainerLow = surface,
    surfaceContainer = surface, surfaceContainerHigh = surfaceRaised, surfaceContainerHighest = surfaceInset,
    outline = border, outlineVariant = divider, error = error, onError = onError,
    errorContainer = errorContainer, onErrorContainer = error,
    inverseSurface = inverseSurface, inverseOnSurface = inverseText, inversePrimary = inverseAccent,
    primaryFixed = accentContainer, primaryFixedDim = accentContainer,
    onPrimaryFixed = onAccentContainer, onPrimaryFixedVariant = onAccentContainer,
    secondaryFixed = accentContainer, secondaryFixedDim = accentContainer,
    onSecondaryFixed = onAccentContainer, onSecondaryFixedVariant = onAccentContainer,
    tertiaryFixed = accentContainer, tertiaryFixedDim = accentContainer,
    onTertiaryFixed = onAccentContainer, onTertiaryFixedVariant = onAccentContainer,
    surfaceTint = accent, scrim = scrim,
)
