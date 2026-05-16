package dev.jtapzg.manjiro.ui.toman

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.FsmMode
import dev.jtapzg.manjiro.ui.theme.MikeyGold
import dev.jtapzg.manjiro.ui.theme.StateHot
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanRed
import dev.jtapzg.manjiro.ui.theme.TomanSurface
import kotlinx.coroutines.delay

@Composable
fun TomanScreen(vm: TomanViewModel) {
    val state by vm.state.collectAsState()
    val games by vm.games.collectAsState()
    val installedGames by vm.installedGames.collectAsState()
    val daemonStatus by vm.daemonStatus.collectAsState()
    val selected by vm.selectedGame.collectAsState()
    val showAjustes by vm.showAjustes.collectAsState()
    val showRecruit by vm.showRecruit.collectAsState()
    val showCronica by vm.showCronica.collectAsState()
    val candidates by vm.recruitCandidates.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val mode = FsmMode.from(state.mode)
    val engaged = mode == FsmMode.ENGAGED
    val safe = mode == FsmMode.SAFE

    val fgLabel = remember(state.foregroundPackage, games) {
        val pkg = state.foregroundPackage
        if (pkg.isNullOrBlank()) null
        else games.firstOrNull { it.packageName == pkg }?.displayName ?: pkg
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { vm.showCronica(true) }) {
                        Icon(Icons.Default.History, contentDescription = "Crônica")
                    }
                    IconButton(onClick = { vm.showSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.toggleEngaged() },
                icon = {
                    Icon(
                        when {
                            safe -> Icons.Default.Shield
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        when {
                            safe -> androidx.compose.ui.res.stringResource(R.string.fab_refugio)
                            engaged -> androidx.compose.ui.res.stringResource(R.string.fab_recuar)
                            else -> androidx.compose.ui.res.stringResource(R.string.fab_roncar)
                        }
                    )
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {
            Hero(
                state = state,
                nowMs = nowMs,
                foregroundLabel = fgLabel,
            )
            DaemonBanner(
                status = daemonStatus,
                onOpenAjustes = { vm.showSettings(true) },
            )
            Spacer(Modifier.height(8.dp))
            // Header da grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.grid_title),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                // Mostra instalados / total no banco do módulo
                val totalDb = games.size
                Text(
                    text = if (totalDb > installedGames.size)
                        "${installedGames.size} · ${totalDb} no banco"
                    else "${installedGames.size}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            // Grid (só instalados)
            Box(modifier = Modifier.fillMaxSize()) {
                GameGrid(
                    games = installedGames,
                    onClick = { vm.launchGame(it.packageName) },
                    onLongPress = { vm.selectGame(it) },
                    onRecruit = { vm.showRecruit(true) },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                )
            }
        }
    }

    // Bottom sheets
    selected?.let { game ->
        FichaMembroSheet(
            game = game,
            onDismiss = { vm.selectGame(null) },
            onLaunch = { vm.selectGame(null); vm.launchGame(it.packageName) },
            onSave = { entry -> vm.saveGame(entry) { vm.selectGame(null) } },
            onRemove = { vm.removeGame(it.packageName) { vm.selectGame(null) } },
        )
    }

    if (showAjustes) {
        AjustesSheet(
            onDismiss = { vm.showSettings(false) },
            vm = vm,
            inSafeMode = safe,
        )
    }

    if (showRecruit) {
        RecruitSheet(
            candidates = candidates,
            onDismiss = { vm.showRecruit(false) },
            onPick = { pkg -> vm.addGame(pkg); vm.showRecruit(false) },
        )
    }

    if (showCronica) {
        CronicaSheet(
            state = state,
            games = installedGames,
            nowMs = nowMs,
            onDismiss = { vm.showCronica(false) },
        )
    }
}

/**
 * Banner curtinho acima do grid quando algo está fora do ar.
 * Toque abre os Ajustes pro usuário ver o diagnóstico completo.
 */
@Composable
private fun DaemonBanner(status: DaemonStatus, onOpenAjustes: () -> Unit) {
    if (status == DaemonStatus.ALIVE || status == DaemonStatus.UNKNOWN) return
    val (tint, title, body) = when (status) {
        DaemonStatus.NO_ROOT -> Triple(
            TomanRed,
            "Sem acesso de líder",
            "Abra o Magisk/KernelSU e libere o root pro Manjiro",
        )
        DaemonStatus.NO_MODULE -> Triple(
            StateHot,
            "Módulo não detectado",
            "Instale o Manjiro Dinamic v1.7.1 e reinicie",
        )
        DaemonStatus.DORMANT -> Triple(
            MikeyGold,
            "Manjiro Sano dormindo",
            "Módulo presente, mas o daemon ainda não publicou estado (aguarde ~30s após boot)",
        )
        else -> Triple(TextPrimary, "", "")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TomanSurface)
            .clickable { onOpenAjustes() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tint),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
