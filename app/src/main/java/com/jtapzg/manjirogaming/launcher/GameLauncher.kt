package com.jtapzg.manjirogaming.launcher

import android.content.Context
import android.content.Intent
import android.util.Log
import com.jtapzg.manjirogaming.data.GameEntry
import com.jtapzg.manjirogaming.data.ManjiroState
import com.jtapzg.manjirogaming.data.RootShell
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.mode.ModeApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Inicia jogos com pré-boost:
 *  - aplica modo do jogo (via ModeApplier.applySessionFor)
 *  - opcionalmente limpa caches (drop_caches via root) e dispara safe_clear no daemon
 *  - tenta abrir o jogo com a Intent normal (PackageManager) ou fallback `am start --user`
 */
class GameLauncher(
    private val context: Context,
    private val modeApplier: ModeApplier
) {

    data class LaunchResult(
        val launched: Boolean,
        val rootBoosted: Boolean,
        val effectiveMode: Mode,
        val memoryCleared: Boolean
    )

    suspend fun launch(
        game: GameEntry,
        currentDaemonState: ManjiroState? = null,
        preBoostDelayMs: Long = 250
    ): LaunchResult = withContext(Dispatchers.IO) {
        // 1) Limpar memória se configurado
        var cleared = false
        if (game.mgCleanMemory) {
            cleared = RootShell.safeClear()
            // drop_caches direto também, redundante mas garante alívio mesmo se daemon ocupado
            runCatching { RootShell.execLine("sync; echo 3 > /proc/sys/vm/drop_caches || true") }
        }

        // 2) Aplicar modo do jogo
        val mode = game.mode()
        val applied = modeApplier.applySessionFor(
            pkg = game.packageName,
            mode = mode,
            quality = game.mgQuality,
            currentDaemonState = currentDaemonState
        )

        // 3) Pequeno delay pro daemon assentar
        if (preBoostDelayMs > 0) delay(preBoostDelayMs)

        // 4) Abrir o jogo
        val launched = openApp(game.packageName)

        LaunchResult(
            launched = launched,
            rootBoosted = applied.rootApplied,
            effectiveMode = applied.effective,
            memoryCleared = cleared
        )
    }

    /** Limpa memória sem abrir jogo (botão "Liberar memória"). */
    suspend fun freeMemoryOnly(currentDaemonState: ManjiroState? = null): Boolean = withContext(Dispatchers.IO) {
        val a = RootShell.safeClear()
        val b = runCatching {
            RootShell.execLine("sync; echo 3 > /proc/sys/vm/drop_caches || true").first == 0
        }.getOrDefault(false)
        a || b
    }

    private fun openApp(pkg: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                // Fallback via root (caso package esteja só em /system ou similar)
                val res = kotlinx.coroutines.runBlocking {
                    RootShell.execLine("monkey -p $pkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
                }
                res.first == 0
            }
        }.onFailure { Log.w(TAG, "openApp($pkg)", it) }.getOrDefault(false)
    }

    companion object { private const val TAG = "MgLauncher" }
}
