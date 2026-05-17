package com.jtapzg.manjirogaming.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jtapzg.manjirogaming.mode.Mode

/**
 * Configurações pessoais do app (nome, modo global, toggles, etc.) + estatísticas locais.
 * Tudo persiste em SharedPreferences; nada de sensível.
 */
class ManjiroPrefs(context: Context) {

    private val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences("manjiro_gaming_prefs", Context.MODE_PRIVATE)

    // ── Conta do usuário ────────────────────────────────────────────────
    var userName: String
        get() = sp.getString(KEY_NAME, "")!!
        set(value) = sp.edit { putString(KEY_NAME, value.trim()) }

    // ── Modo global ─────────────────────────────────────────────────────
    var globalMode: Mode
        get() = Mode.fromKey(sp.getString(KEY_MODE, Mode.BALANCED.key))
        set(value) = sp.edit { putString(KEY_MODE, value.key) }

    // ── Toggles globais ─────────────────────────────────────────────────
    var notificationsEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIF_ON, true)
        set(value) = sp.edit { putBoolean(KEY_NOTIF_ON, value) }

    var startOnBoot: Boolean
        get() = sp.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = sp.edit { putBoolean(KEY_START_ON_BOOT, value) }

    // ── Estatísticas locais por pacote ──────────────────────────────────
    fun gameStats(pkg: String): GameStats {
        return GameStats(
            totalMs = sp.getLong(keyTotal(pkg), 0L),
            sessions = sp.getInt(keyCount(pkg), 0),
            lastPlayedMs = sp.getLong(keyLast(pkg), 0L),
            peakTempC10 = sp.getInt(keyPeak(pkg), 0),
            history = readHistory(pkg)
        )
    }

    fun recordSession(pkg: String, durationMs: Long, mode: Mode, peakTempC10: Int) {
        val now = System.currentTimeMillis()
        sp.edit {
            putLong(keyTotal(pkg), sp.getLong(keyTotal(pkg), 0L) + durationMs)
            putInt(keyCount(pkg), sp.getInt(keyCount(pkg), 0) + 1)
            putLong(keyLast(pkg), now)
            val prevPeak = sp.getInt(keyPeak(pkg), 0)
            if (peakTempC10 > prevPeak) putInt(keyPeak(pkg), peakTempC10)
        }
        pushHistory(pkg, SessionEntry(now, durationMs, mode.key, peakTempC10))
    }

    fun readHistory(pkg: String): List<SessionEntry> {
        val raw = sp.getString(keyHistory(pkg), null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { SessionEntry.fromCsv(it) }.toList()
    }

    private fun pushHistory(pkg: String, entry: SessionEntry) {
        val current = readHistory(pkg).toMutableList()
        current.add(0, entry)
        while (current.size > HISTORY_LIMIT) current.removeAt(current.size - 1)
        sp.edit { putString(keyHistory(pkg), current.joinToString("\n") { it.toCsv() }) }
    }

    fun wipeAll() {
        sp.edit { clear() }
    }

    data class SessionEntry(
        val endedAtMs: Long,
        val durationMs: Long,
        val modeKey: String,
        val peakTempC10: Int
    ) {
        fun toCsv() = "$endedAtMs,$durationMs,$modeKey,$peakTempC10"

        companion object {
            fun fromCsv(line: String): SessionEntry? {
                val parts = line.split(",")
                if (parts.size < 4) return null
                return runCatching {
                    SessionEntry(
                        endedAtMs = parts[0].toLong(),
                        durationMs = parts[1].toLong(),
                        modeKey = parts[2],
                        peakTempC10 = parts[3].toInt()
                    )
                }.getOrNull()
            }
        }
    }

    data class GameStats(
        val totalMs: Long,
        val sessions: Int,
        val lastPlayedMs: Long,
        val peakTempC10: Int,
        val history: List<SessionEntry>
    )

    companion object {
        private const val HISTORY_LIMIT = 20
        private const val KEY_NAME = "user_name"
        private const val KEY_MODE = "global_mode"
        private const val KEY_NOTIF_ON = "notif_on"
        private const val KEY_START_ON_BOOT = "boot_on"

        private fun keyTotal(pkg: String) = "stat_${pkg}_total"
        private fun keyCount(pkg: String) = "stat_${pkg}_count"
        private fun keyLast(pkg: String) = "stat_${pkg}_last"
        private fun keyPeak(pkg: String) = "stat_${pkg}_peak"
        private fun keyHistory(pkg: String) = "stat_${pkg}_hist"

        @Volatile private var INSTANCE: ManjiroPrefs? = null
        fun get(context: Context): ManjiroPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ManjiroPrefs(context).also { INSTANCE = it }
            }
    }
}
