package com.jtapzg.manjirogaming.mode

import androidx.annotation.StringRes
import com.jtapzg.manjirogaming.R

/**
 * Modos que o app expõe ao usuário.
 *
 * - [key]: token usado em arquivos JSON / control.cmd (não traduzido).
 * - [labelRes]: nome amigável visível na UI.
 * - [descRes]: descrição curta (linguagem de gamer).
 * - [profileKey]: nome do perfil que o daemon entende ao aplicar comandos do jogo
 *   ("balanced" é o fallback seguro).
 *
 * AUTO é tratado de forma especial: o app escolhe um modo real (LITE / BALANCED /
 * PERFORMANCE) com base em bateria/temperatura e aplica esse modo no motor.
 */
enum class Mode(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val descRes: Int,
    val profileKey: String
) {
    LITE("lite", R.string.mode_lite, R.string.mode_lite_desc, "lite"),
    BALANCED("balanced", R.string.mode_balanced, R.string.mode_balanced_desc, "balanced"),
    PERFORMANCE("performance", R.string.mode_performance, R.string.mode_performance_desc, "performance"),
    AUTO("auto", R.string.mode_auto, R.string.mode_auto_desc, "balanced");

    companion object {
        fun fromKey(key: String?): Mode = when (key?.lowercase()) {
            "lite", "eco", "battery" -> LITE
            "performance", "perf", "max" -> PERFORMANCE
            "auto" -> AUTO
            else -> BALANCED
        }

        val DEFAULT: Mode = BALANCED
    }
}
