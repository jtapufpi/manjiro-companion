package com.jtapzg.manjirogaming.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.MainViewModel
import com.jtapzg.manjirogaming.ui.theme.MgColors
import com.jtapzg.manjirogaming.ui.theme.MonoNumberStyle
import com.jtapzg.manjirogaming.util.formatShortDuration

@Composable
fun StatsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val games by vm.games.collectAsState()
    val ctx = LocalContext.current

    var selected by remember { mutableStateOf<GameUi?>(null) }
    val current = selected ?: games.firstOrNull()
    val stats = current?.let { vm.prefs.gameStats(it.packageName) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_title),
            color = MgColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))

        if (games.isEmpty()) {
            Text(stringResource(R.string.stats_empty), color = MgColors.TextSecondary)
            return@Column
        }

        // Chips de jogos
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(games, key = { it.packageName }) { g ->
                FilterChip(
                    selected = current?.packageName == g.packageName,
                    onClick = { selected = g },
                    label = { Text(g.displayName, color = MgColors.TextPrimary) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MgColors.Red,
                        selectedLabelColor = Color.White,
                        containerColor = MgColors.CardHigh,
                        labelColor = MgColors.TextSecondary
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (stats == null) {
            Text(stringResource(R.string.stats_empty), color = MgColors.TextSecondary)
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MgColors.Card)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    StatBox(
                        title = stringResource(R.string.stats_total_time),
                        value = ctx.formatShortDuration(stats.totalMs),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatBox(
                        title = stringResource(R.string.stats_total_sessions),
                        value = stats.sessions.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    StatBox(
                        title = stringResource(R.string.stats_max_temp),
                        value = if (stats.peakTempC10 > 0) "%.1f°C".format(stats.peakTempC10 / 10.0) else "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Histórico das últimas 5
        if (stats.history.isNotEmpty()) {
            Text(stringResource(R.string.stats_last_5), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.history.take(5).forEach { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MgColors.Card)
                    ) {
                        Row(
                            Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Mode.fromKey(e.modeKey).labelRes),
                                    color = MgColors.Red,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = ctx.formatShortDuration(e.durationMs),
                                    color = MgColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = if (e.peakTempC10 > 0) "%.1f°C".format(e.peakTempC10 / 10.0) else "—",
                                color = MgColors.TextSecondary,
                                style = MonoNumberStyle
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Mini-gráfico de duração: barras simples
            Text(stringResource(R.string.stats_chart_title), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            DurationBarChart(values = stats.history.take(5).reversed().map { it.durationMs })
        }
    }
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(title, color = MgColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = MgColors.TextPrimary, style = MonoNumberStyle)
        }
    }
}

@Composable
private fun DurationBarChart(values: List<Long>) {
    val maxV = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val padding = 8.dp.toPx()
        val w = size.width - padding * 2
        val h = size.height - padding * 2
        val barW = if (values.isEmpty()) 0f else w / values.size * 0.7f
        val gap = if (values.isEmpty()) 0f else w / values.size * 0.3f
        values.forEachIndexed { i, v ->
            val x = padding + i * (barW + gap)
            val barH = (v.toFloat() / maxV) * h
            val y = padding + (h - barH)
            drawRoundRect(
                color = MgColors.Red,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}
