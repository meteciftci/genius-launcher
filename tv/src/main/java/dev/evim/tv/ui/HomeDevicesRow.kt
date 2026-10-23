package dev.evim.tv.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.AirQuality
import dev.evim.tv.data.home.HomeDevice
import dev.evim.tv.data.home.celsiusToDyson
import dev.evim.tv.data.home.Meal

/** Henüz bağlanmamış cihazlar; anahtarları alınınca gerçek cihaz olarak satıra katılırlar. */
private val Pending = listOf("◉" to R.string.device_camera, "▣" to R.string.device_litter_box)

// Ayar dosyası gönderilmeden önce satırın boş görünmemesi için
private val Placeholders = listOf(
    "❋" to R.string.device_dyson,
    "◍" to R.string.device_feeder_1,
    "◍" to R.string.device_feeder_2,
    "◐" to R.string.device_relay,
)

@Composable
fun HomeDevicesRow(vm: LauncherViewModel) {
    var feeder by remember { mutableStateOf<HomeDevice.Feeder?>(null) }
    var purifierKey by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.home_title), color = EvimColors.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        // Cihazlar ekrana sığmayınca satır yana kayar; odak ilerledikçe kendiliğinden kaydırılır.
        // Kaydırma alanı kenarları kırptığı için büyüyen odak çerçevesine iç boşluk bırakılır.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.offset(x = (-8).dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (!vm.homeConfigured) {
                Placeholders.forEach { (symbol, name) -> DeviceChip(symbol, stringResource(name), null, active = false) {} }
            }
            vm.homeDevices.forEach { device ->
                when (device) {
                    is HomeDevice.Purifier -> DeviceChip("❋", device.name, device.detail(), active = device.state?.power == true) {
                        purifierKey = device.key
                    }
                    is HomeDevice.Feeder -> DeviceChip("◍", device.name, device.detail(), active = device.reachable) {
                        feeder = device
                    }
                    is HomeDevice.Relay -> DeviceChip("◐", device.name, device.detail(), active = device.on) {
                        if (device.reachable) vm.toggleRelay(device)
                    }
                }
            }
            Pending.forEach { (symbol, name) -> DeviceChip(symbol, stringResource(name), null, active = false) {} }
        }
    }

    feeder?.let { device ->
        FeederDialog(device, onFeed = { vm.feed(device, it); feeder = null }, onDismiss = { feeder = null })
    }
    // Komut sonrası güncel durumu göstermek için cihaz listeden her seferinde yeniden alınır
    (vm.homeDevices.firstOrNull { it.key == purifierKey } as? HomeDevice.Purifier)?.let { device ->
        PurifierDialog(device, onSet = { vm.setPurifier(device, it) }, onDismiss = { purifierKey = null })
    }
}

@Composable
private fun HomeDevice.detail(): String {
    if (!reachable) return stringResource(R.string.home_unreachable)
    return when (this) {
        is HomeDevice.Relay -> stringResource(if (on) R.string.relay_on else R.string.relay_off)
        is HomeDevice.Feeder -> nextMeal?.let { stringResource(R.string.feeder_next, it.time()) }.orEmpty()
        is HomeDevice.Purifier -> listOfNotNull(
            state?.temperatureCelsius?.let { "$it°" },
            state?.humidity?.let { stringResource(R.string.percent, it) },
            state?.airQuality?.let { stringResource(it.label) },
        ).joinToString("  ·  ")
    }
}

@get:StringRes
private val AirQuality.label: Int
    get() = when (this) {
        AirQuality.GOOD -> R.string.air_good
        AirQuality.FAIR -> R.string.air_fair
        AirQuality.POOR -> R.string.air_poor
        AirQuality.VERY_POOR -> R.string.air_very_poor
        AirQuality.SEVERE -> R.string.air_severe
        AirQuality.EXTREME -> R.string.air_extreme
    }

private fun Meal.time() = "%02d:%02d".format(hour, minute)

