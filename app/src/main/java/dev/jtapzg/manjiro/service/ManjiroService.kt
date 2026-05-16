package dev.jtapzg.manjiro.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.jtapzg.manjiro.data.FsmMode
import dev.jtapzg.manjiro.data.ManjiroRepository
import dev.jtapzg.manjiro.data.ManjiroState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import dev.jtapzg.manjiro.R

class ManjiroService : LifecycleService() {

    private lateinit var repo: ManjiroRepository
    private lateinit var nm: NotificationManager
    private var lastSessionStartMs = 0L
    private var sessionPeakC10 = 0
    private var sessionStartBatPct = -1
    private var sessionPkg: String? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Notif.ensureChannels(this, nm)
        repo = ManjiroRepository.get(this)
        repo.startPolling()

        startInForeground(ManjiroState.UNKNOWN)
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startInForeground(state: ManjiroState) {
        val notif = Notif.buildEstado(
            this, state, foregroundLabel = null, nowMs = System.currentTimeMillis()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notif.NOTIF_ID_ESTADO, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notif.NOTIF_ID_ESTADO, notif)
        }
    }

    private fun observeState() {
        repo.state
            .onEach { s ->
                // Atualiza notificação
                val foregroundLabel = labelFor(s.foregroundPackage)
                val notif = Notif.buildEstado(
                    this, s, foregroundLabel, System.currentTimeMillis()
                )
                nm.notify(Notif.NOTIF_ID_ESTADO, notif)

                // Tracking de sessão (pra emitir crônica no fim)
                trackSession(s)
            }
            .launchIn(lifecycleScope)
    }

    private fun trackSession(s: ManjiroState) {
        val pkg = s.foregroundPackage
        val sessionMs = s.sessionStartMs
        // Start de sessão
        if (sessionMs > 0 && sessionMs != lastSessionStartMs && pkg != null) {
            lastSessionStartMs = sessionMs
            sessionPeakC10 = s.socTempC10
            sessionStartBatPct = s.batteryPct
            sessionPkg = pkg
        }
        // Update pico
        if (sessionMs > 0 && pkg != null && sessionMs == lastSessionStartMs) {
            if (s.socTempC10 > sessionPeakC10) sessionPeakC10 = s.socTempC10
        }
        // End de sessão (session_start volta a 0 OU package mudou)
        if (lastSessionStartMs > 0 && (sessionMs == 0L || (pkg != null && pkg != sessionPkg))) {
            val durMs = System.currentTimeMillis() - lastSessionStartMs
            if (durMs > 30_000L && sessionPkg != null && repo.cronicaEnabled()) {
                val drain = if (sessionStartBatPct in 0..100 && s.batteryPct in 0..100)
                    (sessionStartBatPct - s.batteryPct).coerceAtLeast(0) else 0
                val label = labelFor(sessionPkg) ?: sessionPkg!!
                Notif.postCronica(
                    this, nm,
                    gameLabel = label,
                    durationMs = durMs,
                    peakSocC10 = sessionPeakC10,
                    avgSocC10 = sessionPeakC10, // (não temos média ainda; usa pico como placeholder)
                    batDrainPct = drain,
                    fps = null,
                )
                repo.recordSession(sessionPkg!!, durMs, sessionPeakC10)
            }
            // reset
            lastSessionStartMs = 0L
            sessionPeakC10 = 0
            sessionStartBatPct = -1
            sessionPkg = null
            lifecycleScope.launch { repo.refreshGames() }
        }
    }

    private fun labelFor(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return runCatching {
            val pm = packageManager
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrNull() ?: pkg
    }

    override fun onDestroy() {
        super.onDestroy()
        repo.stopPolling()
    }
}
