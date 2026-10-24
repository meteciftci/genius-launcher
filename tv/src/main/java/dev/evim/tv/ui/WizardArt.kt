package dev.evim.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.East
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.evim.tv.R

/*
 * Kurulum sihirbazının açıklayıcı görselleri. Resim dosyası yerine Compose ile çizilir: dil değişince
 * içindeki yazılar da değişir ve APK büyümez. Her biri yaklaşık 360×300 dp'lik bir alana sığar.
 */

/** ADB: Ayarlar'da izlenecek yol; derleme numarasına 7 kez basılır, sonra USB hata ayıklama açılır. */
@Composable
internal fun AdbArt() {
    Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ArtRow(Icons.Rounded.Settings, stringResource(R.string.art_settings))
        ArtArrow()
        ArtRow(Icons.Rounded.Info, stringResource(R.string.art_about))
        ArtArrow()
        ArtRow(Icons.Rounded.Build, stringResource(R.string.art_build), highlight = true) {
            Text("× 7", color = EvimColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        ArtArrow()
        ArtRow(Icons.Rounded.Code, stringResource(R.string.art_developer))
        ArtArrow()
        ArtRow(Icons.Rounded.Usb, stringResource(R.string.art_usb_debugging), highlight = true) { ArtSwitch(on = true) }
    }
}

/** Home: kumandadaki Home tuşu Genius Launcher'ı açar. */
@Composable
internal fun HomeArt() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Kumanda
        Column(
            Modifier
                .width(78.dp)
                .height(250.dp)
                .background(Color(0xFF26262E), RoundedCornerShape(40.dp))
                .border(1.dp, EvimColors.ChipBorder, RoundedCornerShape(40.dp))
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(46.dp).border(2.dp, Color(0x33FFFFFF), CircleShape))
            Box(Modifier.size(34.dp).background(EvimColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Home, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            repeat(3) { Box(Modifier.size(width = 22.dp, height = 8.dp).background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))) }
        }
        Icon(Icons.Rounded.East, null, tint = EvimColors.Accent, modifier = Modifier.padding(horizontal = 18.dp).size(30.dp))
        // TV'de Genius Launcher
        Column(
            Modifier
                .width(220.dp)
                .height(136.dp)
                .background(Color(0xFF15151B), RoundedCornerShape(12.dp))
                .border(3.dp, Color(0xFF3A3A44), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Box(Modifier.size(width = 70.dp, height = 7.dp).background(Color(0x55FFFFFF), RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(width = 120.dp, height = 14.dp).background(Color.White, RoundedCornerShape(4.dp)))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EvimColors.Picks.take(5).forEach { Box(Modifier.size(width = 30.dp, height = 22.dp).background(it, RoundedCornerShape(5.dp))) }
            }
        }
    }
}

/** Temizlik: bazı uygulamalar listeden çıkar; işletim sistemi yerinde kalır. */
@Composable
internal fun CleanupArt() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf(false, true, false), listOf(true, false, true)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { i, removed ->
                    Box {
                        Box(
                            Modifier
                                .size(width = 96.dp, height = 70.dp)
                                .alpha(if (removed) 0.3f else 1f)
                                .background(EvimColors.Picks[(i * 3 + if (removed) 1 else 4) % EvimColors.Picks.size], RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Apps, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                        }
                        if (removed) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(26.dp).background(EvimColors.Danger, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Remove, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Uyanık tut: TV kapalı, kutu ev ağında açık kalır. */
@Composable
internal fun AwakeArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width = 260.dp, height = 150.dp)
                .background(Color(0xFF0A0A0D), RoundedCornerShape(12.dp))
                .border(3.dp, Color(0xFF3A3A44), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Bedtime, null, tint = Color(0x55FFFFFF), modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 96.dp, height = 40.dp).background(Color(0xFF26262E), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.padding(end = 12.dp).size(9.dp).background(EvimColors.Online, CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Rounded.Wifi, null, tint = EvimColors.Online, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
internal fun DoneArt() {
    Box(Modifier.size(170.dp).background(EvimColors.Online.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.CheckCircle, null, tint = EvimColors.Online, modifier = Modifier.size(96.dp))
    }
}

@Composable
private fun ArtRow(icon: ImageVector, text: String, highlight: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) EvimColors.Accent.copy(alpha = 0.16f) else Color(0x12FFFFFF), RoundedCornerShape(12.dp))
            .border(1.dp, if (highlight) EvimColors.Accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlight) EvimColors.Accent else EvimColors.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun ArtArrow() {
    Icon(Icons.Rounded.ArrowDownward, null, tint = Color(0x44FFFFFF), modifier = Modifier.fillMaxWidth().height(12.dp))
}

@Composable
private fun ArtSwitch(on: Boolean, width: Dp = 38.dp) {
    Box(
        Modifier.size(width = width, height = 22.dp).background(if (on) EvimColors.Accent else Color(0x33FFFFFF), RoundedCornerShape(50)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).size(16.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Rounded.Check, null, tint = EvimColors.Accent, modifier = Modifier.size(12.dp))
        }
    }
}
