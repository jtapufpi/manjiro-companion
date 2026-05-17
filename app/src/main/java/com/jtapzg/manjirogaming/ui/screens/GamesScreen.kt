package com.jtapzg.manjirogaming.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.data.ManjiroRepository
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.MainViewModel
import com.jtapzg.manjirogaming.ui.components.ModeIcon
import com.jtapzg.manjirogaming.ui.theme.MgColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GamesScreen(
    vm: MainViewModel,
    onOpenGame: (GameUi) -> Unit,
    modifier: Modifier = Modifier
) {
    val games by vm.games.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.games_title),
                color = MgColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.games_add))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (games.isEmpty()) {
            Text(
                text = stringResource(R.string.games_empty),
                color = MgColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(games, key = { it.packageName }) { g ->
                    GameRow(
                        game = g,
                        onClick = { onOpenGame(g) },
                        onLaunch = { vm.launchGame(g) },
                        onRemove = { vm.removeGame(g) }
                    )
                }
            }
        }
    }

    if (showPicker) {
        AppPickerDialog(
            vm = vm,
            onDismiss = { showPicker = false },
            onPick = { pkg ->
                vm.addGame(pkg)
                showPicker = false
            }
        )
    }
}

@Composable
private fun GameRow(
    game: GameUi,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MgColors.Card)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MgColors.CardHigh),
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(game.icon) {
                    game.icon?.let { d ->
                        runCatching {
                            if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) d.bitmap
                            else {
                                val b = android.graphics.Bitmap.createBitmap(
                                    d.intrinsicWidth.coerceAtLeast(1),
                                    d.intrinsicHeight.coerceAtLeast(1),
                                    android.graphics.Bitmap.Config.ARGB_8888
                                )
                                val c = android.graphics.Canvas(b)
                                d.setBounds(0, 0, c.width, c.height); d.draw(c); b
                            }
                        }.getOrNull()
                    }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_controller), null, tint = MgColors.TextSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(game.displayName, color = MgColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeIcon(mode = game.mode, tint = MgColors.Red, sizeDp = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(game.mode.labelRes),
                        color = MgColors.Red,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            IconButton(onClick = onLaunch) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.games_launch), tint = MgColors.Red)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.games_remove), tint = MgColors.TextSecondary)
            }
        }
    }
}

@Composable
fun GameEditDialog(
    vm: MainViewModel,
    game: GameUi,
    onDismiss: () -> Unit
) {
    var quality by remember(game) { mutableStateOf(game.entry.mgQuality.coerceIn(40, 100)) }
    val config: Configuration = LocalConfiguration.current
    val pxW = (config.screenWidthDp * config.densityDpi / 160f).toInt()
    val pxH = (config.screenHeightDp * config.densityDpi / 160f).toInt()
    val widthAt = remember(quality) { (pxW * quality / 100) }
    val heightAt = remember(quality) { (pxH * quality / 100) }
    val qualityLabel = when {
        quality == 100 -> stringResource(R.string.games_quality_native)
        quality >= 80 -> stringResource(R.string.games_quality_high)
        quality >= 60 -> stringResource(R.string.games_quality_good)
        else -> stringResource(R.string.games_quality_low)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MgColors.Card,
        titleContentColor = MgColors.TextPrimary,
        textContentColor = MgColors.TextSecondary,
        title = { Text(game.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Modo do jogo
                Text(stringResource(R.string.games_mode_label), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Mode.LITE, Mode.BALANCED, Mode.PERFORMANCE).forEach { m ->
                        val isSel = game.mode == m
                        FilledTonalButton(
                            onClick = { vm.updateGameMode(game, m) },
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isSel) MgColors.Red else MgColors.CardHigh,
                                contentColor = if (isSel) androidx.compose.ui.graphics.Color.White else MgColors.TextPrimary
                            )
                        ) {
                            Text(stringResource(m.labelRes))
                        }
                    }
                }

                // Nitidez
                Text(stringResource(R.string.games_quality_label), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.games_quality_value_pct, quality, widthAt, heightAt),
                    color = MgColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(qualityLabel, color = MgColors.Red, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { quality = it.toInt() },
                    onValueChangeFinished = { vm.updateGameQuality(game, quality) },
                    valueRange = 40f..100f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MgColors.Red,
                        activeTrackColor = MgColors.Red,
                        inactiveTrackColor = MgColors.CardHigh
                    )
                )

                // Limpar memória
                ToggleRow(
                    title = stringResource(R.string.games_clean_memory_label),
                    subtitle = stringResource(R.string.games_clean_memory_sub),
                    checked = game.entry.mgCleanMemory,
                    onCheckedChange = { vm.toggleGameCleanMemory(game, it) }
                )

                // Notificação
                ToggleRow(
                    title = stringResource(R.string.games_notif_label),
                    subtitle = stringResource(R.string.games_notif_sub),
                    checked = game.entry.mgNotify,
                    onCheckedChange = { vm.toggleGameNotif(game, it) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.launchGame(game)
                    onDismiss()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MgColors.Red)
            ) { Text(stringResource(R.string.games_launch)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MgColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MgColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, color = MgColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = MgColors.Red,
                uncheckedThumbColor = MgColors.TextSecondary,
                uncheckedTrackColor = MgColors.CardHigh
            )
        )
    }
}

@Composable
private fun AppPickerDialog(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<ManjiroRepository.InstalledApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val all = withContext(Dispatchers.IO) {
                ManjiroRepository.get(ctx).installedLaunchableApps()
            }
            apps = all
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MgColors.Card,
        title = { Text(stringResource(R.string.games_picker_title), color = MgColors.TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.games_picker_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(stringResource(R.string.games_picker_empty), color = MgColors.TextSecondary)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bmp = remember(app.icon) {
                                    app.icon?.let { d ->
                                        runCatching {
                                            if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) d.bitmap
                                            else {
                                                val b = android.graphics.Bitmap.createBitmap(
                                                    d.intrinsicWidth.coerceAtLeast(1),
                                                    d.intrinsicHeight.coerceAtLeast(1),
                                                    android.graphics.Bitmap.Config.ARGB_8888
                                                )
                                                val c = android.graphics.Canvas(b)
                                                d.setBounds(0, 0, c.width, c.height); d.draw(c); b
                                            }
                                        }.getOrNull()
                                    }
                                }
                                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(painterResource(R.drawable.ic_chip_app), null, tint = MgColors.TextSecondary)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(app.displayName, color = MgColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                                    Text(app.packageName, color = MgColors.TextTertiary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.games_picker_cancel), color = MgColors.TextSecondary)
            }
        }
    )
}
