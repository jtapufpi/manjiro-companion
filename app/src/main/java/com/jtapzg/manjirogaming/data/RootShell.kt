package com.jtapzg.manjirogaming.data

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Camada fina de comunicação com o módulo Manjiro Dinamic via root shell.
 *
 * Caminhos abaixo são o contrato do módulo (1.7.3+):
 *   /data/adb/manjiro_dinamic/state/status.json   ← módulo escreve
 *   /data/adb/manjiro_dinamic/state/control.cmd   → APK escreve (fila simples)
 *   /data/adb/manjiro_dinamic/state/apk_state.json → APK escreve (estado / modo escolhido)
 *   /data/adb/manjiro_dinamic/config/games.json   ↔ ambos
 */
object RootShell {

    private const val TAG = "MgRootShell"

    private const val MODULE_DIR = "/data/adb/manjiro_dinamic"
    const val STATUS_PATH = "$MODULE_DIR/state/status.json"
    const val CONTROL_PATH = "$MODULE_DIR/state/control.cmd"
    const val APK_STATE_PATH = "$MODULE_DIR/state/apk_state.json"
    const val GAMES_PATH = "$MODULE_DIR/config/games.json"
    const val MODULE_PROP_PATH = "$MODULE_DIR/module.prop"

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    suspend fun ensureRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        SuFile(MODULE_PROP_PATH).exists()
    }

    suspend fun readText(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val f = SuFile(path)
            if (!f.exists()) return@runCatching null
            SuFileInputStream.open(f).use { input ->
                BufferedReader(InputStreamReader(input)).use { it.readText() }
            }
        }.onFailure { Log.w(TAG, "readText($path)", it) }.getOrNull()
    }

    suspend fun writeText(path: String, content: String, makeParents: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val f = SuFile(path)
                if (makeParents) f.parentFile?.mkdirs()
                SuFileOutputStream.open(f).use { it.write(content.toByteArray()) }
                true
            }.onFailure { Log.w(TAG, "writeText($path)", it) }.getOrDefault(false)
        }

    suspend fun appendLine(path: String, line: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val safe = line.replace("\"", "\\\"")
            // Usa shell pra append atômico (echo >> via su)
            val cmd = "mkdir -p \"$(dirname $path)\" && printf '%s\\n' \"$safe\" >> $path"
            val res = Shell.cmd(cmd).exec()
            res.isSuccess
        }.onFailure { Log.w(TAG, "appendLine($path)", it) }.getOrDefault(false)
    }

    suspend fun readStatusJson(): String? = readText(STATUS_PATH)
    suspend fun readApkStateJson(): String? = readText(APK_STATE_PATH)
    suspend fun writeApkStateJson(json: String): Boolean = writeText(APK_STATE_PATH, json)
    suspend fun readGamesJson(): String? = readText(GAMES_PATH)
    suspend fun writeGamesJson(json: String): Boolean = writeText(GAMES_PATH, json)

    /** Enfileira um comando pro daemon. Daemon consome control.cmd e remove a linha. */
    suspend fun sendCommand(cmd: String): Boolean = appendLine(CONTROL_PATH, cmd)

    /** Açõezinhas de alto nível (cada uma fica como linha em control.cmd). */
    suspend fun gameOn(pkg: String, profile: String? = null): Boolean =
        sendCommand("game_on $pkg" + (profile?.let { " $it" } ?: ""))

    suspend fun gameOff(pkg: String): Boolean = sendCommand("game_off $pkg")

    suspend fun safeClear(): Boolean = sendCommand("safe_clear")

    suspend fun setProfile(pkg: String, profile: String): Boolean =
        sendCommand("profile_set $pkg $profile")

    suspend fun reload(): Boolean = sendCommand("reload")

    /** Aplica modo global do app via daemon. */
    suspend fun applyMode(modeKey: String, source: String = "apk"): Boolean =
        sendCommand("mode_set $modeKey source=$source")

    /** Atalho útil pra testar shell sem comando complicado. */
    suspend fun rootPing(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.cmd("id").exec().isSuccess }.getOrDefault(false)
    }

    @Throws(IOException::class)
    suspend fun execLine(line: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val res = Shell.cmd(line).exec()
        res.code to (res.out.joinToString("\n") + if (res.err.isNotEmpty()) "\n" + res.err.joinToString("\n") else "")
    }
}
