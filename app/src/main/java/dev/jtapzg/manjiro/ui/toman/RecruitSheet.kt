package dev.jtapzg.manjiro.ui.toman

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanBg
import dev.jtapzg.manjiro.ui.theme.TomanSurface
import dev.jtapzg.manjiro.ui.theme.TomanSurfaceHigh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecruitSheet(
    candidates: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (pkg: String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, candidates) {
        if (query.isBlank()) candidates
        else candidates.filter {
            it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true)
        }
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
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.grid_recruit),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.grid_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            if (filtered.isEmpty()) {
                Text("—", color = TextSecondary)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TomanSurface),
                ) {
                    items(items = filtered, key = { it.first }) { (pkg, label) ->
                        AppRow(pkg = pkg, label = label, onPick = { onPick(pkg) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(pkg: String, label: String, onPick: () -> Unit) {
    val ctx = LocalContext.current
    var icon by remember { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(pkg) {
        icon = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TomanSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = icon?.toBitmap(72, 72)
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pkg, color = TextSecondary, fontSize = 10.sp, fontFamily = MonoFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Drawable.toBitmap(width: Int, height: Int): Bitmap? = runCatching {
    if (this is android.graphics.drawable.BitmapDrawable && bitmap != null) return@runCatching bitmap
    val bmp = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, width, height)
    draw(canvas)
    bmp
}.getOrNull()
