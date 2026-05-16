package dev.jtapzg.manjiro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.R

/** Rajdhani — títulos, vibe gamer (estilo ROG/MSI). */
val RajdhaniFamily = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** JetBrains Mono — números (telemetry vibe). */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val ManjiroTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 32.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,
        fontSize = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, letterSpacing = 0.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = RajdhaniFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.5.sp
    ),
)
