package com.jtapzg.manjirogaming

import android.app.Application
import com.jtapzg.manjirogaming.data.ManjiroRepository
import com.jtapzg.manjirogaming.service.Notif

class ManjiroGamingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notif.ensureChannels(this)
        // toca polling do daemon o quanto antes (singleton)
        ManjiroRepository.get(this).startPolling()
    }
}
