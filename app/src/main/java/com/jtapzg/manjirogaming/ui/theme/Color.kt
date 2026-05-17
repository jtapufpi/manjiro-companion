package com.jtapzg.manjirogaming.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta inspirada no Game Space (RedMagic).
 * Fundo preto profundo, vermelho de destaque com glow, superfícies acinzentadas.
 */
object MgColors {
    val Bg = Color(0xFF0A0A0A)
    val Card = Color(0xFF1C1C1E)
    val CardHigh = Color(0xFF242428)
    val Border = Color(0xFF2A2A2A)

    val Red = Color(0xFFFF2222)
    val RedDim = Color(0xFFB81818)
    val RedGlow = Color(0x55FF2222)

    val Amber = Color(0xFFF4C43B)
    val Green = Color(0xFF36C597)
    val Blue = Color(0xFF4FC3F7)
    val Hot = Color(0xFFEF5350)
    val Warn = Color(0xFFFFB74D)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextTertiary = Color(0xFF5A6068)
}

/** Cor de "saúde" pra barras de métrica: verde > 60, amarelo 30–60, vermelho < 30. */
fun healthColor(percent: Int): Color = when {
    percent >= 60 -> MgColors.Green
    percent >= 30 -> MgColors.Warn
    else -> MgColors.Hot
}

/** Cor pra temperatura em décimos de Celsius (do daemon). */
fun temperatureColor(tempC10: Int?): Color = when {
    tempC10 == null -> MgColors.TextSecondary
    tempC10 >= 450 -> MgColors.Hot          // >= 45.0 °C
    tempC10 >= 400 -> MgColors.Warn         // >= 40.0 °C
    tempC10 >= 250 -> MgColors.Green        // >= 25.0 °C
    else -> MgColors.Blue                   // frio
}

/** Cor pra nível de bateria. */
fun batteryColor(percent: Int?): Color = when {
    percent == null -> MgColors.TextSecondary
    percent <= 15 -> MgColors.Hot
    percent <= 30 -> MgColors.Warn
    else -> MgColors.Green
}
