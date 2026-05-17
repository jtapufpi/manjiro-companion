package com.jtapzg.manjirogaming.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.jtapzg.manjirogaming.data.ManjiroPrefs
import com.jtapzg.manjirogaming.data.ManjiroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sobe o ManjiroService após boot do dispositivo (se "iniciar com o sistema" estiver ligado),
 * e reaplica o modo global salvo no motor.
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> startIfEnabled(context)
        }
    }

    private fun startIfEnabled(context: Context) {
        val prefs = ManjiroPrefs.get(context)
        if (!prefs.startOnBoot) return
        // Sobe foreground service
        val svc = Intent(context, ManjiroService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, svc)
        } else {
            context.startService(svc)
        }
        // Reaplica modo salvo
        val pending = goAsync()
        scope.launch {
            runCatching { ManjiroRepository.get(context).reapplyAfterBoot() }
            pending.finish()
        }
    }
}
