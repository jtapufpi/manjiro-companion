package com.jtapzg.manjirogaming.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.jtapzg.manjirogaming.launcher.GameLauncher
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.mode.ModeApplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton que centraliza:
 *  - polling do status.json (motor → app)
 *  - escrita do apk_state.json (app → motor) via ModeApplier
 *  - CRUD de games.json
 *  - launch de jogos (via GameLauncher)
 *  - diagnóstico (root / módulo / erros)
 */
class ManjiroRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs: ManjiroPrefs = ManjiroPrefs.get(appContext)
    val modeApplier: ModeApplier = ModeApplier(appContext)
    val launcher: GameLauncher = GameLauncher(appContext, modeApplier)

    private val _state = MutableStateFlow<ManjiroState?>(null)
    val state: StateFlow<ManjiroState?> = _state.asStateFlow()

    private val _games = MutableStateFlow<List<GameUi>>(emptyList())
    val games: StateFlow<List<GameUi>> = _games.asStateFlow()

    private val _diag = MutableStateFlow(Diagnostics())
    val diag: StateFlow<Diagnostics> = _diag.asStateFlow()

    private var pollJob: Job? = null

    data class Diagnostics(
        val hasRoot: Boolean = false,
        val moduleInstalled: Boolean = false,
        val lastError: String? = null
    )

    // ── Polling ─────────────────────────────────────────────────────────
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            // diagnóstico inicial
            val hasRoot = RootShell.ensureRoot()
            val hasModule = RootShell.isModuleInstalled()
            _diag.update { it.copy(hasRoot = hasRoot, moduleInstalled = hasModule) }

            // primeira carga de games
            refreshGames()

            while (true) {
                val raw = RootShell.readStatusJson()
                val parsed = ManjiroState.parse(raw)
                _state.value = parsed
                val intervalMs = when (parsed?.fsmMode) {
                    ManjiroState.FsmMode.ENGAGED, ManjiroState.FsmMode.ACTIVE -> 2_000L
                    ManjiroState.FsmMode.SAFE, ManjiroState.FsmMode.COOLDOWN -> 3_000L
                    else -> 6_000L
                }
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun shutdown() {
        stopPolling()
        scope.cancel()
    }

    // ── Games ───────────────────────────────────────────────────────────
    suspend fun refreshGames(): List<GameUi> = withContext(Dispatchers.IO) {
        val raw = RootShell.readGamesJson()
        val parsed = GamesIo.parse(raw)
        val pm = appContext.packageManager
        val ui = parsed.games.map { entry -> resolveUi(entry, pm) }
        _games.value = ui
        ui
    }

    private fun resolveUi(entry: GameEntry, pm: PackageManager): GameUi {
        val installed = runCatching {
            pm.getApplicationInfo(entry.packageName, 0)
            true
        }.getOrDefault(false)
        val displayName = if (installed) appContext.friendlyAppName(entry.packageName) else entry.packageName
        val icon = if (installed) runCatching { pm.getApplicationIcon(entry.packageName) }.getOrNull() else null
        val intent = if (installed) pm.getLaunchIntentForPackage(entry.packageName) else null
        val stats = prefs.gameStats(entry.packageName)
        return GameUi(
            packageName = entry.packageName,
            displayName = displayName,
            installed = installed,
            icon = icon,
            entry = entry,
            launchIntent = intent,
            lastPlayedMs = stats.lastPlayedMs,
            totalPlayedMs = stats.totalMs
        )
    }

    suspend fun saveGames(updated: List<GameEntry>): Boolean = withContext(Dispatchers.IO) {
        val file = GamesFile(games = updated)
        val ok = RootShell.writeGamesJson(GamesIo.encode(file))
        if (ok) {
            // recarrega state em memória pra refletir a mudança imediatamente
            refreshGames()
            RootShell.reload()
        }
        ok
    }

    suspend fun addGame(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parse(it) } ?: GamesFile())
            .games.toMutableList()
        if (current.any { it.packageName == packageName }) return@withContext true
        val newEntry = GameEntry(packageName = packageName)
        current.add(newEntry)
        saveGames(current)
    }

    suspend fun removeGame(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parse(it) } ?: GamesFile())
            .games.filterNot { it.packageName == packageName }
        saveGames(current)
    }

    suspend fun updateGame(updated: GameEntry): Boolean = withContext(Dispatchers.IO) {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parse(it) } ?: GamesFile())
            .games.toMutableList()
        val idx = current.indexOfFirst { it.packageName == updated.packageName }
        if (idx >= 0) current[idx] = updated else current.add(updated)
        saveGames(current)
    }

    suspend fun launchGame(game: GameUi): GameLauncher.LaunchResult = withContext(Dispatchers.IO) {
        launcher.launch(game.entry, _state.value)
    }

    suspend fun freeMemory(): Boolean = launcher.freeMemoryOnly(_state.value)

    // ── Modo ────────────────────────────────────────────────────────────
    suspend fun applyMode(mode: Mode): ModeApplier.Result =
        modeApplier.applyGlobal(mode, _state.value)

    suspend fun reapplyAfterBoot(): ModeApplier.Result =
        modeApplier.reapplyCurrent(_state.value)

    // ── App enumeration pro picker ─────────────────────────────────────
    suspend fun installedLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        runCatching {
            pm.queryIntentActivities(intent, 0)
                .map {
                    InstalledApp(
                        packageName = it.activityInfo.packageName,
                        displayName = it.loadLabel(pm).toString(),
                        icon = it.loadIcon(pm)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.displayName.lowercase() }
        }.onFailure { Log.w(TAG, "installedApps", it) }.getOrDefault(emptyList())
    }

    data class InstalledApp(
        val packageName: String,
        val displayName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    // ── Sessão / estatísticas ──────────────────────────────────────────
    fun recordSession(pkg: String, durationMs: Long, mode: Mode, peakTempC10: Int) {
        prefs.recordSession(pkg, durationMs, mode, peakTempC10)
        // dispara refresh leve de games pra atualizar lastPlayedMs
        scope.launch { refreshGames() }
    }

    companion object {
        private const val TAG = "MgRepo"

        @Volatile private var INSTANCE: ManjiroRepository? = null
        fun get(context: Context): ManjiroRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ManjiroRepository(context).also { INSTANCE = it }
            }
    }
}
