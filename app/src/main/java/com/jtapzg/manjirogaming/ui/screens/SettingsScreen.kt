package com.jtapzg.manjirogaming.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.BuildConfig
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.ui.MainViewModel
import com.jtapzg.manjirogaming.ui.theme.MgColors

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val name by vm.userName.collectAsState()
    val notifEnabled by vm.notifEnabled.collectAsState()
    val bootEnabled by vm.bootEnabled.collectAsState()
    var draft by remember(name) { mutableStateOf(name) }
    var showReset by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            color = MgColors.TextPrimary,
            style = MaterialTheme.typography.headlineMedium
        )

        SettingsSection(title = stringResource(R.string.settings_section_account)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_name_label)) },
                placeholder = { Text(stringResource(R.string.settings_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MgColors.TextPrimary,
                    unfocusedTextColor = MgColors.TextPrimary,
                    focusedBorderColor = MgColors.Red,
                    unfocusedBorderColor = MgColors.Border,
                    focusedLabelColor = MgColors.Red,
                    unfocusedLabelColor = MgColors.TextSecondary,
                    cursorColor = MgColors.Red
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        vm.setName(draft)
                    }
                )
            )
            LaunchedEffect(draft) {
                // commit automaticamente quando para de digitar (debounce simples a cargo do user clicar fora)
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_app)) {
            ToggleRow(
                title = stringResource(R.string.settings_notif_master),
                subtitle = stringResource(R.string.settings_notif_master_sub),
                checked = notifEnabled,
                onCheckedChange = vm::setNotifEnabled
            )
            ToggleRow(
                title = stringResource(R.string.settings_start_on_boot),
                subtitle = stringResource(R.string.settings_start_on_boot_sub),
                checked = bootEnabled,
                onCheckedChange = vm::setBootEnabled
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_advanced)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Tenta abrir o WebUI via "WebUI X" (Magisk) — fallback: link manual ao módulo
                        val opened = openWebUi(ctx)
                        if (!opened) {
                            android.widget.Toast.makeText(ctx, R.string.fb_module_open_fail, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_open_webui), color = MgColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_open_webui_sub), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Default.OpenInNew, null, tint = MgColors.Red)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showReset = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_reset), color = MgColors.Hot, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_reset_sub), color = MgColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                color = MgColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.settings_dev_by),
                color = MgColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.settings_dev_link),
                color = MgColors.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    runCatching {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jtapzg"))
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        ctx.startActivity(i)
                    }
                }
            )
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            containerColor = MgColors.Card,
            title = { Text(stringResource(R.string.settings_reset_confirm_title), color = MgColors.TextPrimary) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body), color = MgColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetAll()
                    showReset = false
                }) { Text(stringResource(R.string.settings_reset_confirm_yes), color = MgColors.Hot) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
                    Text(stringResource(R.string.settings_reset_confirm_no), color = MgColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, color = MgColors.Red, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MgColors.Card)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MgColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = MgColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
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

private fun openWebUi(ctx: android.content.Context): Boolean {
    // Tenta WebUI X / Magisk WebUI; se falhar, mostra link.
    val intents = listOf(
        Intent().apply {
            setClassName("com.dergoogler.mmrl.wx", "com.dergoogler.mmrl.wx.ui.activity.webui.WebUIActivity")
            putExtra("MOD_ID", "manjiro_dinamic")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        },
        Intent(Intent.ACTION_VIEW, Uri.parse("intent://manjiro_dinamic#Intent;scheme=webui;end")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    )
    for (i in intents) {
        try { ctx.startActivity(i); return true } catch (_: Throwable) {}
    }
    return false
}
