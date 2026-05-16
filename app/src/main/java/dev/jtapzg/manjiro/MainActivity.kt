package dev.jtapzg.manjiro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.jtapzg.manjiro.service.ManjiroService
import dev.jtapzg.manjiro.ui.theme.ManjiroTheme
import dev.jtapzg.manjiro.ui.toman.TomanScreen
import dev.jtapzg.manjiro.ui.toman.TomanViewModel

class MainActivity : ComponentActivity() {

    private val vm: TomanViewModel by viewModels()

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — usuário decide */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start companion foreground service
        ContextCompat.startForegroundService(this, Intent(this, ManjiroService::class.java))

        setContent {
            ManjiroTheme {
                TomanScreen(vm)
            }
        }
    }
}
