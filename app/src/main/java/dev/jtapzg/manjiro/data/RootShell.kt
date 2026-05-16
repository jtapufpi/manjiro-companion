package dev.jtapzg.manjiro.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper sobre libsu pro daemon Manjiro Dinamic.
 *
 * Convenções com o daemon (`manjirod`):
 * - status:  cat /data/adb/manjiro_dinamic/state/status.json
 * - games:   cat /data/adb/manjiro_dinamic/config/games.json
 * - control: echo "<cmd>" >> /data/adb/manjiro_dinamic/state/control.cmd
 *
 * Comandos suportados no control.cmd (uma linha por comando):
 *   game_on <package>     # força ENGAGED pra esse jogo
 *   game_off              # libera Modo Mikey manual
 *   safe_clear            # tira do refúgio (se o motor concordar)
 *   profile_set <pkg> <profile>
 *   reload                # recarrega games.json
 */
object RootShell {

    private const val STATE_DIR = "/data/adb/manjiro_dinamic/state"
    private const val CONFIG_DIR = "/data/adb/manjiro_dinamic/config"
    private const val STATUS = "$STATE_DIR/status.json"
    private const val CONTROL = "$STATE_DIR/control.cmd"
    private const val GAMES = "$CONFIG_DIR/games.json"

    init {
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    /** Tenta abrir shell root; true se a permissão foi concedida. */
    suspend fun ensureRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Shell.getShell().isRoot }.getOrDefault(false)
    }

    /** Verifica se o módulo Manjiro Dinamic está presente. */
    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val r = Shell.cmd("test -d /data/adb/manjiro_dinamic && echo yes || echo no").exec()
            r.isSuccess && r.out.firstOrNull()?.trim() == "yes"
        }.getOrDefault(false)
    }

    /** Lê o status.json bruto (null se não houver). */
    suspend fun readStatusJson(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val r = Shell.cmd("cat $STATUS 2>/dev/null").exec()
            if (r.isSuccess) r.out.joinToString("\n").takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    /** Lê o games.json bruto (null se não houver). */
    suspend fun readGamesJson(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val r = Shell.cmd("cat $GAMES 2>/dev/null").exec()
            if (r.isSuccess) r.out.joinToString("\n").takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    /** Sobrescreve games.json com novo conteúdo. */
    suspend fun writeGamesJson(content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = "/data/local/tmp/games.json.tmp"
            val escaped = content.replace("'", "'\\''")
            val r = Shell.cmd(
                "mkdir -p $CONFIG_DIR",
                "printf '%s' '$escaped' > $tmp",
                "cat $tmp > $GAMES",
                "chmod 0644 $GAMES",
                "rm -f $tmp",
                "echo reload >> $CONTROL"
            ).exec()
            r.isSuccess
        }.getOrDefault(false)
    }

    /** Envia comando pro daemon (game_on/game_off/safe_clear/profile_set/...). */
    suspend fun sendCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val escaped = cmd.replace("'", "'\\''").trim()
            val r = Shell.cmd(
                "mkdir -p $STATE_DIR",
                "echo '$escaped' >> $CONTROL"
            ).exec()
            r.isSuccess
        }.getOrDefault(false)
    }

    suspend fun gameOn(packageName: String) = sendCommand("game_on $packageName")
    suspend fun gameOff() = sendCommand("game_off")
    suspend fun safeClear() = sendCommand("safe_clear")
    suspend fun setProfile(packageName: String, profile: String) =
        sendCommand("profile_set $packageName $profile")

    /** Caminho útil pra mostrar no Ajustes diagnóstico. */
    val statusPath: String get() = STATUS
    val gamesPath: String get() = GAMES
    val controlPath: String get() = CONTROL
}
