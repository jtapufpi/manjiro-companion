package dev.jtapzg.manjiro.ui.toman

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jtapzg.manjiro.R
import dev.jtapzg.manjiro.data.FsmMode
import dev.jtapzg.manjiro.data.ManjiroState
import dev.jtapzg.manjiro.ui.theme.MikeyGold
import dev.jtapzg.manjiro.ui.theme.MonoFamily
import dev.jtapzg.manjiro.ui.theme.StateGood
import dev.jtapzg.manjiro.ui.theme.StateHot
import dev.jtapzg.manjiro.ui.theme.StateCold
import dev.jtapzg.manjiro.ui.theme.TextPrimary
import dev.jtapzg.manjiro.ui.theme.TextSecondary
import dev.jtapzg.manjiro.ui.theme.TomanBg
import dev.jtapzg.manjiro.ui.theme.TomanRed
import dev.jtapzg.manjiro.ui.theme.disciplineColor
import dev.jtapzg.manjiro.ui.theme.tempColor
import dev.jtapzg.manjiro.util.TimeFormat
import dev.jtapzg.manjiro.util.Translations

@Composable
fun Hero(
    state: ManjiroState,
    nowMs: Long,
    foregroundLabel: String?,
    modifier: Modifier = Modifier,
) {
    val mode = FsmMode.from(state.mode)
    val modeColor = when (mode) {
        FsmMode.ENGAGED -> TomanRed
        FsmMode.SAFE -> StateHot
        FsmMode.COOLDOWN -> StateCold
        FsmMode.ACTIVE -> StateGood
        else -> TextSecondary
    }
    val animatedAccent by animateColorAsState(modeColor, tween(400), label = "modeColor")

    // Glow pulsante quando ENGAGED ou SAFE.
    val transition = rememberInfiniteTransition(label = "hero-pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (mode == FsmMode.SAFE) 700 else 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(TomanBg),
    ) {
        // BG image: Mikey vermelho quando engaged, sepia/dark caso contrário
        if (mode == FsmMode.ENGAGED) {
            Image(
                painter = painterResource(id = R.drawable.mikey_hero),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.45f),
                contentScale = ContentScale.Crop,
            )
        }
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to TomanBg.copy(alpha = 0.85f),
                        0.6f to TomanBg.copy(alpha = 0.4f),
                        1f to TomanBg,
                    )
                )
        )

        // Pulsing border in ENGAGED/SAFE
        if (mode == FsmMode.ENGAGED || mode == FsmMode.SAFE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(animatedAccent.copy(alpha = pulseAlpha * 0.25f), Color.Transparent),
                            radius = 600f
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Linha 1: estado + jogo + tempo
            Column {
                Text(
                    text = Translations.mode(state.mode).uppercase(),
                    color = animatedAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = Translations.modeBlurb(state.mode),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = foregroundLabel ?: stringForNoGame(),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    )
                    if (state.sessionStartMs > 0) {
                        Text(
                            text = "  ·  ${TimeFormat.session(nowMs - state.sessionStartMs)}",
                            color = TextSecondary,
                            fontFamily = MonoFamily,
                            fontSize = 17.sp,
                        )
                    }
                }
                if (mode == FsmMode.SAFE && state.safeReason != null) {
                    Text(
                        text = Translations.safeReason(state.safeReason),
                        color = StateHot,
                        fontSize = 13.sp,
                    )
                }
            }

            // Linha 2: stats (temps + bateria + disciplina) — escondido quando UNKNOWN
            val isUnknown = mode == FsmMode.UNKNOWN
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Stat(
                        label = "Toman",
                        value = if (isUnknown || state.socTempC10 <= 0) "—"
                            else TimeFormat.tempC10(state.socTempC10) + "°",
                        valueColor = if (isUnknown) TextSecondary else tempColor(state.socTempC10),
                    )
                    Stat(
                        label = "Hinata",
                        value = if (isUnknown || state.batteryTempC10 <= 0) "—"
                            else TimeFormat.tempC10(state.batteryTempC10) + "°",
                        valueColor = if (isUnknown) TextSecondary else tempColor(state.batteryTempC10),
                    )
                    Stat(
                        label = "Bateria",
                        value = if (state.batteryPct in 0..100) "${state.batteryPct}%" else "—",
                        valueColor = if (state.batteryPct in 0..15) StateHot
                            else if (state.batteryPct in 16..30) MikeyGold
                            else if (isUnknown) TextSecondary
                            else TextPrimary,
                    )
                }
                if (!isUnknown) {
                    Spacer(Modifier.height(14.dp))
                    DisciplineBar(state.lyapunovV)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun DisciplineBar(lyapunovV: Int) {
    val color = disciplineColor(lyapunovV)
    val label = Translations.discipline(lyapunovV)
    // Inverte: menor lyapunov = barra mais cheia (mais disciplinado)
    val fill = (1f - (lyapunovV.coerceIn(0, 10_000) / 10_000f)).coerceIn(0.05f, 1f)

    Column {
        Text(
            "Disciplina · $label",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(TomanRed.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun stringForNoGame(): String =
    androidx.compose.ui.res.stringResource(id = R.string.hero_no_game)
