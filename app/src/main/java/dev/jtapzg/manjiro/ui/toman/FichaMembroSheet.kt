package dev.jtapzg.manjiro.ui.toman

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.CombatForm
import dev.jtapzg.manjiro.data.GameEntry
import dev.jtapzg.manjiro.data.GameUi
import dev.jtapzg.manjiro.ui.theme.MikeyGold
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanBg
import dev.jtapzg.manjiro.ui.theme.TomanRed
import dev.jtapzg.manjiro.ui.theme.TomanSurfaceHigh
import dev.jtapzg.manjiro.util.TimeFormat
import dev.jtapzg.manjiro.util.Translations
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FichaMembroSheet(
    game: GameUi,
    onDismiss: () -> Unit,
    onLaunch: (GameUi) -> Unit,
    onSave: (GameEntry) -> Unit,
    onRemove: (GameUi) -> Unit,
) {
    var enabled by remember { mutableStateOf(game.entry.enabled) }
    var form by remember { mutableStateOf(CombatForm.fromKey(game.entry.profile)) }
    var redLine by remember { mutableStateOf(game.entry.thermalTargetC10.toFloat()) }
    var hud by remember { mutableStateOf(game.entry.hud) }
    var dnd by remember { mutableStateOf(game.entry.dnd) }

    LaunchedEffect(game.packageName) {
        enabled = game.entry.enabled
        form = CombatForm.fromKey(game.entry.profile)
        redLine = game.entry.thermalTargetC10.toFloat()
        hud = game.entry.hud
        dnd = game.entry.dnd
    }

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
            // Header
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ficha_title),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = game.displayName,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${Translations.gameClass(game.entry.gameClass)} · " + game.packageName,
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))

            // Stats row (total + last)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MiniStat(
                    label = "Tempo total",
                    value = TimeFormat.total(game.totalPlayMs),
                )
                MiniStat(
                    label = "Última batalha",
                    value = TimeFormat.ago(game.lastSessionMs),
                )
            }
            Spacer(Modifier.height(18.dp))

            // Toggle ativo
            SettingRow(
                title = androidx.compose.ui.res.stringResource(R.string.ficha_enabled),
            ) {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            // Form picker
            Spacer(Modifier.height(8.dp))
            Text(
                androidx.compose.ui.res.stringResource(R.string.ficha_combat),
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CombatForm.entries.forEach { f ->
                    FormChip(
                        form = f,
                        selected = f == form,
                        onClick = { form = f },
                        modifier = Modifier.fillMaxWidth().padding(0.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // Red line slider (thermal target)
            SettingRow(
                title = androidx.compose.ui.res.stringResource(R.string.ficha_red_line),
                trailing = {
                    Text(
                        "${(redLine / 10f).roundToInt()}°",
                        color = TomanRed,
                        fontFamily = MonoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
            Slider(
                value = redLine,
                onValueChange = { redLine = it },
                valueRange = 700f..950f, // 70°C .. 95°C
                steps = 24,
            )

            // HUD + DND
            SettingRow(
                title = androidx.compose.ui.res.stringResource(R.string.ficha_hud),
            ) { Switch(checked = hud, onCheckedChange = { hud = it }) }
            SettingRow(
                title = androidx.compose.ui.res.stringResource(R.string.ficha_dnd),
            ) { Switch(checked = dnd, onCheckedChange = { dnd = it }) }

            Spacer(Modifier.height(20.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onRemove(game) },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.ficha_remove))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onSave(
                            game.entry.copy(
                                enabled = enabled,
                                profile = form.daemonProfile,
                                thermalTargetC10 = redLine.roundToInt(),
                                hud = hud,
                                dnd = dnd,
                            )
                        )
                    },
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ficha_save))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(
                            game.entry.copy(
                                enabled = enabled,
                                profile = form.daemonProfile,
                                thermalTargetC10 = redLine.roundToInt(),
                                hud = hud,
                                dnd = dnd,
                            )
                        )
                        onLaunch(game)
                    },
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.ficha_launch))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontFamily = MonoFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingRow(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        when {
            trailing != null -> trailing()
            content != null -> content()
        }
    }
}

@Composable
private fun FormChip(
    form: CombatForm,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) TomanRed else TomanSurfaceHigh
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            androidx.compose.ui.res.stringResource(form.labelRes),
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            androidx.compose.ui.res.stringResource(form.descRes),
            color = if (selected) MikeyGold else TextSecondary,
            fontSize = 10.sp,
            maxLines = 2,
        )
    }
}
