package com.jtapzg.manjirogaming.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.MainViewModel
import com.jtapzg.manjirogaming.ui.components.CurrentModeCard
import com.jtapzg.manjirogaming.ui.components.GameCard
import com.jtapzg.manjirogaming.ui.components.MetricBar
import com.jtapzg.manjirogaming.ui.components.WarningBanner
import com.jtapzg.manjirogaming.ui.theme.MgColors

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onChangeMode: () -> Unit,
    onOpenGame: (GameUi) -> Unit,
    onLaunch: (GameUi) -> Unit,
    onAddGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by vm.state.collectAsState()
    val games by vm.games.collectAsState()
    val diag by vm.diag.collectAsState()
    val name by vm.userName.collectAsState()
    val mode by vm.globalMode.collectAsState()
    val effective by vm.effectiveMode.collectAsState()

    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Saudação
        Text(
            text = if (name.isBlank())
                stringResource(R.string.greet_default)
            else stringResource(R.string.greet_named, name),
            color = MgColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium
        )

        // Diagnósticos
        if (!diag.hasRoot) {
            WarningBanner(
                title = stringResource(R.string.banner_no_root_title),
                body = stringResource(R.string.banner_no_root_body)
            )
        } else if (!diag.moduleInstalled) {
            WarningBanner(
                title = stringResource(R.string.banner_no_module_title),
                body = stringResource(R.string.banner_no_module_body)
            )
        }

        // Modo atual
        CurrentModeCard(mode = mode, effective = effective, onChange = onChangeMode)

        // Métricas
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MgColors.Card)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.dash_metrics_title),
                    color = MgColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(12.dp))
                MetricBar(
                    label = stringResource(R.string.dash_metric_cpu),
                    percent = state?.cpuPercent,
                    invertHealth = true
                )
                Spacer(Modifier.height(10.dp))
                MetricBar(
                    label = stringResource(R.string.dash_metric_ram),
                    percent = state?.memFreePercent
                )
                Spacer(Modifier.height(10.dp))
                MetricBar(
                    label = stringResource(R.string.dash_metric_gpu),
                    percent = state?.gpuPercent,
                    invertHealth = true
                )
            }
        }

        // Lista de jogos (horizontal)
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dash_my_games),
                    color = MgColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(Modifier.height(8.dp))
            if (games.isEmpty()) {
                Text(
                    text = stringResource(R.string.dash_no_games),
                    color = MgColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(games, key = { it.packageName }) { g ->
                        GameCard(
                            game = g,
                            onLaunch = { onLaunch(g) },
                            onEdit = { onOpenGame(g) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
