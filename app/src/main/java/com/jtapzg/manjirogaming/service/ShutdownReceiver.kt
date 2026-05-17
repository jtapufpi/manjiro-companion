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
 * Em shutdown, sinaliza ao motor que a sessão atual encerrou (se houver).
 * Isso evita que o daemon mantenha estado "engaged" ao próximo boot.
 */
class ShutdownReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            runCatching { RootShell.sendCommand("shutdown") }
            pending.finish()
        }
    }
}
