package dev.jtapzg.manjiro.ui.toman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.GameUi
import dev.jtapzg.manjiro.data.ManjiroState
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanBg
import dev.jtapzg.manjiro.ui.theme.TomanSurface
import dev.jtapzg.manjiro.ui.theme.tempColor
import dev.jtapzg.manjiro.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronicaSheet(
    state: ManjiroState,
    games: List<GameUi>,
    nowMs: Long,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TomanBg,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .padding(bottom = 22.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.cronica_title),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Sessão atual
            if (state.sessionStartMs > 0 && state.foregroundPackage != null) {
                CurrentSession(state, nowMs)
                Spacer(Modifier.height(14.dp))
            }

            // Bloco "Hoje" — total + nº de batalhas
            val today = games.filter {
                it.lastSessionMs > 0 && sameDay(it.lastSessionMs, nowMs)
            }
            if (today.isNotEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.cronica_today),
                    color = TextSecondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${today.size} ${if (today.size == 1) "batalha" else "batalhas"}",
                        color = TextPrimary, fontSize = 16.sp,
                    )
                    Text(
                        TimeFormat.total(today.sumOf { it.totalPlayMs }),
                        color = TextPrimary,
                        fontFamily = MonoFamily, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // Histórico (últimas batalhas) — usamos prefs locais
            Text(
                androidx.compose.ui.res.stringResource(R.string.cronica_history),
                color = TextSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))

            val recent = games.filter { it.lastSessionMs > 0 }
                .sortedByDescending { it.lastSessionMs }
                .take(30)

            if (recent.isEmpty()) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.cronica_empty),
                    color = TextSecondary, fontSize = 13.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TomanSurface),
                ) {
                    items(items = recent, key = { it.packageName }) { g ->
                        HistRow(g, nowMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentSession(state: ManjiroState, nowMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TomanSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.foregroundPackage ?: "—",
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                androidx.compose.ui.res.stringResource(R.string.cronica_session_now),
                color = TextSecondary, fontSize = 11.sp,
            )
        }
        Text(
            TimeFormat.session(nowMs - state.sessionStartMs),
            color = TextPrimary,
            fontFamily = MonoFamily, fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HistRow(g: GameUi, nowMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(g.displayName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(TimeFormat.dateTime(g.lastSessionMs, nowMs), color = TextSecondary, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                TimeFormat.total(g.totalPlayMs),
                color = TextPrimary,
                fontFamily = MonoFamily, fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (g.lastTempC10 > 0) {
                Text(
                    "${TimeFormat.tempC10(g.lastTempC10)}° pico",
                    color = tempColor(g.lastTempC10),
                    fontFamily = MonoFamily, fontSize = 10.sp,
                )
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}
