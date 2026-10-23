package dev.evim.tv.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.NetworkWifi1Bar
import androidx.compose.material.icons.rounded.NetworkWifi2Bar
import androidx.compose.material.icons.rounded.NetworkWifi3Bar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale("tr", "TR")

@Composable
fun TopBar(vm: LauncherViewModel, onWeatherClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
            vm.refreshRam()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Chip(endPadding = 4.dp) {
            Text(SimpleDateFormat("HH:mm", TR).format(Date(now)), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("EEE d MMM", TR).format(Date(now)), color = EvimColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            val weather = vm.weather
            ChipButton(
                text = if (weather != null) "${weather.symbol}  ${weather.temperature}°  ·  ${vm.city.name}" else vm.city.name,
                onClick = onWeatherClick,
            )
        }
        Spacer(Modifier.weight(1f))
        Chip {
            Text("⌂", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("Evim", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  kurulum bekliyor", color = EvimColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(5.dp).background(EvimColors.Warning, CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        ShortcutsChip(vm = vm)
        Spacer(Modifier.width(8.dp))
        RamChip(vm = vm)
    }
}

/** Google TV'deki gibi hızlı ayar kısayolları; Wi-Fi ve Bluetooth simgeleri durumu da gösterir. */
@Composable
private fun ShortcutsChip(vm: LauncherViewModel) {
    val status = vm.status
    val wifiIcon = when (status.wifiLevel) {
        null -> Icons.Rounded.WifiOff
        0, 1 -> Icons.Rounded.NetworkWifi1Bar
        2 -> Icons.Rounded.NetworkWifi2Bar
        3 -> Icons.Rounded.NetworkWifi3Bar
        else -> Icons.Rounded.Wifi
    }
    Chip(startPadding = 4.dp, endPadding = 4.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(wifiIcon, "Wi-Fi") { vm.openSettings(Settings.ACTION_WIFI_SETTINGS) }
            IconButton(
                if (status.bluetoothOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                "Bluetooth aksesuarları",
            ) { vm.openSettings(ACTION_CONNECT_INPUT) }
            IconButton(Icons.Rounded.Tv, "Görüntü ve ses") { vm.openSettings(Settings.ACTION_SOUND_SETTINGS) }
            IconButton(Icons.Rounded.Settings, "Ayarlar") { vm.openSettings(Settings.ACTION_SETTINGS) }
        }
    }
}

@Composable
private fun IconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(26.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = EvimColors.TextDim,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(15.dp))
        }
    }
}

// Google TV ayarlarında aksesuar (Bluetooth kumanda, kulaklık…) eşleme ekranı
private const val ACTION_CONNECT_INPUT = "com.google.android.intent.action.CONNECT_INPUT"

@Composable
private fun RamChip(vm: LauncherViewModel) {
    val used = vm.ram.usedFraction
    Chip(endPadding = 4.dp) {
        Text("RAM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("  %${(used * 100).toInt()}", color = EvimColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(54.dp)
                .height(3.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(used.coerceIn(0f, 1f))
                    .background(EvimColors.Warning, RoundedCornerShape(50)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = vm::freeRam,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = EvimColors.Warning.copy(alpha = 0.18f),
                contentColor = EvimColors.Warning,
                focusedContainerColor = EvimColors.Warning,
                focusedContentColor = Color(0xFF111111),
            ),
        ) {
            Text(
                "⚡ Boşalt",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Chip(
    startPadding: androidx.compose.ui.unit.Dp = 14.dp,
    endPadding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            // Arkadaki renk sürekli değiştiği için koyu, sade bir zemin: yazı her renkte okunur
            .background(EvimColors.ChipSolid, RoundedCornerShape(50))
            .border(1.dp, EvimColors.ChipBorder, RoundedCornerShape(50))
            .padding(start = startPadding, end = endPadding),
        content = content,
    )
}

/** Kutucuğun içinde kumandayla seçilebilen küçük düğme; odakta beyazlaşır. */
@Composable
private fun ChipButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            contentColor = EvimColors.TextDim,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
