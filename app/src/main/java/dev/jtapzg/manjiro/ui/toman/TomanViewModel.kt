package dev.jtapzg.manjiro.ui.toman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jtapzg.manjiro.data.CombatForm
import dev.jtapzg.manjiro.data.GameEntry
import dev.jtapzg.manjiro.data.GameUi
import dev.jtapzg.manjiro.data.ManjiroRepository
import dev.jtapzg.manjiro.data.ManjiroState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resumo do diagnóstico do daemon. */
enum class DaemonStatus {
    UNKNOWN,        // ainda checando
    NO_ROOT,        // libsu não conseguiu shell root
    NO_MODULE,      // /data/adb/manjiro_dinamic não existe
    DORMANT,        // módulo presente mas daemon não publicou status ainda
    ALIVE,          // tudo certo, status.json sendo lido
}

class TomanViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ManjiroRepository.get(app)

    val state: StateFlow<ManjiroState> = repo.state
    val games: StateFlow<List<GameUi>> = repo.games
    val rootGranted: StateFlow<Boolean?> = repo.rootGranted
    val moduleInstalled: StateFlow<Boolean?> = repo.moduleInstalled

    /**
     * Jogos visíveis no grid principal: apenas os que estão **instalados**
     * no celular. O banco do módulo lista ~1700 candidatos pré-cadastrados;
     * isso ficaria poluído demais.
     */
    val installedGames: StateFlow<List<GameUi>> = repo.games
        .map { list -> list.filter { it.installed } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Diagnóstico combinado (root + módulo + estado). */
    val daemonStatus: StateFlow<DaemonStatus> = combine(
        repo.rootGranted, repo.moduleInstalled, repo.state
    ) { root, mod, st ->
        when {
            root == null || mod == null -> DaemonStatus.UNKNOWN
            root == false -> DaemonStatus.NO_ROOT
            mod == false -> DaemonStatus.NO_MODULE
            st.mode.equals("unknown", true) -> DaemonStatus.DORMANT
            else -> DaemonStatus.ALIVE
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DaemonStatus.UNKNOWN)

    private val _selectedGame = MutableStateFlow<GameUi?>(null)
    val selectedGame: StateFlow<GameUi?> = _selectedGame.asStateFlow()

    private val _showAjustes = MutableStateFlow(false)
    val showAjustes: StateFlow<Boolean> = _showAjustes.asStateFlow()

    private val _showRecruit = MutableStateFlow(false)
    val showRecruit: StateFlow<Boolean> = _showRecruit.asStateFlow()

    private val _showCronica = MutableStateFlow(false)
    val showCronica: StateFlow<Boolean> = _showCronica.asStateFlow()

    private val _recruitCandidates = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val recruitCandidates: StateFlow<List<Pair<String, String>>> = _recruitCandidates.asStateFlow()

    init {
        repo.startPolling()
    }

    fun selectGame(g: GameUi?) { _selectedGame.value = g }
    fun showSettings(show: Boolean) { _showAjustes.value = show }
    fun showRecruit(show: Boolean) {
        _showRecruit.value = show
        if (show) viewModelScope.launch {
            _recruitCandidates.value = repo.installedLaunchableApps()
        }
    }
    fun showCronica(show: Boolean) { _showCronica.value = show }

    fun launchGame(pkg: String) {
        viewModelScope.launch { repo.launchGame(pkg) }
    }

    fun toggleEngaged() {
        viewModelScope.launch { repo.toggleEngaged() }
    }

    fun saveGame(entry: GameEntry, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.saveGame(entry)
            onDone()
        }
    }

    fun removeGame(packageName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.removeGame(packageName)
            onDone()
        }
    }

    fun addGame(pkg: String) {
        viewModelScope.launch { repo.addGame(pkg) }
    }

    // ===== Settings exposed =====
    fun hudGlobal(): Boolean = repo.hudGlobalEnabled()
    fun setHudGlobal(b: Boolean) = repo.setHudGlobal(b)
    fun defaultForm(): CombatForm = repo.defaultForm()
    fun setDefaultForm(f: CombatForm) = repo.setDefaultForm(f)
    fun devMode(): Boolean = repo.devModeEnabled()
    fun setDevMode(b: Boolean) = repo.setDevMode(b)
    fun cronicaEnabled(): Boolean = repo.cronicaEnabled()
    fun setCronicaEnabled(b: Boolean) = repo.setCronicaEnabled(b)
}
