package com.jtapzg.manjirogaming.mode

import com.jtapzg.manjirogaming.data.ManjiroState

/**
 * Decide qual modo *concreto* o "Automático" deve aplicar com base em
 * temperatura e bateria reportadas pelo motor.
 *
 * Regras (gerais):
 *  - Bateria <= 20%       → LITE
 *  - SoC >= 46 °C         → LITE (frear pra esfriar)
 *  - SoC <= 38 °C E bateria >= 60% E carregando → PERFORMANCE
 *  - Caso contrário                            → BALANCED
 */
object AutoMode {
    fun decide(state: ManjiroState?): Mode {
        if (state == null) return Mode.BALANCED
        val bat = state.batteryPct ?: 100
        val soc10 = state.socTempC10 ?: 0
        val charging = state.batteryCharging ?: false

        if (bat in 1..20) return Mode.LITE
        if (soc10 >= 460) return Mode.LITE
        if (soc10 in 1..380 && bat >= 60 && charging) return Mode.PERFORMANCE
        return Mode.BALANCED
    }
}
