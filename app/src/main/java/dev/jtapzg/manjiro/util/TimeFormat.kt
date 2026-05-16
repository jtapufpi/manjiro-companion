package dev.jtapzg.manjiro.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object TimeFormat {

    /** ms → "12:34" ou "1:23:45". Tempo de sessão / cronômetro. */
    fun session(elapsedMs: Long): String {
        val total = abs(elapsedMs) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    /** ms → "47:22" (sempre minutos:segundos, sem horas). Notificação. */
    fun sessionShort(elapsedMs: Long): String {
        val total = abs(elapsedMs) / 1000
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    /** ms → "1h 24m" ou "24m" ou "—". Total acumulado. */
    fun total(totalMs: Long): String {
        if (totalMs <= 0) return "—"
        val total = totalMs / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        return when {
            h >= 1 -> "%dh %dm".format(h, m)
            m >= 1 -> "%dm".format(m)
            else -> "%ds".format(total)
        }
    }

    /** epoch ms → "há 5 min", "há 2 h", "ontem", "12 mai". */
    fun ago(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0) return "—"
        val delta = (nowMs - epochMs) / 1000
        return when {
            delta < 30 -> "agora"
            delta < 60 -> "há ${delta}s"
            delta < 3600 -> "há ${delta / 60} min"
            delta < 86400 -> "há ${delta / 3600} h"
            delta < 172800 -> "ontem"
            delta < 604800 -> "há ${delta / 86400} dias"
            else -> SimpleDateFormat("d MMM", Locale("pt", "BR")).format(Date(epochMs))
        }
    }

    /** epoch ms → "08:30" hoje, "ontem 22:15", "12/05 14:00". */
    fun dateTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0) return "—"
        val time = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(epochMs))
        val sameDay = SimpleDateFormat("yyyyMMdd", Locale("pt", "BR")).run {
            format(Date(epochMs)) == format(Date(nowMs))
        }
        return if (sameDay) time
        else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(epochMs))
    }

    /** centidegrees → "84.7" (uma casa decimal). */
    fun tempC10(c10: Int): String {
        if (c10 <= 0 || c10 > 1500) return "—"
        return "%.1f".format(c10 / 10f)
    }
}
