package dev.jtapzg.manjiro.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta Toman
val TomanBg = Color(0xFF0A0A0D)
val TomanSurface = Color(0xFF15161B)
val TomanSurfaceHigh = Color(0xFF1E2026)
val TomanRed = Color(0xFFE63946)
val TomanRedDark = Color(0xFFA21B2B)
val TomanRedGlow = Color(0x66E63946)
val MikeyGold = Color(0xFFF4C43B)
val MikeyGoldDark = Color(0xFFB38912)

val StateGood = Color(0xFF36C597)
val StateWarn = Color(0xFFFFB74D)
val StateHot = Color(0xFFEF5350)
val StateCold = Color(0xFF4FC3F7)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9099A2)
val TextTertiary = Color(0xFF5A6068)

/** Cor pra termômetro: verde -> amarelo -> vermelho conforme temperatura. */
fun tempColor(c10: Int): Color {
    val t = c10 / 10f
    return when {
        t <= 0 -> TextTertiary
        t < 70 -> StateGood
        t < 85 -> StateWarn
        else -> StateHot
    }
}

/** Cor da Disciplina (lyapunov). Menor = melhor. */
fun disciplineColor(lyapunovV: Int): Color = when {
    lyapunovV < 0 -> TextTertiary
    lyapunovV < 1500 -> StateGood
    lyapunovV < 4000 -> MikeyGold
    lyapunovV < 7000 -> StateWarn
    else -> StateHot
}
