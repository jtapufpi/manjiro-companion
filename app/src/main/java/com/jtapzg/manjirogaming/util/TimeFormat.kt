package com.jtapzg.manjirogaming.util

import android.content.Context
import com.jtapzg.manjirogaming.R

/** Formata uma duração em ms como "12s", "3min", "1h 24m". */
fun Context.formatShortDuration(ms: Long): String {
    if (ms < 1_000) return getString(R.string.time_seconds_short, 0)
    val totalSec = ms / 1_000
    if (totalSec < 60) return getString(R.string.time_seconds_short, totalSec.toInt())
    val totalMin = totalSec / 60
    if (totalMin < 60) return getString(R.string.time_minutes_short, totalMin.toInt())
    val h = totalMin / 60
    val m = totalMin % 60
    return getString(R.string.time_hours_short, h.toInt(), m.toInt())
}

/** Formata "ago" simples: agora, X min, Xh. */
fun Context.formatRelativeAgo(timestampMs: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timestampMs).coerceAtLeast(0)
    return formatShortDuration(diff)
}

/** Formata segundos como "M:SS" pra cronômetro de sessão. */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val ss = s % 60
    return "%d:%02d".format(m, ss)
}
