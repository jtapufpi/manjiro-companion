package dev.jtapzg.manjiro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.jtapzg.manjiro.data.ManjiroRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = ManjiroRepository.get(context)
        val scope = CoroutineScope(Dispatchers.IO)
        when (intent.action) {
            Notif.ACTION_TOGGLE -> scope.launch { repo.toggleEngaged() }
            Notif.ACTION_SAFE_CLEAR -> scope.launch {
                dev.jtapzg.manjiro.data.RootShell.safeClear()
            }
        }
    }
}
