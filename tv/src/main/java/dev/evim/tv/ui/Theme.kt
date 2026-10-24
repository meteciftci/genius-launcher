package dev.evim.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object EvimColors {
    val Background = Color(0xFF0B0B0F)
    val Chip = Color(0x14FFFFFF)
    val ChipBorder = Color(0x26FFFFFF)
    val ChipSolid = Color(0xB30B0B0F)
    val Text = Color.White
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x73FFFFFF)
    val Online = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFE5484D)
    /** Panellerde birincil düğme ve simgeler. */
    val Accent = Color(0xFF7AA2FF)
    // Marka ve cihaz türü renkleri: kartlarda ve satırlarda simge zemini
    val Tuya = Color(0xFFFF8A5B)
    val Dyson = Color(0xFF3CCFB0)
    val Feeder = Color(0xFFF5B83D)
    val Camera = Color(0xFFB794F6)

    /** Kartlarda ve renk menüsünde sunulan elle seçilebilir renkler. */
    val Picks = listOf(
        Color(0xFFD7141E), Color(0xFFE5484D), Color(0xFFF59E0B), Color(0xFF22A355),
        Color(0xFF1F9D8B), Color(0xFF2F6FDB), Color(0xFF6E56CF), Color(0xFFD6409F),
        Color(0xFF6B7280),
    )
}

fun Color.lighter(amount: Float = 0.25f) = lerp(this, Color.White, amount)

fun Color.darker(amount: Float = 0.35f) = lerp(this, Color.Black, amount)

@Composable
fun EvimTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
