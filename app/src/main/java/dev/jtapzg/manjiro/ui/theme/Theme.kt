package dev.jtapzg.manjiro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema fixo dark (toman vibe). Mesmo se o sistema estiver em modo light,
 * o app mostra a paleta preto-vermelho-dourado por design.
 */
private val ManjiroColors = darkColorScheme(
    primary = TomanRed,
    onPrimary = TextPrimary,
    primaryContainer = TomanRedDark,
    onPrimaryContainer = TextPrimary,
    secondary = MikeyGold,
    onSecondary = TomanBg,
    secondaryContainer = MikeyGoldDark,
    onSecondaryContainer = TextPrimary,
    tertiary = StateGood,
    onTertiary = TomanBg,
    background = TomanBg,
    onBackground = TextPrimary,
    surface = TomanSurface,
    onSurface = TextPrimary,
    surfaceVariant = TomanSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    error = StateHot,
    onError = TextPrimary,
    outline = TextTertiary,
    outlineVariant = TextTertiary,
)

@Composable
fun ManjiroTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ManjiroColors,
        typography = ManjiroTypography,
        content = content,
    )
}
