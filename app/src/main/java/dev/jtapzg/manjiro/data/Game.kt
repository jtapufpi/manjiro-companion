package dev.jtapzg.manjiro.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Entrada de um jogo no `games.json`.
 *
 * Schema do daemon (chave = package_name):
 *   {
 *     "package.name": {
 *       "enabled": true,
 *       "class": "competitive",
 *       "weight": 1.0,
 *       "target": 60,
 *       "thermal_target_c10": 850,
 *       "profile": "mikey"
 *     }
 *   }
 */
@Serializable
data class GameEntry(
    val packageName: String,
    val enabled: Boolean = true,
    @SerialName("class") val gameClass: String = "casual",
    val weight: Float = 1.0f,
    val target: Int = 60,
    @SerialName("thermal_target_c10") val thermalTargetC10: Int = 850,
    val profile: String = "mikey",
    val hud: Boolean = true,
    val dnd: Boolean = false,
) {
    companion object {
        fun fromManifestObject(packageName: String, obj: JsonObject): GameEntry =
            GameEntry(
                packageName = packageName,
                enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true,
                gameClass = obj["class"]?.jsonPrimitive?.contentOrNull ?: "casual",
                weight = obj["weight"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 1f,
                target = obj["target"]?.jsonPrimitive?.intOrNull ?: 60,
                thermalTargetC10 = obj["thermal_target_c10"]?.jsonPrimitive?.intOrNull ?: 850,
                profile = obj["profile"]?.jsonPrimitive?.contentOrNull ?: "mikey",
                hud = obj["hud"]?.jsonPrimitive?.boolean ?: true,
                dnd = obj["dnd"]?.jsonPrimitive?.boolean ?: false,
            )
    }
}

/** Tudo que a UI sabe sobre um jogo recrutado: backing data + metadata do APK. */
data class GameUi(
    val entry: GameEntry,
    val displayName: String,
    val installed: Boolean,
    val totalPlayMs: Long = 0,
    val lastSessionMs: Long = 0,
    val lastTempC10: Int = 0,
    val launchIntent: android.content.Intent? = null,
) {
    val packageName: String get() = entry.packageName
}

/** Modo escolhido pelo usuário (mapeia pra profile no GameEntry). */
enum class CombatForm(val daemonProfile: String, val labelRes: Int, val descRes: Int) {
    MIKEY("mikey", dev.jtapzg.manjiro.R.string.form_mikey, dev.jtapzg.manjiro.R.string.form_mikey_desc),
    HINA("hina", dev.jtapzg.manjiro.R.string.form_hina, dev.jtapzg.manjiro.R.string.form_hina_desc),
    DRAKEN("draken", dev.jtapzg.manjiro.R.string.form_draken, dev.jtapzg.manjiro.R.string.form_draken_desc);

    companion object {
        fun fromKey(key: String?): CombatForm =
            entries.firstOrNull { it.daemonProfile == key?.lowercase() } ?: MIKEY
    }
}

object GamesIo {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun parseGames(raw: String): List<GameEntry> = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        root.entries.mapNotNull { (pkg, value) ->
            (value as? JsonObject)?.let { GameEntry.fromManifestObject(pkg, it) }
        }.sortedBy { it.packageName }
    }.getOrDefault(emptyList())

    fun serializeGames(games: List<GameEntry>): String {
        val pairs = games.associate { it.packageName to mapOf(
            "enabled" to it.enabled,
            "class" to it.gameClass,
            "weight" to it.weight,
            "target" to it.target,
            "thermal_target_c10" to it.thermalTargetC10,
            "profile" to it.profile,
            "hud" to it.hud,
            "dnd" to it.dnd,
        ) }
        val sb = StringBuilder().append("{\n")
        val keys = pairs.keys.toList()
        keys.forEachIndexed { i, pkg ->
            val obj = pairs.getValue(pkg)
            sb.append("  \"$pkg\": {\n")
            val entries = obj.entries.toList()
            entries.forEachIndexed { j, (k, v) ->
                sb.append("    \"$k\": ")
                when (v) {
                    is Boolean -> sb.append(v.toString())
                    is Int -> sb.append(v.toString())
                    is Float -> sb.append(v.toString())
                    else -> sb.append("\"${v}\"")
                }
                if (j < entries.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("  }")
            if (i < keys.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}\n")
        return sb.toString()
    }
}
