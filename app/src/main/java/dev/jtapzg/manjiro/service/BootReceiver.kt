package dev.jtapzg.manjiro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ManjiroService::class.java)
                )
            }
        }
    }
}
