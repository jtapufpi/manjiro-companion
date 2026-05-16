package dev.jtapzg.manjiro.ui.toman

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.BuildConfig
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.CombatForm
import dev.jtapzg.manjiro.data.RootShell
import dev.jtapzg.manjiro.ui.theme.MikeyGold
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanBg
import dev.jtapzg.manjiro.ui.theme.TomanRed
import dev.jtapzg.manjiro.ui.theme.TomanSurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesSheet(
    onDismiss: () -> Unit,
    vm: TomanViewModel,
    inSafeMode: Boolean,
) {
    val ctx = LocalContext.current

    var hud by remember { mutableStateOf(vm.hudGlobal()) }
    var form by remember { mutableStateOf(vm.defaultForm()) }
    var devMode by remember { mutableStateOf(vm.devMode()) }
    var cronica by remember { mutableStateOf(vm.cronicaEnabled()) }

    // 7 toques no header pra habilitar dev mode
    var tapsForDev by remember { mutableIntStateOf(0) }

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
                text = androidx.compose.ui.res.stringResource(R.string.ajustes_title),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clickable {
                    tapsForDev += 1
                    if (tapsForDev >= 7 && !devMode) {
                        devMode = true
                        vm.setDevMode(true)
                    }
                },
            )
            Spacer(Modifier.height(18.dp))

            // HUD global
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ajustes_hud_global),
                    color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hud,
                    onCheckedChange = { hud = it; vm.setHudGlobal(it) },
                )
            }
            Spacer(Modifier.height(14.dp))

            // Crônica
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Crônica de fim de sessão",
                    color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = cronica,
                    onCheckedChange = { cronica = it; vm.setCronicaEnabled(it) },
                )
            }
            Spacer(Modifier.height(14.dp))

            // Forma padrão picker
            Text(
                androidx.compose.ui.res.stringResource(R.string.ajustes_default_form),
                color = TextSecondary, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CombatForm.entries.forEach { f ->
                    val sel = f == form
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) TomanRed else TomanSurfaceHigh)
                            .clickable { form = f; vm.setDefaultForm(f) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(f.labelRes),
                            color = TextPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Painel avançado (WebUI)
            TextButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:8080"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            ) {
                Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.ajustes_open_webui))
            }

            if (inSafeMode) {
                TextButton(
                    onClick = { vm.toggleEngaged() },
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.ajustes_exit_safe),
                        color = TomanRed,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Versão
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ajustes_version),
                    color = TextSecondary, fontSize = 12.sp,
                )
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    color = TextPrimary,
                    fontFamily = MonoFamily, fontSize = 12.sp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ajustes_module_version),
                    color = TextSecondary, fontSize = 12.sp,
                )
                Text(
                    "/data/adb/manjiro_dinamic",
                    color = TextPrimary,
                    fontFamily = MonoFamily, fontSize = 11.sp,
                )
            }

            if (devMode) {
                Spacer(Modifier.height(14.dp))
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ajustes_devmode_enabled),
                    color = MikeyGold, fontSize = 11.sp,
                    fontFamily = MonoFamily,
                )
                Text("status: ${RootShell.statusPath}", color = TextSecondary, fontSize = 10.sp, fontFamily = MonoFamily)
                Text("games:  ${RootShell.gamesPath}", color = TextSecondary, fontSize = 10.sp, fontFamily = MonoFamily)
                Text("ctrl:   ${RootShell.controlPath}", color = TextSecondary, fontSize = 10.sp, fontFamily = MonoFamily)
            }
        }
    }
}
