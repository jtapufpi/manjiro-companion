package dev.jtapzg.manjiro

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import dev.jtapzg.manjiro.service.Notif

class ManjiroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Notif.ensureChannels(this, nm)
    }
}
