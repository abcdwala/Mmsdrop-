package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF07080E)
val CharcoalSurface = Color(0xFF0F111C)
val CharcoalElevated = Color(0xFF171A2B)
val CharcoalHigh = Color(0xFF21263D)
val BorderSubtle = Color(0xFF2B314D)

val VioletPrimary = Color(0xFF8B5CF6)
val VioletBright = Color(0xFFA78BFA)
val BlueAccent = Color(0xFF3B82F6)
val CyanGlow = Color(0xFF38BDF8)

val TextPrimary = Color(0xFFF5F7FF)
val TextSecondary = Color(0xFF9BA3C2)
val TextMuted = Color(0xFF6B7394)

val SuccessEmerald = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val DangerCrimson = Color(0xFFEF4444)

val BrandGradient = Brush.linearGradient(
  colors = listOf(VioletPrimary, BlueAccent)
)

val HeroOverlayGradient = Brush.verticalGradient(
  colors = listOf(Color(0x3307080E), Color(0xCC07080E), ObsidianBg)
)
