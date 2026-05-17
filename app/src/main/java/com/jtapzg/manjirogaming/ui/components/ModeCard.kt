package com.jtapzg.manjirogaming.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.theme.MgColors

/**
 * Cartão grande de modo. Quando selecionado, ganha borda vermelha + glow animado.
 */
@Composable
fun ModeCard(
    mode: Mode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = if (selected) {
        val transition = rememberInfiniteTransition(label = "glow-${mode.key}")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
            label = "alpha"
        ).value
    } else 0f

    val borderColor = if (selected) MgColors.Red else MgColors.Border
    val borderWidth = if (selected) 1.5.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (selected) 12.dp else 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = MgColors.RedGlow.copy(alpha = pulse),
                spotColor = MgColors.RedGlow.copy(alpha = pulse)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = MgColors.Red),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MgColors.Card),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            ModeIcon(
                mode = mode,
                tint = if (selected) MgColors.Red else MgColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(mode.labelRes),
                    color = MgColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.mode_active_badge),
                        color = MgColors.Red,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(
                                MgColors.RedGlow.copy(alpha = 0.45f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(mode.descRes),
                color = MgColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
