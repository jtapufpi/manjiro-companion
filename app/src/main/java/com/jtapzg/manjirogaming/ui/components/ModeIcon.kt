package com.jtapzg.manjirogaming.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.mode.Mode

@Composable
fun ModeIcon(
    mode: Mode,
    tint: Color,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 36.dp
) {
    val res = when (mode) {
        Mode.LITE -> R.drawable.ic_mode_lite
        Mode.BALANCED -> R.drawable.ic_mode_balanced
        Mode.PERFORMANCE -> R.drawable.ic_mode_performance
        Mode.AUTO -> R.drawable.ic_mode_auto
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(sizeDp)
    )
}
