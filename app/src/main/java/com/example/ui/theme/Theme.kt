package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DropMmsDarkColorScheme = darkColorScheme(
  primary = VioletPrimary,
  onPrimary = Color.White,
  primaryContainer = CharcoalHigh,
  onPrimaryContainer = VioletBright,
  secondary = BlueAccent,
  onSecondary = Color.White,
  secondaryContainer = CharcoalElevated,
  onSecondaryContainer = CyanGlow,
  tertiary = CyanGlow,
  background = ObsidianBg,
  onBackground = TextPrimary,
  surface = CharcoalSurface,
  onSurface = TextPrimary,
  surfaceVariant = CharcoalElevated,
  onSurfaceVariant = TextSecondary,
  outline = BorderSubtle,
  error = DangerCrimson,
  onError = Color.White,
)

private val DropMmsShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
)

@Composable
fun DropMmsTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DropMmsDarkColorScheme,
    typography = DropMmsTypography,
    shapes = DropMmsShapes,
    content = content,
  )
}
