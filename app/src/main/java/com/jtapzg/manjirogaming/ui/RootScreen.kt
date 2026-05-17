package com.jtapzg.manjirogaming.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.ui.components.AmbientBackground
import com.jtapzg.manjirogaming.ui.screens.DashboardScreen
import com.jtapzg.manjirogaming.ui.screens.GameEditDialog
import com.jtapzg.manjirogaming.ui.screens.GamesScreen
import com.jtapzg.manjirogaming.ui.screens.ModesScreen
import com.jtapzg.manjirogaming.ui.screens.SettingsScreen
import com.jtapzg.manjirogaming.ui.screens.StatsScreen
import com.jtapzg.manjirogaming.ui.theme.MgColors
import kotlinx.coroutines.flow.collectLatest

enum class Tab(val labelRes: Int) {
    DASHBOARD(R.string.tab_dashboard),
    MODES(R.string.tab_modes),
    GAMES(R.string.tab_games),
    STATS(R.string.tab_stats),
    SETTINGS(R.string.tab_settings)
}

@Composable
fun RootScreen(vm: MainViewModel) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var editing by remember { mutableStateOf<GameUi?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val games by vm.games.collectAsState()

    // Mantém o jogo em edição sincronizado quando games muda
    LaunchedEffect(games) {
        val current = editing
        if (current != null) {
            editing = games.firstOrNull { it.packageName == current.packageName }
        }
    }

    LaunchedEffect(Unit) {
        vm.feedbackFlow.collectLatest { msg ->
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    AmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MgColors.Bg.copy(alpha = 0.96f)) {
                    Tab.entries.forEach { t ->
                        val icon = when (t) {
                            Tab.DASHBOARD -> Icons.Default.Home
                            Tab.MODES -> Icons.Default.FlashOn
                            Tab.GAMES -> Icons.Default.SportsEsports
                            Tab.STATS -> Icons.Default.BarChart
                            Tab.SETTINGS -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(t.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MgColors.Red,
                                selectedTextColor = MgColors.Red,
                                indicatorColor = MgColors.RedGlow.copy(alpha = 0.35f),
                                unselectedIconColor = MgColors.TextSecondary,
                                unselectedTextColor = MgColors.TextSecondary
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.DASHBOARD -> DashboardScreen(
                        vm = vm,
                        onChangeMode = { tab = Tab.MODES },
                        onOpenGame = { editing = it },
                        onLaunch = { vm.launchGame(it) },
                        onAddGame = { tab = Tab.GAMES }
                    )
                    Tab.MODES -> ModesScreen(vm)
                    Tab.GAMES -> GamesScreen(vm, onOpenGame = { editing = it })
                    Tab.STATS -> StatsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
                editing?.let { game ->
                    GameEditDialog(vm = vm, game = game, onDismiss = { editing = null })
                }
            }
        }
    }
}
