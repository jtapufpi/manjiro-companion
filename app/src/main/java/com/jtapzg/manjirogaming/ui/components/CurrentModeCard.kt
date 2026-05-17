package com.jtapzg.manjirogaming.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jtapzg.manjirogaming.R
import com.jtapzg.manjirogaming.mode.Mode
import com.jtapzg.manjirogaming.ui.theme.MgColors

@Composable
fun CurrentModeCard(
    mode: Mode,
    effective: Mode = mode,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = MgColors.RedGlow, spotColor = MgColors.RedGlow),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MgColors.Card),
        border = BorderStroke(1.dp, MgColors.Red.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeIcon(mode = mode, tint = MgColors.Red, sizeDp = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dash_current_mode),
                    color = MgColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = stringResource(mode.labelRes),
                    color = MgColors.TextPrimary,
                    style = MaterialTheme.typography.headlineMedium
                )
                if (mode == Mode.AUTO && effective != Mode.AUTO) {
                    Text(
                        text = "agora aplicando: " + stringResource(effective.labelRes),
                        color = MgColors.Red,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            FilledTonalButton(
                onClick = onChange,
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MgColors.RedGlow,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.dash_change_mode))
            }
        }
    }
}
