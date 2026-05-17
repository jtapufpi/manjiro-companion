package com.jtapzg.manjirogaming.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameEntry
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.data.ManjiroRepository
import com.jtapzg.manjirogaming.data.friendlyAppName
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.service.Notif
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = getApplication<Application>().applicationContext
    private val repo = ManjiroRepository.get(ctx)

    val state = repo.state
    val games = repo.games
    val diag = repo.diag
    val prefs = repo.prefs

    val effectiveMode: StateFlow<Mode> = combine(state, MutableStateFlow(repo.prefs.globalMode)) { s, _ ->
        val m = repo.prefs.globalMode
        if (m == Mode.AUTO) com.jtapzg.manjirogaming.mode.AutoMode.decide(s) else m
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.prefs.globalMode)

    private val _globalMode = MutableStateFlow(repo.prefs.globalMode)
    val globalMode: StateFlow<Mode> = _globalMode.asStateFlow()

    private val _userName = MutableStateFlow(repo.prefs.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _notifEnabled = MutableStateFlow(repo.prefs.notificationsEnabled)
    val notifEnabled: StateFlow<Boolean> = _notifEnabled.asStateFlow()

    private val _bootEnabled = MutableStateFlow(repo.prefs.startOnBoot)
    val bootEnabled: StateFlow<Boolean> = _bootEnabled.asStateFlow()

    private val feedback = Channel<String>(capacity = Channel.BUFFERED)
    val feedbackFlow = feedback.receiveAsFlow()

    init {
        repo.startPolling()
    }

    fun setName(value: String) {
        val trimmed = value.trim()
        prefs.userName = trimmed
        _userName.value = trimmed
        emit(
            if (trimmed.isBlank()) ctx.getString(R.string.fb_name_cleared)
            else ctx.getString(R.string.fb_name_saved, trimmed)
        )
    }

    fun setNotifEnabled(value: Boolean) {
        prefs.notificationsEnabled = value
        _notifEnabled.value = value
        emit(if (value) ctx.getString(R.string.fb_notif_on) else ctx.getString(R.string.fb_notif_off))
    }

    fun setBootEnabled(value: Boolean) {
        prefs.startOnBoot = value
        _bootEnabled.value = value
        emit(if (value) ctx.getString(R.string.fb_boot_on) else ctx.getString(R.string.fb_boot_off))
    }

    fun applyGlobalMode(mode: Mode) {
        viewModelScope.launch {
            val res = repo.applyMode(mode)
            _globalMode.value = mode
            val label = ctx.getString(mode.labelRes)
            val msg = if (res.rootApplied) {
                ctx.getString(R.string.fb_mode_applied, label)
            } else {
                ctx.getString(R.string.fb_mode_applied_root_fail, label)
            }
            emit(msg)
            if (_notifEnabled.value) {
                Notif.postEvent(ctx, msg)
            }
        }
    }

    fun addGame(packageName: String) {
        viewModelScope.launch {
            val name = ctx.friendlyAppName(packageName)
            val ok = repo.addGame(packageName)
            emit(
                if (ok) ctx.getString(R.string.fb_game_added, name)
                else ctx.getString(R.string.fb_error_generic)
            )
        }
    }

    fun removeGame(game: GameUi) {
        viewModelScope.launch {
            val ok = repo.removeGame(game.packageName)
            emit(
                if (ok) ctx.getString(R.string.fb_game_removed, game.displayName)
                else ctx.getString(R.string.fb_error_generic)
            )
        }
    }

    fun updateGameMode(game: GameUi, mode: Mode) {
        viewModelScope.launch {
            val updated = game.entry.copy(mgMode = mode.key, profile = mode.profileKey)
            val ok = repo.updateGame(updated)
            if (ok) emit(ctx.getString(R.string.fb_mode_applied, ctx.getString(mode.labelRes)))
            else emit(ctx.getString(R.string.fb_error_generic))
        }
    }

    fun updateGameQuality(game: GameUi, quality: Int) {
        viewModelScope.launch {
            val q = quality.coerceIn(40, 100)
            val updated = game.entry.copy(mgQuality = q)
            val ok = repo.updateGame(updated)
            if (ok) emit(ctx.getString(R.string.fb_quality_applied, game.displayName, q))
        }
    }

    fun toggleGameCleanMemory(game: GameUi, value: Boolean) {
        viewModelScope.launch {
            val updated = game.entry.copy(mgCleanMemory = value)
            repo.updateGame(updated)
            emit(if (value) ctx.getString(R.string.fb_clean_mem_on) else ctx.getString(R.string.fb_clean_mem_off))
        }
    }

    fun toggleGameNotif(game: GameUi, value: Boolean) {
        viewModelScope.launch {
            val updated = game.entry.copy(mgNotify = value)
            repo.updateGame(updated)
            emit(if (value) ctx.getString(R.string.fb_game_notif_on) else ctx.getString(R.string.fb_game_notif_off))
        }
    }

    fun launchGame(game: GameUi) {
        viewModelScope.launch {
            val mode = game.mode
            val label = ctx.getString(mode.labelRes)
            emit(ctx.getString(R.string.fb_game_launching, label, game.displayName))
            val res = repo.launchGame(game)
            if (!res.launched) {
                emit(ctx.getString(R.string.fb_launch_fail, game.displayName))
            } else if (res.memoryCleared) {
                val msg = ctx.getString(R.string.fb_memory_freed, label)
                emit(msg)
                if (_notifEnabled.value) Notif.postEvent(ctx, msg)
            }
        }
    }

    fun installedAppsFlow() = repo.let {
        kotlinx.coroutines.flow.flow { emit(it.installedLaunchableApps()) }
    }

    fun resetAll() {
        viewModelScope.launch {
            prefs.wipeAll()
            _userName.value = ""
            _notifEnabled.value = true
            _bootEnabled.value = true
            _globalMode.value = Mode.BALANCED
            emit(ctx.getString(R.string.fb_data_reset))
        }
    }

    fun refreshGames() {
        viewModelScope.launch { repo.refreshGames() }
    }

    private fun emit(msg: String) {
        feedback.trySend(msg)
    }
}
