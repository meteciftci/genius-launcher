package dev.evim.tv.ui

import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import dev.evim.tv.R
import androidx.compose.ui.res.stringResource
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.tv.material3.Border
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
import androidx.compose.material.icons.rounded.Tune
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
import dev.evim.tv.data.weatherDescription
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TopBar(vm: LauncherViewModel, onWeatherClick: () -> Unit, onSmartHome: () -> Unit, onGeniusSettings: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
            vm.refreshRam()
        }
    }

    // Saat biçimi ve tarih sistemin dil ve 12/24 saat ayarına uyar
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) {
        SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale)
    }
    val dateFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Chip(endPadding = 4.dp) {
            Text(timeFormat.format(Date(now)), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(dateFormat.format(Date(now)), color = EvimColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            // Basınca saatlik ve 7 günlük hava durumu paneli açılır
            val weather = vm.weather
            val description = weather?.let { w -> weatherDescription(w.code)?.let { stringResource(it) } }
            ChipButton(
                text = if (weather == null) vm.city.name else listOfNotNull("${weather.symbol}  ${weather.temperature}°", description, vm.city.name).joinToString("  ·  "),
                onClick = onWeatherClick,
            )
        }
        Spacer(Modifier.weight(1f))
        HomeChip(vm = vm, onClick = onSmartHome)
        Spacer(Modifier.width(8.dp))
        ShortcutsChip(vm = vm, onGeniusSettings = onGeniusSettings)
        Spacer(Modifier.width(8.dp))
        RamChip(vm = vm)
    }
}

/** Evim kutusu: akıllı ev panelini açar; cihaz yokken eklemeye çağırır. */
@Composable
private fun HomeChip(vm: LauncherViewModel, onClick: () -> Unit) {
    val devices = vm.homeDevices
    Surface(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, EvimColors.ChipBorder))),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EvimColors.ChipSolid,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⌂", fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.home_title), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val status = if (devices.isEmpty()) stringResource(R.string.home_add_device) else stringResource(R.string.home_device_count, devices.size)
            Text(status, fontSize = 11.sp, modifier = Modifier.alpha(0.7f))
            if (devices.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                // Yeşil: tüm cihazlar cevap veriyor; sarı: biri ulaşılamıyor
                val healthy = devices.all { it.reachable }
                Box(Modifier.size(5.dp).background(if (healthy) EvimColors.Online else EvimColors.Warning, CircleShape))
            }
        }
    }
}

/** Google TV'deki gibi hızlı ayar kısayolları; Wi-Fi ve Bluetooth simgeleri durumu da gösterir. */
@Composable
private fun ShortcutsChip(vm: LauncherViewModel, onGeniusSettings: () -> Unit) {
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
            IconButton(wifiIcon, stringResource(R.string.shortcut_wifi)) { vm.openSettings(Settings.ACTION_WIFI_SETTINGS) }
            IconButton(
                if (status.bluetoothOn) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                stringResource(R.string.shortcut_bluetooth),
            ) { vm.openAccessorySettings() }
            IconButton(Icons.Rounded.Tv, stringResource(R.string.shortcut_display_sound)) { vm.openSettings(Settings.ACTION_SOUND_SETTINGS) }
            IconButton(Icons.Rounded.Settings, stringResource(R.string.shortcut_settings)) { vm.openSettings(Settings.ACTION_SETTINGS) }
            IconButton(Icons.Rounded.Tune, stringResource(R.string.settings_title), onGeniusSettings)
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


@Composable
private fun RamChip(vm: LauncherViewModel) {
    val used = vm.ram.usedFraction
    Chip(endPadding = 4.dp) {
        Text(stringResource(R.string.ram_label), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("  " + stringResource(R.string.percent, (used * 100).toInt()), color = EvimColors.TextDim, fontSize = 11.sp)
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
                vm.ramMessage?.let { stringResource(it.text, *it.args) } ?: stringResource(R.string.ram_free),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
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
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
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
