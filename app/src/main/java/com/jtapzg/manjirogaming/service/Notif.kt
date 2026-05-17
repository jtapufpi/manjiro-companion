package com.jtapzg.manjirogaming.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.jtapzg.manjirogaming.MainActivity
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.ManjiroState
import com.jtapzg.manjirogaming.data.friendlyAppName
import com.jtapzg.manjirogaming.mode.Mode

/**
 * Central de notificações. Dois canais:
 *  - CHANNEL_GAME (alto/persistente): sessão de jogo / estado do motor
 *  - CHANNEL_EVENT (low importance): avisos pontuais (modo aplicado, memória etc.)
 *
 * Toda função `build*` retorna um Notification pronto pra ser entregue
 * via NotificationManager OU usado como notificação do foreground service.
 */
object Notif {

    private const val CHANNEL_GAME = "manjiro_gaming_session"
    private const val CHANNEL_EVENT = "manjiro_gaming_event"

    const val NOTIF_ID_SESSION = 1001
    const val NOTIF_ID_EVENT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService<NotificationManager>() ?: return

        val gameChannel = NotificationChannel(
            CHANNEL_GAME,
            context.getString(R.string.channel_game),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_game_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(gameChannel)

        val eventChannel = NotificationChannel(
            CHANNEL_EVENT,
            context.getString(R.string.channel_event),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_event_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(eventChannel)
    }

    /** Notificação persistente da sessão. Sempre retorna algo, mesmo sem state. */
    fun buildSession(
        context: Context,
        state: ManjiroState?,
        globalMode: Mode
    ): android.app.Notification {
        ensureChannels(context)

        val isGame = state?.isGameActive == true && !state.foregroundPackage.isNullOrBlank()
        val (title, text) = when {
            isGame -> {
                val pkg = state!!.foregroundPackage!!
                val label = state.foregroundLabel ?: context.friendlyAppName(pkg)
                val modeKey = state.activeMode ?: state.requestedMode ?: globalMode.key
                val activeMode = Mode.fromKey(modeKey)
                val tempStr = state.socTempCelsius?.let { "%.1f°C".format(it) } ?: "—"
                context.getString(R.string.notif_game_title, label) to
                    context.getString(
                        R.string.notif_game_text,
                        context.getString(activeMode.labelRes),
                        tempStr
                    )
            }
            state?.fsmMode == ManjiroState.FsmMode.SAFE ->
                context.getString(R.string.notif_safe_title) to
                    context.getString(R.string.notif_safe_text)
            else -> {
                val effective = globalMode
                context.getString(R.string.notif_idle_title) to
                    context.getString(R.string.notif_idle_text, context.getString(effective.labelRes))
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recoilIntent = Intent(context, NotifActionReceiver::class.java).apply {
            action = NotifActionReceiver.ACTION_RECOIL
            putExtra(NotifActionReceiver.EXTRA_PACKAGE, state?.foregroundPackage)
        }
        val recoilPi = PendingIntent.getBroadcast(
            context, 1, recoilIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_GAME)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (isGame) {
            builder.addAction(
                NotificationCompat.Action.Builder(0, context.getString(R.string.notif_action_recoil), recoilPi).build()
            )
        }

        return builder.build()
    }

    /** Toast persistente nas notificações: ex. "Modo Lite ativado". */
    fun postEvent(context: Context, title: String, body: String? = null) {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_EVENT)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .apply { if (body != null) setContentText(body); setStyle(NotificationCompat.BigTextStyle().bigText(body ?: title)) }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(8_000)
            .setContentIntent(pi)
            .build()
        context.getSystemService<NotificationManager>()?.notify(NOTIF_ID_EVENT, notif)
    }
}
