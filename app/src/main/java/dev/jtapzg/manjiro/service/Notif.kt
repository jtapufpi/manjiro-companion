package dev.jtapzg.manjiro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat.PRIORITY_LOW
import dev.jtapzg.manjiro.MainActivity
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.FsmMode
import dev.jtapzg.manjiro.data.ManjiroState
import dev.jtapzg.manjiro.util.TimeFormat
import dev.jtapzg.manjiro.util.Translations

object Notif {

    const val CHANNEL_ESTADO = "estado"
    const val CHANNEL_CRONICA = "cronica"
    const val NOTIF_ID_ESTADO = 1001

    const val ACTION_TOGGLE = "dev.jtapzg.manjiro.ACTION_TOGGLE"
    const val ACTION_SAFE_CLEAR = "dev.jtapzg.manjiro.ACTION_SAFE_CLEAR"
    const val ACTION_OPEN = "dev.jtapzg.manjiro.ACTION_OPEN"

    fun ensureChannels(ctx: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createEstado(ctx, nm)
        createCronica(ctx, nm)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createEstado(ctx: Context, nm: NotificationManager) {
        val ch = NotificationChannel(
            CHANNEL_ESTADO,
            ctx.getString(R.string.channel_estado),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.channel_estado_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createCronica(ctx: Context, nm: NotificationManager) {
        val ch = NotificationChannel(
            CHANNEL_CRONICA,
            ctx.getString(R.string.channel_cronica),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.channel_cronica_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(ch)
    }

    /**
     * Constrói a notificação "Estado do Toman" — persistente, muda conforme FSM.
     *
     * Linha 1 (título): nome do estado (Modo Mikey / Toman em casa / etc).
     * Linha 2 (body):   jogo + tempo de sessão.
     * Linha 3 (body):   temps + bateria (📦 emoji-rich).
     * Expandida:        técnicas ativas + última ação + linha vermelha.
     */
    fun buildEstado(
        ctx: Context,
        state: ManjiroState,
        foregroundLabel: String?,
        nowMs: Long,
    ): android.app.Notification {
        val mode = FsmMode.from(state.mode)
        val title = Translations.mode(state.mode)
        val sessionStr = if (state.sessionStartMs > 0)
            TimeFormat.sessionShort(nowMs - state.sessionStartMs) else "—"
        val game = foregroundLabel ?: ctx.getString(R.string.hero_no_game)
        val tempLine = buildString {
            append("🐉 ${TimeFormat.tempC10(state.socTempC10)}°")
            append("   🔋 ${TimeFormat.tempC10(state.batteryTempC10)}°")
            if (state.batteryPct in 0..100) append("   ⚡ ${state.batteryPct}%")
        }

        val short = buildString {
            append(game)
            if (state.sessionStartMs > 0) append("  ·  $sessionStr")
            append('\n').append(tempLine)
        }

        val techniques = state.activeActuators.take(3)
            .joinToString(separator = " · ") { Translations.technique(it) }

        val big = buildString {
            append(game)
            if (state.sessionStartMs > 0) append("  ·  $sessionStr").append('\n')
            else append('\n')
            append(tempLine).append('\n')
            append("Disciplina: ${Translations.discipline(state.lyapunovV)}").append('\n')
            if (techniques.isNotBlank()) append("Técnicas: ").append(techniques)
            if (mode == FsmMode.SAFE && state.safeReason != null) {
                append('\n').append(ctx.getString(R.string.notif_safe_reason, Translations.safeReason(state.safeReason)))
            }
        }

        val color = when (mode) {
            FsmMode.ENGAGED -> 0xFFE63946.toInt()
            FsmMode.SAFE -> 0xFFEF5350.toInt()
            FsmMode.COOLDOWN -> 0xFF4FC3F7.toInt()
            FsmMode.ACTIVE -> 0xFF36C597.toInt()
            else -> 0xFF9099A2.toInt()
        }

        val openIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val toggleIntent = PendingIntent.getBroadcast(
            ctx, 1,
            Intent(ctx, NotifActionReceiver::class.java).apply {
                action = if (mode == FsmMode.SAFE) ACTION_SAFE_CLEAR else ACTION_TOGGLE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val toggleLabel = when (mode) {
            FsmMode.SAFE -> ctx.getString(R.string.notif_refugio)
            FsmMode.ENGAGED -> ctx.getString(R.string.notif_recuar)
            else -> ctx.getString(R.string.notif_roncar)
        }

        return NotificationCompat.Builder(ctx, CHANNEL_ESTADO)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(short.replace('\n', ' '))
            .setStyle(BigTextStyle().bigText(big).setBigContentTitle(title))
            .setOngoing(true)
            .setColor(color)
            .setColorized(mode == FsmMode.ENGAGED || mode == FsmMode.SAFE)
            .setPriority(PRIORITY_LOW)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .build()
    }

    /** Crônica de fim de sessão (silenciosa, ID por timestamp). */
    fun postCronica(
        ctx: Context,
        nm: NotificationManager,
        gameLabel: String,
        durationMs: Long,
        peakSocC10: Int,
        avgSocC10: Int,
        batDrainPct: Int,
        fps: Int?,
    ) {
        val title = "Crônica · $gameLabel"
        val body = buildString {
            append("Tempo ${TimeFormat.total(durationMs)}")
            append("   ·   pico ${TimeFormat.tempC10(peakSocC10)}°")
            append("   ·   média ${TimeFormat.tempC10(avgSocC10)}°")
            append('\n')
            append("Bateria −${batDrainPct}%")
            if (fps != null) append("   ·   FPS médio $fps")
        }
        val openIntent = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_CRONICA)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body.replace('\n', ' '))
            .setStyle(BigTextStyle().bigText(body))
            .setColor(0xFFF4C43B.toInt())
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(PRIORITY_LOW)
            .build()
        nm.notify((System.currentTimeMillis() / 1000).toInt(), n)
    }
}
