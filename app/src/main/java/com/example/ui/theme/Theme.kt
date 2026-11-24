package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Navy900,
    onPrimary = PureWhite,
    primaryContainer = Azure100,
    onPrimaryContainer = Navy950,
    secondary = Azure600,
    onSecondary = PureWhite,
    secondaryContainer = Azure50,
    onSecondaryContainer = Navy800,
    tertiary = Navy700,
    onTertiary = PureWhite,
    background = PaperIvory,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = PaperWarm,
    surfaceContainer = Slate100,
    outline = Slate200,
    outlineVariant = Slate200,
    error = ErrorCrimson,
    onError = PureWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorCrimson
)

private val DarkColorScheme = darkColorScheme(
    primary = Azure500,
    onPrimary = Navy950,
    primaryContainer = Navy800,
    onPrimaryContainer = Azure100,
    secondary = Azure100,
    onSecondary = Navy950,
    secondaryContainer = Navy700,
    onSecondaryContainer = PureWhite,
    background = Navy950,
    onBackground = PureWhite,
    surface = Navy900,
    onSurface = PureWhite,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate400,
    outline = Navy700,
    error = ErrorContainer,
    onError = ErrorCrimson
)

val LivroShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LivroIaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LivroShapes,
        content = content
    )
}
