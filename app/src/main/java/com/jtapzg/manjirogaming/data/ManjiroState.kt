package com.jtapzg.manjirogaming.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Snapshot do estado publicado pelo daemon Manjiro Dinamic em
 * /data/adb/manjiro_dinamic/state/status.json.
 *
 * Tolerante a campos novos do módulo: ignoramos chaves desconhecidas pra evitar
 * crashes em versões futuras.
 */
@Serializable
data class ManjiroState(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("engine") val engine: String = "manjiro-dinamic",
    @SerialName("mode") val mode: String = "idle",
    @SerialName("mode_label") val modeLabel: String? = null,
    @SerialName("active_mode") val activeMode: String? = null,
    @SerialName("requested_mode") val requestedMode: String? = null,
    @SerialName("soc_temp_c10") val socTempC10: Int? = null,
    @SerialName("battery_temp_c10") val batteryTempC10: Int? = null,
    @SerialName("battery_pct") val batteryPct: Int? = null,
    @SerialName("battery_charging") val batteryCharging: Boolean? = null,
    @SerialName("cpu_load_pct") val cpuLoadPct: Int? = null,
    @SerialName("mem_free_pct") val memFreePct: Int? = null,
    @SerialName("gpu_busy_pct") val gpuBusyPct: Int? = null,
    @SerialName("gpu_busy") val gpuBusyLegacy: Int? = null,
    @SerialName("active_actuators") val activeActuators: List<String> = emptyList(),
    @SerialName("foreground_package") val foregroundPackage: String? = null,
    @SerialName("foreground_label") val foregroundLabel: String? = null,
    @SerialName("game_class") val gameClass: String? = null,
    @SerialName("session_start_ms") val sessionStartMs: Long? = null,
    @SerialName("last_event") val lastEvent: String? = null,
    @SerialName("last_event_ms") val lastEventMs: Long? = null
) {
    val isGameActive: Boolean
        get() = !foregroundPackage.isNullOrBlank() && mode.equals("engaged", ignoreCase = true)

    val socTempCelsius: Double? get() = socTempC10?.let { it / 10.0 }
    val batteryTempCelsius: Double? get() = batteryTempC10?.let { it / 10.0 }
    val gpuPercent: Int? get() = gpuBusyPct ?: gpuBusyLegacy
    val cpuPercent: Int? get() = cpuLoadPct
    val memFreePercent: Int? get() = memFreePct

    val fsmMode: FsmMode get() = FsmMode.parse(mode)

    enum class FsmMode { BOOTSTRAP, IDLE, ACTIVE, ENGAGED, COOLDOWN, SAFE, UNKNOWN;
        companion object {
            fun parse(s: String?): FsmMode = when (s?.lowercase()) {
                "bootstrap" -> BOOTSTRAP
                "idle" -> IDLE
                "active" -> ACTIVE
                "engaged" -> ENGAGED
                "cooldown" -> COOLDOWN
                "safe" -> SAFE
                else -> UNKNOWN
            }
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        fun parse(raw: String?): ManjiroState? = runCatching {
            if (raw.isNullOrBlank()) null else json.decodeFromString<ManjiroState>(raw)
        }.getOrNull()
    }
}
