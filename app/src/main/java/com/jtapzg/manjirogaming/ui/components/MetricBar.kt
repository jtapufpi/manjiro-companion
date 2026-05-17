package com.jtapzg.manjirogaming.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.ui.theme.MgColors
import com.jtapzg.manjirogaming.ui.theme.MonoNumberStyle
import com.jtapzg.manjirogaming.ui.theme.healthColor

/** Barra horizontal de saúde (0..100). Mostra label + número monoespaçado. */
@Composable
fun MetricBar(
    label: String,
    percent: Int?,
    modifier: Modifier = Modifier,
    invertHealth: Boolean = false
) {
    val displayed = (percent ?: 0).coerceIn(0, 100)
    val healthBasis = if (invertHealth) 100 - displayed else displayed
    val color = healthColor(healthBasis)
    val targetFraction = displayed / 100f
    val animated by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 600),
        label = "metric"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Métrica $label, ${percent ?: 0} por cento"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = MgColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (percent == null) "—" else "%d%%".format(displayed),
                color = color,
                style = MonoNumberStyle.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(MgColors.CardHigh)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
