package com.jtapzg.manjirogaming.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jtapzg.manjirogaming.data.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Trata ações disparadas a partir da notificação persistente.
 *  - ACTION_RECOIL: encerra a sessão do jogo (game_off) e volta pro idle.
 */
class NotifActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        when (intent.action) {
            ACTION_RECOIL -> {
                val pending = goAsync()
                scope.launch {
                    runCatching {
                        if (pkg.isNotBlank()) RootShell.gameOff(pkg)
                    }
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_RECOIL = "com.jtapzg.manjirogaming.ACTION_RECOIL"
        const val EXTRA_PACKAGE = "package"
    }
}
