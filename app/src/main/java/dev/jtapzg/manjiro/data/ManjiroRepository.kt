package dev.jtapzg.manjiro.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Faz polling do status.json + cache de games.json + lista de pacotes instalados.
 *
 * Polling adaptativo:
 *   - mode == engaged: 2s (queremos UI viva durante jogo)
 *   - mode == active/cooldown: 3s
 *   - mode == idle/bootstrap/unknown: 6s
 *   - mode == safe: 1s (alerta crítico)
 */
class ManjiroRepository(private val ctx: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("manjiro", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ManjiroState.UNKNOWN)
    val state: StateFlow<ManjiroState> = _state.asStateFlow()

    private val _games = MutableStateFlow<List<GameUi>>(emptyList())
    val games: StateFlow<List<GameUi>> = _games.asStateFlow()

    private val _rootGranted = MutableStateFlow<Boolean?>(null)
    val rootGranted: StateFlow<Boolean?> = _rootGranted.asStateFlow()

    private val _moduleInstalled = MutableStateFlow<Boolean?>(null)
    val moduleInstalled: StateFlow<Boolean?> = _moduleInstalled.asStateFlow()

    @Volatile private var polling = false

    fun startPolling() {
        if (polling) return
        polling = true
        scope.launch {
            _rootGranted.value = RootShell.ensureRoot()
            _moduleInstalled.value = RootShell.isModuleInstalled()
            refreshGames()
            while (polling) {
                val raw = RootShell.readStatusJson()
                val parsed = raw?.let { ManjiroState.parse(it) }
                if (parsed != null) _state.value = parsed
                val nextDelayMs = when (parsed?.mode?.lowercase()) {
                    "engaged" -> 2_000L
                    "safe" -> 1_000L
                    "active", "cooldown" -> 3_000L
                    else -> 6_000L
                }
                delay(nextDelayMs)
            }
        }
    }

    fun stopPolling() { polling = false }

    suspend fun refreshGames() {
        val raw = RootShell.readGamesJson()
        val entries = raw?.let { GamesIo.parseGames(it) }.orEmpty()
        val pm = ctx.packageManager
        val list = entries.map { entry ->
            val label = runCatching { pm.getApplicationInfo(entry.packageName, 0) }
                .map { pm.getApplicationLabel(it).toString() }
                .getOrDefault(entry.packageName)
            val installed = runCatching { pm.getApplicationInfo(entry.packageName, 0) }.isSuccess
            val launchIntent = runCatching { pm.getLaunchIntentForPackage(entry.packageName) }
                .getOrNull()?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            GameUi(
                entry = entry,
                displayName = label,
                installed = installed,
                totalPlayMs = prefs.getLong("play_total_${entry.packageName}", 0),
                lastSessionMs = prefs.getLong("play_last_${entry.packageName}", 0),
                lastTempC10 = prefs.getInt("temp_last_${entry.packageName}", 0),
                launchIntent = launchIntent,
            )
        }.sortedWith(
            compareByDescending<GameUi> { it.lastSessionMs }
                .thenBy { it.displayName.lowercase() }
        )
        _games.value = list
    }

    /** Adiciona um jogo ao games.json e re-publica. */
    suspend fun addGame(packageName: String, gameClass: String = "user_added"): Boolean {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parseGames(it) } ?: emptyList())
            .filterNot { it.packageName == packageName }
        val next = current + GameEntry(packageName = packageName, gameClass = gameClass)
        val ok = RootShell.writeGamesJson(GamesIo.serializeGames(next))
        if (ok) refreshGames()
        return ok
    }

    /** Atualiza/salva uma entrada. */
    suspend fun saveGame(entry: GameEntry): Boolean {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parseGames(it) } ?: emptyList())
            .filterNot { it.packageName == entry.packageName }
        val next = current + entry
        val ok = RootShell.writeGamesJson(GamesIo.serializeGames(next))
        if (ok) refreshGames()
        return ok
    }

    /** Remove um jogo. */
    suspend fun removeGame(packageName: String): Boolean {
        val current = (RootShell.readGamesJson()?.let { GamesIo.parseGames(it) } ?: emptyList())
            .filterNot { it.packageName == packageName }
        val ok = RootShell.writeGamesJson(GamesIo.serializeGames(current))
        if (ok) refreshGames()
        return ok
    }

    /** Toggle Modo Mikey: se já tá engaged, manda recuar; senão, engaja o jogo top. */
    suspend fun toggleEngaged() {
        val s = _state.value
        if (s.mode.equals("engaged", true)) {
            RootShell.gameOff()
        } else {
            val pkg = s.foregroundPackage ?: _games.value.firstOrNull()?.packageName
            if (pkg != null) RootShell.gameOn(pkg) else RootShell.gameOn("none")
        }
    }

    suspend fun launchGame(pkg: String) {
        RootShell.gameOn(pkg)
        delay(250) // pré-boost
        withContext(Dispatchers.Main) {
            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) ctx.startActivity(intent)
        }
    }

    /** Listagem de candidatos pra recrutar (apps com launcher intent). */
    suspend fun installedLaunchableApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.GET_ACTIVITIES
        val resolved = pm.queryIntentActivities(intent, flags)
        resolved.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isSystem) return@mapNotNull null
            ai.packageName to pm.getApplicationLabel(ai).toString()
        }.distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    /** Atualiza estatísticas locais ao final de uma sessão. */
    fun recordSession(packageName: String, durationMs: Long, peakTempC10: Int) {
        val prevTotal = prefs.getLong("play_total_$packageName", 0)
        prefs.edit {
            putLong("play_total_$packageName", prevTotal + durationMs)
            putLong("play_last_$packageName", System.currentTimeMillis())
            putInt("temp_last_$packageName", peakTempC10)
        }
    }

    // ===== Preferences expostas pra Ajustes =====
    fun hudGlobalEnabled(): Boolean = prefs.getBoolean("hud_global", true)
    fun setHudGlobal(value: Boolean) { prefs.edit { putBoolean("hud_global", value) } }

    fun defaultForm(): CombatForm =
        CombatForm.fromKey(prefs.getString("default_form", "mikey"))
    fun setDefaultForm(form: CombatForm) {
        prefs.edit { putString("default_form", form.daemonProfile) }
    }

    fun devModeEnabled(): Boolean = prefs.getBoolean("dev_mode", false)
    fun setDevMode(value: Boolean) { prefs.edit { putBoolean("dev_mode", value) } }

    fun cronicaEnabled(): Boolean = prefs.getBoolean("cronica_enabled", true)
    fun setCronicaEnabled(value: Boolean) { prefs.edit { putBoolean("cronica_enabled", value) } }

    companion object {
        @Volatile private var instance: ManjiroRepository? = null
        fun get(ctx: Context): ManjiroRepository =
            instance ?: synchronized(this) {
                instance ?: ManjiroRepository(ctx.applicationContext).also { instance = it }
            }
    }
}
