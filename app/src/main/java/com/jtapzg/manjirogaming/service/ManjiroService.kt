package com.jtapzg.manjirogaming.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.jtapzg.manjirogaming.data.ManjiroRepository
import com.jtapzg.manjirogaming.data.ManjiroState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service que:
 *  - Mantém polling do daemon (via Repository)
 *  - Mantém a notificação persistente sempre atualizada
 *  - Detecta início/fim de sessão de jogo e grava estatísticas
 */
class ManjiroService : LifecycleService() {

    private lateinit var repo: ManjiroRepository

    private var currentSessionPkg: String? = null
    private var currentSessionStartMs: Long = 0
    private var currentSessionPeakTempC10: Int = 0
    private var currentSessionMode: com.jtapzg.manjirogaming.mode.Mode? = null

    override fun onCreate() {
        super.onCreate()
        repo = ManjiroRepository.get(this)
        Notif.ensureChannels(this)
        startForegroundCompat()
        repo.startPolling()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // re-promove ao foreground em caso de relaunch
        startForegroundCompat()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = Notif.buildSession(this, repo.state.value, repo.prefs.globalMode)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    Notif.NOTIF_ID_SESSION,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(Notif.NOTIF_ID_SESSION, notif)
            }
        } catch (t: Throwable) {
            // Fallback genérico: ainda mostrar notificação mesmo se promote falhar.
            Log.w(TAG, "startForeground falhou", t)
            try { startForeground(Notif.NOTIF_ID_SESSION, notif) } catch (_: Throwable) {}
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repo.state.collectLatest { state ->
                updateNotification(state)
                trackSession(state)
            }
        }
    }

    private fun updateNotification(state: ManjiroState?) {
        if (!repo.prefs.notificationsEnabled) return
        val notif = Notif.buildSession(this, state, repo.prefs.globalMode)
        try {
            val nm = androidx.core.content.ContextCompat.getSystemService(
                this, android.app.NotificationManager::class.java
            )
            nm?.notify(Notif.NOTIF_ID_SESSION, notif)
        } catch (t: Throwable) {
            Log.w(TAG, "updateNotification", t)
        }
    }

    private fun trackSession(state: ManjiroState?) {
        if (state == null) return
        val isGame = state.isGameActive
        val pkg = state.foregroundPackage

        // Atualiza pico de temperatura da sessão em curso
        if (currentSessionPkg != null) {
            val t = state.socTempC10 ?: 0
            if (t > currentSessionPeakTempC10) currentSessionPeakTempC10 = t
        }

        when {
            isGame && pkg != null && currentSessionPkg != pkg -> {
                // Inicia nova sessão (e fecha anterior se diferente)
                closeSessionIfAny()
                currentSessionPkg = pkg
                currentSessionStartMs = System.currentTimeMillis()
                currentSessionPeakTempC10 = state.socTempC10 ?: 0
                currentSessionMode = state.activeMode?.let { com.jtapzg.manjirogaming.mode.Mode.fromKey(it) }
                    ?: repo.prefs.globalMode
            }
            !isGame && currentSessionPkg != null -> {
                closeSessionIfAny()
            }
        }
    }

    private fun closeSessionIfAny() {
        val pkg = currentSessionPkg ?: return
        val durationMs = System.currentTimeMillis() - currentSessionStartMs
        if (durationMs > 5_000) {
            repo.recordSession(
                pkg = pkg,
                durationMs = durationMs,
                mode = currentSessionMode ?: repo.prefs.globalMode,
                peakTempC10 = currentSessionPeakTempC10
            )
        }
        currentSessionPkg = null
        currentSessionStartMs = 0
        currentSessionPeakTempC10 = 0
        currentSessionMode = null
    }

    override fun onDestroy() {
        closeSessionIfAny()
        repo.stopPolling()
        super.onDestroy()
    }

    companion object { private const val TAG = "MgService" }
}
