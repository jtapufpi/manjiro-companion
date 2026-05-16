package dev.jtapzg.manjiro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Representa o snapshot atual do motor Manjiro Dinamic.
 * Lido de `/data/adb/manjiro_dinamic/state/status.json` via root shell.
 *
 * Mantenha em sincronia com o schema do daemon `manjirod`.
 * Campos opcionais usam default pra tolerar versões antigas do módulo.
 */
@Serializable
data class ManjiroState(
    val version: Int = 0,
    val engine: String? = null,
    val mode: String = "idle",
    @SerialName("soc_temp_c10") val socTempC10: Int = 0,
    @SerialName("battery_temp_c10") val batteryTempC10: Int = 0,
    @SerialName("battery_emergency_c10") val batteryEmergencyC10: Int = 0,
    @SerialName("battery_pct") val batteryPct: Int = -1,
    @SerialName("gpu_busy") val gpuBusy: Int = -1,
    @SerialName("current_ma") val currentMa: Int = 0,
    @SerialName("oscillation_count") val oscillationCount: Int = 0,
    @SerialName("lyapunov_v") val lyapunovV: Int = 0,
    @SerialName("cap_prime_pct") val capPrimePct: Int = 100,
    @SerialName("cap_gold_pct") val capGoldPct: Int = 100,
    @SerialName("cap_gpu_pct") val capGpuPct: Int = 100,
    @SerialName("active_actuators") val activeActuators: List<String> = emptyList(),
    @SerialName("foreground_package") val foregroundPackage: String? = null,
    @SerialName("game_class") val gameClass: String? = null,
    @SerialName("temp_c10") val tempC10: Int = 0,
    @SerialName("temp_pred_5s_c10") val tempPred5sC10: Int = 0,
    @SerialName("temp_pred_10s_c10") val tempPred10sC10: Int = 0,
    @SerialName("last_action") val lastAction: String? = null,
    @SerialName("actuation_budget") val actuationBudget: Int = 0,
    @SerialName("safe_reason") val safeReason: String? = null,
    @SerialName("session_start_ms") val sessionStartMs: Long = 0,
    @SerialName("updated_ms") val updatedMs: Long = 0,
    @SerialName("module_version") val moduleVersion: String? = null,
) {
    val isGameActive: Boolean get() = foregroundPackage != null && mode == "engaged"
    val socTempCelsius: Float get() = socTempC10 / 10f
    val batteryTempCelsius: Float get() = batteryTempC10 / 10f

    companion object {
        val UNKNOWN = ManjiroState(mode = "unknown")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun parse(raw: String): ManjiroState? = runCatching {
            json.decodeFromString(serializer(), raw)
        }.getOrNull()
    }
}

enum class FsmMode(val daemonKey: String) {
    BOOTSTRAP("bootstrap"),
    IDLE("idle"),
    ACTIVE("active"),
    ENGAGED("engaged"),
    COOLDOWN("cooldown"),
    SAFE("safe"),
    UNKNOWN("unknown");

    companion object {
        fun from(s: String?): FsmMode =
            entries.firstOrNull { it.daemonKey == s?.lowercase() } ?: UNKNOWN
    }
}
