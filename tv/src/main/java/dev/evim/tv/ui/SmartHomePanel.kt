package dev.evim.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.HomeDevice

private sealed interface HomeStep {
    data object List : HomeStep
    data object Add : HomeStep
    data object Tuya : HomeStep
    data object Dyson : HomeStep
    data class Device(val key: String) : HomeStep
    data class Remove(val key: String) : HomeStep
}

/**
 * Evim kutusundan açılan akıllı ev paneli: cihaz ekleme ve eklenmiş cihazları kaldırma / yeniden adlandırma.
 * Sıralama widget'ta kalır. [startAtAdd] ise (hiç cihaz yokken) doğrudan marka seçimiyle açılır.
 */
@Composable
fun SmartHomeDialog(vm: LauncherViewModel, startAtAdd: Boolean, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf<HomeStep>(if (startAtAdd) HomeStep.Add else HomeStep.List) }
    var renaming by remember { mutableStateOf<HomeDevice?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { first.requestFocus() } }

    fun back() {
        step = when (val current = step) {
            HomeStep.List -> return onDismiss()
            HomeStep.Add -> if (startAtAdd) return onDismiss() else HomeStep.List
            HomeStep.Tuya, HomeStep.Dyson -> HomeStep.Add
            is HomeStep.Device -> HomeStep.List
            is HomeStep.Remove -> HomeStep.Device(current.key)
        }
    }

    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 440.dp) {
            when (val current = step) {
                HomeStep.List -> {
                    PanelTitle(stringResource(R.string.smart_home_title), stringResource(R.string.smart_home_subtitle))
                    MenuItem("+  ${stringResource(R.string.smart_home_add)}", Modifier.focusRequester(first)) { step = HomeStep.Add }
                    vm.homeDevices.forEach { device ->
                        MenuItem("${device.name}  ·  ${stringResource(device.brand())}") { step = HomeStep.Device(device.key) }
                    }
                }
                HomeStep.Add -> {
                    PanelTitle(stringResource(R.string.smart_home_add), stringResource(R.string.smart_home_add_subtitle))
                    MenuItem("${stringResource(R.string.brand_tuya)}  ·  ${stringResource(R.string.brand_tuya_kinds)}", Modifier.focusRequester(first)) { step = HomeStep.Tuya }
                    MenuItem("${stringResource(R.string.brand_dyson)}  ·  ${stringResource(R.string.brand_dyson_kinds)}") { step = HomeStep.Dyson }
                }
                HomeStep.Tuya -> TuyaAddFlow(vm, first, onDone = { step = HomeStep.List })
                HomeStep.Dyson -> DysonAddFlow(vm, first, onDone = { step = HomeStep.List })
                is HomeStep.Device -> {
                    val device = vm.homeDevices.firstOrNull { it.key == current.key }
                    if (device == null) {
                        step = HomeStep.List
                        return@Panel
                    }
                    PanelTitle(device.name, stringResource(device.brand()))
                    MenuItem(stringResource(R.string.menu_rename), Modifier.focusRequester(first)) { renaming = device }
                    if (vm.hasCustomName(device)) {
                        MenuItem(stringResource(R.string.menu_reset_name)) { vm.renameDevice(device, null) }
                    }
                    // Anahtar değiştiyse (cihaz yeniden kurulduysa) hesapla yeniden alınır
                    MenuItem(stringResource(R.string.menu_reconnect)) {
                        step = if (device is HomeDevice.Purifier) HomeStep.Dyson else HomeStep.Tuya
                    }
                    MenuItem(stringResource(R.string.menu_remove)) { step = HomeStep.Remove(device.key) }
                }
                is HomeStep.Remove -> {
                    val device = vm.homeDevices.firstOrNull { it.key == current.key }
                    if (device == null) {
                        step = HomeStep.List
                        return@Panel
                    }
                    // Geri alınamaz: odak "Vazgeç"te başlar
                    PanelTitle(stringResource(R.string.remove_device_title, device.name), null)
                    PanelBody(stringResource(R.string.remove_device_body, stringResource(device.brand())))
                    MenuItem(stringResource(R.string.remove_confirm)) {
                        vm.removeDevice(device)
                        step = if (vm.homeDevices.size <= 1) HomeStep.Add else HomeStep.List
                    }
                    MenuItem(stringResource(R.string.cancel), Modifier.focusRequester(first), ::back)
                }
            }
        }
    }

    renaming?.let { device ->
        RenameDialog(
            current = device.name,
            onSave = { vm.renameDevice(device, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

private fun HomeDevice.brand() = if (this is HomeDevice.Purifier) R.string.brand_dyson else R.string.brand_tuya

@Composable
internal fun PanelTitle(title: String, subtitle: String?) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = EvimColors.TextDim, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun PanelBody(text: String) {
    Text(text, color = EvimColors.TextDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
}
