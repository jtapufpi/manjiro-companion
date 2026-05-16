package dev.jtapzg.manjiro.ui.toman

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.GameUi
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TextTertiary
import dev.jtapzg.manjiro.ui.theme.TomanRed
import dev.jtapzg.manjiro.ui.theme.TomanSurface
import dev.jtapzg.manjiro.ui.theme.TomanSurfaceHigh
import dev.jtapzg.manjiro.ui.theme.tempColor
import dev.jtapzg.manjiro.util.TimeFormat
import dev.jtapzg.manjiro.util.Translations

@Composable
fun GameGrid(
    games: List<GameUi>,
    onClick: (GameUi) -> Unit,
    onLongPress: (GameUi) -> Unit,
    onRecruit: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    modifier: Modifier = Modifier,
) {
    if (games.isEmpty()) {
        EmptyState(onRecruit, modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(items = games, key = { it.packageName }) { g ->
            GameCard(g, onClick = { onClick(g) }, onLongPress = { onLongPress(g) })
        }
    }
}

@Composable
private fun GameCard(
    game: GameUi,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val ctx = LocalContext.current
    var icon by remember { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(game.packageName) {
        icon = runCatching { ctx.packageManager.getApplicationIcon(game.packageName) }.getOrNull()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        color = TomanSurface,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TomanSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = icon?.toBitmap(108, 108)
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = game.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (!game.installed) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = game.displayName,
                        modifier = Modifier.fillMaxSize().alpha(0.4f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("•", color = TextTertiary, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = game.displayName,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Translations.gameClass(game.entry.gameClass),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.lastTempC10 > 0) {
                    TempBadge(game.lastTempC10)
                }
            }
        }
    }
}

@Composable
private fun TempBadge(c10: Int) {
    val color = tempColor(c10)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "${TimeFormat.tempC10(c10)}°",
            color = color,
            fontFamily = MonoFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyState(onRecruit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(TomanSurface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.mikey_empty),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.85f),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringForEmptyTitle(),
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringForEmptyBody(),
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRecruit,
        ) {
            Text(stringForRecruit())
        }
    }
}

@Composable
private fun stringForEmptyTitle(): String =
    androidx.compose.ui.res.stringResource(id = R.string.grid_empty_title)

@Composable
private fun stringForEmptyBody(): String =
    androidx.compose.ui.res.stringResource(id = R.string.grid_empty_body)

@Composable
private fun stringForRecruit(): String =
    androidx.compose.ui.res.stringResource(id = R.string.grid_recruit)

/** Converte Drawable → Bitmap (suporta AdaptiveIconDrawable). */
private fun Drawable.toBitmap(width: Int, height: Int): Bitmap? = runCatching {
    if (this is BitmapDrawable && bitmap != null) return@runCatching bitmap
    val bmp = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, width, height)
    draw(canvas)
    bmp
}.getOrNull()
