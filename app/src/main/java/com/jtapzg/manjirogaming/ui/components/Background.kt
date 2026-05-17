package com.jtapzg.manjirogaming.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.jtapzg.manjirogaming.ui.theme.MgColors

/** Fundo ambiente: gradiente radial sutil pulsando devagar, sem distração. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "bg")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bg-pulse"
    )

    Box(modifier = modifier.fillMaxSize().background(MgColors.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.2f, size.height * 0.0f)
            val brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1A0707).copy(alpha = 0.85f * pulse),
                    Color(0x00000000)
                ),
                center = center,
                radius = size.maxDimension * 0.7f
            )
            drawRect(brush = brush, size = Size(size.width, size.height))
        }
        content()
    }
}
