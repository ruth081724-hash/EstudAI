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

private val DarkColorScheme = darkColorScheme(
    primary = SkyCobaltPrimary,
    onPrimary = Color(0xFF09152B),
    primaryContainer = DeepCobaltContainer,
    onPrimaryContainer = SoftSkyOnContainer,
    secondary = GoldSecondary,
    onSecondary = Color(0xFF331B00),
    secondaryContainer = DeepAmberContainer,
    onSecondaryContainer = SoftGoldOnContainer,
    tertiary = MintTertiary,
    onTertiary = Color(0xFF002E20),
    tertiaryContainer = DeepEmeraldContainer,
    onTertiaryContainer = SoftMintOnContainer,
    background = DeepInkBackground,
    onBackground = IvoryOnDarkSurface,
    surface = MidnightSurface,
    onSurface = IvoryOnDarkSurface,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MistOnDarkVariant,
    outline = MidnightOutline,
    error = SoftCoralError,
    errorContainer = DeepCrimsonContainer,
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = AmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = IvoryBackground,
    onBackground = InkOnSurface,
    surface = PureSurface,
    onSurface = InkOnSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    outline = WarmOutline,
    error = CrimsonError,
    errorContainer = CrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EstudIATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
