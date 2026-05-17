package com.jtapzg.manjirogaming.mode

import android.content.Context
import android.util.Log
import com.jtapzg.manjirogaming.data.ManjiroState
import com.jtapzg.manjirogaming.data.ManjiroPrefs
import com.jtapzg.manjirogaming.data.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Aplica modos no motor Manjiro Dinamic.
 *
 * Estratégia em camadas:
 *
 *  1) Atualiza SharedPreferences (modo global do APK).
 *  2) Escreve `apk_state.json` no diretório do módulo. O daemon do módulo
 *     lê esse arquivo e usa como *override* (preferência declarada pelo APK).
 *  3) Envia comando `mode_set <key>` via `control.cmd` (fila simples).
 *
 * Se algum dos passos 2/3 falhar (ex.: sem root), a UI ainda funciona — só
 * sinaliza que o motor não pôde ser reconfigurado.
 */
class ModeApplier(private val context: Context) {

    private val prefs = ManjiroPrefs.get(context)
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    data class ApkState(
        @SerialName("schema_version") val schemaVersion: Int = 1,
        @SerialName("source") val source: String = "manjiro_gaming_app",
        @SerialName("mode") val mode: String,
        @SerialName("auto") val auto: Boolean = false,
        @SerialName("effective_mode") val effectiveMode: String = mode,
        @SerialName("user_name") val userName: String = "",
        @SerialName("foreground_package") val foregroundPackage: String? = null,
        @SerialName("foreground_quality") val foregroundQuality: Int? = null,
        @SerialName("updated_ms") val updatedMs: Long = System.currentTimeMillis()
    )

    data class Result(val ok: Boolean, val rootApplied: Boolean, val effective: Mode) {
        val modeAppliedLocally: Boolean get() = ok
    }

    /** Aplica modo global. Persistente — vira o "modo padrão" do APK. */
    suspend fun applyGlobal(
        mode: Mode,
        currentDaemonState: ManjiroState? = null
    ): Result = withContext(Dispatchers.IO) {
        prefs.globalMode = mode
        val effective = if (mode == Mode.AUTO) {
            AutoMode.decide(currentDaemonState)
        } else mode

        val state = ApkState(
            mode = mode.key,
            auto = mode == Mode.AUTO,
            effectiveMode = effective.key,
            userName = prefs.userName,
            foregroundPackage = currentDaemonState?.foregroundPackage
        )

        val rootApplied = pushToDaemon(state)
        Result(ok = true, rootApplied = rootApplied, effective = effective)
    }

    /** Aplica modo só por sessão (ex.: ao abrir um jogo) sem mexer no padrão. */
    suspend fun applySessionFor(
        pkg: String,
        mode: Mode,
        quality: Int,
        currentDaemonState: ManjiroState? = null
    ): Result = withContext(Dispatchers.IO) {
        val effective = if (mode == Mode.AUTO) AutoMode.decide(currentDaemonState) else mode

        val state = ApkState(
            mode = prefs.globalMode.key,
            auto = prefs.globalMode == Mode.AUTO,
            effectiveMode = effective.key,
            userName = prefs.userName,
            foregroundPackage = pkg,
            foregroundQuality = quality
        )

        val rootApplied = pushToDaemon(state) &&
            RootShell.gameOn(pkg, effective.profileKey)
        Result(ok = true, rootApplied = rootApplied, effective = effective)
    }

    /** Reaplica o estado actual (pra usar após boot completed). */
    suspend fun reapplyCurrent(currentDaemonState: ManjiroState? = null): Result =
        applyGlobal(prefs.globalMode, currentDaemonState)

    private suspend fun pushToDaemon(state: ApkState): Boolean {
        val encoded = runCatching { json.encodeToString(ApkState.serializer(), state) }
            .getOrElse {
                Log.w(TAG, "encode apk_state", it)
                return false
            }
        val wrote = RootShell.writeApkStateJson(encoded)
        val sent = RootShell.applyMode(state.effectiveMode)
        return wrote && sent
    }

    companion object { private const val TAG = "MgModeApplier" }
}