@Composable
private fun DeviceChip(symbol: String, name: String, detail: String?, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color.White), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EvimColors.Chip,
            contentColor = Color.White,
            focusedContainerColor = Color(0x33FFFFFF),
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier.size(18.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(symbol, fontSize = 9.sp)
            }
            Spacer(Modifier.width(7.dp))
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            if (!detail.isNullOrEmpty()) {
                Text("  $detail", color = EvimColors.TextDim, fontSize = 10.sp, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(4.dp).background(if (active) EvimColors.Online else EvimColors.TextFaint, CircleShape))
        }
    }
}

@Composable
private fun FeederDialog(device: HomeDevice.Feeder, onFeed: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val feeding = stringResource(R.string.feed_started)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val feed = { portions: Int ->
        onFeed(portions)
        Toast.makeText(context, feeding, Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            DialogTitle(device.name, device.nextMeal?.let { stringResource(R.string.feeder_next_meal, it.time(), it.portions) })
            if (device.reachable) {
                // Yanlışlıkla OK'e basınca mama dökülmesin diye odak "Vazgeç"te başlar
                MenuItem(stringResource(R.string.feed_one)) { feed(1) }
                MenuItem(stringResource(R.string.feed_two)) { feed(2) }
                MenuItem(stringResource(R.string.cancel), Modifier.focusRequester(first), onDismiss)
            } else {
                MenuItem(stringResource(R.string.home_unreachable), Modifier.focusRequester(first), onDismiss)
            }
        }
    }
}

@Composable
private fun PurifierDialog(device: HomeDevice.Purifier, onSet: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val state = device.state

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            val humidity = state?.humidity?.let { stringResource(R.string.purifier_humidity, it) }
            val air = state?.airQuality?.let { stringResource(R.string.purifier_air, stringResource(it.label)) }
            val summary = listOfNotNull(
                state?.temperatureCelsius?.let { "$it°" },
                humidity,
                air,
                state?.pm25?.let { "PM2.5 $it" },
            ).joinToString("  ·  ")
            DialogTitle(device.name, summary)
            if (state == null) {
                MenuItem(stringResource(R.string.home_unreachable), Modifier.focusRequester(first), onDismiss)
                return@Panel
            }
            MenuItem(
                stringResource(if (state.power) R.string.purifier_turn_off else R.string.purifier_turn_on),
                Modifier.focusRequester(first),
            ) { onSet(mapOf("fpwr" to if (state.power) "OFF" else "ON")) }
            if (state.power) {
                ToggleItem(R.string.purifier_auto, state.auto) { onSet(mapOf("auto" to if (state.auto) "OFF" else "ON")) }
                val speed = state.fanSpeed
                if (speed != null) {
                    // Elle hız seçmek otomatik modu kapatır
                    if (speed > 1) MenuItem(stringResource(R.string.purifier_fan_down, speed)) { onSet(fanSpeed(speed - 1)) }
                    if (speed < 10) MenuItem(stringResource(R.string.purifier_fan_up, speed)) { onSet(fanSpeed(speed + 1)) }
                }
                ToggleItem(R.string.purifier_heating, state.heating) { onSet(mapOf("hmod" to if (state.heating) "OFF" else "HEAT")) }
                val target = state.targetCelsius
                if (state.heating && target != null) {
                    // Dyson ısıtıcının kabul ettiği aralık 1-37 °C
                    if (target > 1) MenuItem(stringResource(R.string.purifier_temp_down, target)) { onSet(mapOf("hmax" to celsiusToDyson(target - 1))) }
                    if (target < 37) MenuItem(stringResource(R.string.purifier_temp_up, target)) { onSet(mapOf("hmax" to celsiusToDyson(target + 1))) }
                }
            }
        }
    }
}

private fun fanSpeed(speed: Int) = mapOf("auto" to "OFF", "fnsp" to "%04d".format(speed))

@Composable
private fun ToggleItem(@StringRes label: Int, on: Boolean, onClick: () -> Unit) {
    val value = stringResource(if (on) R.string.relay_on else R.string.relay_off)
    MenuItem("${stringResource(label)}: $value", onClick = onClick)
}

@Composable
private fun DialogTitle(title: String, subtitle: String?) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (!subtitle.isNullOrEmpty()) Text(subtitle, color = EvimColors.TextDim, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))
}
