package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PulseIndigo,
    onPrimary = Color.White,
    primaryContainer = PulseIndigoContainer,
    onPrimaryContainer = PulseOnIndigoContainer,
    secondary = PulseMint,
    onSecondary = Color.White,
    secondaryContainer = PulseMintContainer,
    onSecondaryContainer = PulseOnMintContainer,
    tertiary = PulseCoral,
    onTertiary = Color.White,
    tertiaryContainer = PulseCoralContainer,
    onTertiaryContainer = PulseOnCoralContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF151257),
    primaryContainer = PulseIndigoDark,
    onPrimaryContainer = PulseIndigoContainer,
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022C22),
    secondaryContainer = PulseMintDark,
    onSecondaryContainer = PulseMintContainer,
    tertiary = Color(0xFFFB923C),
    onTertiary = Color(0xFF431407),
    tertiaryContainer = PulseCoralDark,
    onTertiaryContainer = PulseCoralContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight
)

val FormPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FormPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FormPulseTypography,
        shapes = FormPulseShapes,
        content = content
    )
}
