package com.jtapzg.manjirogaming.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.jtapzg.manjirogaming.mode.Mode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Linha do games.json (formato 1.7.3 do módulo Manjiro Dinamic + extensões do APK).
 */
@Serializable
data class GameEntry(
    @SerialName("package") val packageName: String,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("game_class") val gameClass: String? = null,
    @SerialName("weight") val weight: Int = 5,
    @SerialName("target") val target: String = "balanced",
    @SerialName("thermal_target_c10") val thermalTargetC10: Int = 430,
    @SerialName("profile") val profile: String? = null,
    @SerialName("hud") val hud: Boolean = false,
    @SerialName("dnd") val dnd: Boolean = true,
    /** Extensão do APK — modo escolhido para esse jogo. */
    @SerialName("mg_mode") val mgMode: String = Mode.BALANCED.key,
    /** Extensão do APK — nitidez de tela em % (40..100). */
    @SerialName("mg_quality") val mgQuality: Int = 100,
    /** Extensão do APK — limpar memória antes de abrir. */
    @SerialName("mg_clean_memory") val mgCleanMemory: Boolean = true,
    /** Extensão do APK — mostrar notificação dessa sessão. */
    @SerialName("mg_notify") val mgNotify: Boolean = true
) {
    fun mode(): Mode = Mode.fromKey(mgMode)
}

@Serializable
data class GamesFile(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("games") val games: List<GameEntry> = emptyList()
)

/** Representação UI de um jogo (combina metadados do PackageManager + entry do JSON). */
data class GameUi(
    val packageName: String,
    val displayName: String,
    val installed: Boolean,
    val icon: Drawable?,
    val entry: GameEntry,
    val launchIntent: Intent? = null,
    val lastPlayedMs: Long = 0,
    val totalPlayedMs: Long = 0
) {
    val mode: Mode get() = entry.mode()
}

object GamesIo {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun parse(raw: String?): GamesFile {
        if (raw.isNullOrBlank()) return GamesFile()
        return runCatching { json.decodeFromString<GamesFile>(raw) }.getOrElse {
            // Tolera arquivos antigos só com lista.
            runCatching {
                val list = json.decodeFromString<List<GameEntry>>(raw)
                GamesFile(games = list)
            }.getOrDefault(GamesFile())
        }
    }

    fun encode(file: GamesFile): String = json.encodeToString(GamesFile.serializer(), file)
}

/** Resolve um nome amigável a partir do PackageManager, com fallback no package. */
fun Context.friendlyAppName(packageName: String): String {
    return runCatching {
        val pm = packageManager
        val ai = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(ai).toString()
    }.getOrDefault(packageName)
}
