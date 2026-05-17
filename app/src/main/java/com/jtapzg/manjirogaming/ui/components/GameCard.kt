package com.jtapzg.manjirogaming.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.remember
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.data.GameUi
import com.jtapzg.manjirogaming.ui.theme.MgColors
import com.jtapzg.manjirogaming.util.formatRelativeAgo
import androidx.compose.ui.platform.LocalContext

/** Cartão pequeno de jogo: ícone + nome + última sessão + botão "jogar". */
@Composable
fun GameCard(
    game: GameUi,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val agoText = if (game.lastPlayedMs <= 0) {
        stringResource(R.string.dash_session_never)
    } else {
        stringResource(R.string.dash_session_short, context.formatRelativeAgo(game.lastPlayedMs))
    }

    Card(
        modifier = modifier
            .width(168.dp)
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MgColors.Card),
        border = BorderStroke(1.dp, MgColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MgColors.CardHigh),
                    contentAlignment = Alignment.Center
                ) {
                    val drawable = game.icon
                    if (drawable != null) {
                        val bitmap = remember(drawable) { drawableToBitmap(drawable) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_controller),
                                contentDescription = null,
                                tint = MgColors.TextSecondary
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_controller),
                            contentDescription = null,
                            tint = MgColors.TextSecondary
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onLaunch,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MgColors.Red,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.games_launch))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = game.displayName,
                color = MgColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                text = stringResource(game.mode.labelRes),
                color = MgColors.Red,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = agoText,
                color = MgColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return runCatching {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return@runCatching drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp
    }.getOrNull()
}
