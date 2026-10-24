package dev.evim.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Outlet
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
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
    data object Camera : HomeStep
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
            HomeStep.Tuya, HomeStep.Dyson, HomeStep.Camera -> HomeStep.Add
            is HomeStep.Device -> HomeStep.List
            is HomeStep.Remove -> HomeStep.Device(current.key)
        }
    }

    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 540.dp) {
            when (val current = step) {
                HomeStep.List -> {
                    PanelHeader(stringResource(R.string.smart_home_title), stringResource(R.string.smart_home_subtitle), Icons.Rounded.Home)
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        ListRow(
                            stringResource(R.string.smart_home_add),
                            stringResource(R.string.smart_home_add_hint),
                            Icons.Rounded.Add,
                            trailing = Trailing.Chevron,
                            modifier = Modifier.focusRequester(first),
                        ) { step = HomeStep.Add }
                        vm.homeDevices.forEach { device ->
                            ListRow(
                                device.name,
                                "${stringResource(device.brand())}  ·  ${stringResource(if (device.reachable) R.string.home_online else R.string.home_unreachable)}",
                                device.icon(),
                                device.tint(),
                                trailing = Trailing.Chevron,
                            ) { step = HomeStep.Device(device.key) }
                        }
                    }
                }
                HomeStep.Add -> {
                    PanelHeader(stringResource(R.string.smart_home_add_question), stringResource(R.string.smart_home_add_subtitle), Icons.Rounded.Add)
                    CardRow {
                        ChoiceCard(
                            Icons.Rounded.Outlet,
                            stringResource(R.string.card_tuya_title),
                            stringResource(R.string.brand_tuya),
                            Modifier.weight(1f).focusRequester(first),
                            EvimColors.Tuya,
                        ) { step = HomeStep.Tuya }
                        ChoiceCard(
                            Icons.Rounded.Air,
                            stringResource(R.string.card_dyson_title),
                            stringResource(R.string.brand_dyson),
                            Modifier.weight(1f),
                            EvimColors.Dyson,
                        ) { step = HomeStep.Dyson }
                        ChoiceCard(
                            Icons.Rounded.Videocam,
                            stringResource(R.string.card_camera_title),
                            stringResource(R.string.brand_eufy),
                            Modifier.weight(1f),
                            EvimColors.Camera,
                        ) { step = HomeStep.Camera }
                    }
                }
                HomeStep.Tuya -> TuyaAddFlow(vm, first, onDone = { step = HomeStep.List })
                HomeStep.Dyson -> DysonAddFlow(vm, first, onDone = { step = HomeStep.List })
                HomeStep.Camera -> EufyAddFlow(vm, first, onDone = { step = HomeStep.List })
                is HomeStep.Device -> {
                    val device = vm.homeDevices.firstOrNull { it.key == current.key }
                    if (device == null) {
                        step = HomeStep.List
                        return@Panel
                    }
                    PanelHeader(device.name, stringResource(device.brand()), device.icon(), device.tint())
                    CardRow {
                        ChoiceCard(Icons.Rounded.Edit, stringResource(R.string.menu_rename), null, Modifier.weight(1f).focusRequester(first)) { renaming = device }
                        // Anahtar değiştiyse (cihaz yeniden kurulduysa) hesapla yeniden alınır
                        ChoiceCard(Icons.Rounded.Sync, stringResource(R.string.menu_reconnect), stringResource(R.string.reconnect_hint), Modifier.weight(1f)) {
                            step = when (device) {
                                is HomeDevice.Purifier -> HomeStep.Dyson
                                is HomeDevice.Camera -> HomeStep.Camera
                                else -> HomeStep.Tuya
                            }
                        }
                        ChoiceCard(Icons.Rounded.Delete, stringResource(R.string.menu_remove), null, Modifier.weight(1f), EvimColors.Danger) {
                            step = HomeStep.Remove(device.key)
                        }
                    }
                    if (vm.hasCustomName(device)) {
                        Spacer(Modifier.height(6.dp))
                        ListRow(stringResource(R.string.menu_reset_name), icon = Icons.Rounded.Undo) { vm.renameDevice(device, null) }
                    }
                }
                is HomeStep.Remove -> {
                    val device = vm.homeDevices.firstOrNull { it.key == current.key }
                    if (device == null) {
                        step = HomeStep.List
                        return@Panel
                    }
                    PanelHeader(stringResource(R.string.remove_device_title, device.name), null, Icons.Rounded.Delete, EvimColors.Danger)
                    PanelText(stringResource(R.string.remove_device_body, stringResource(device.brand())))
                    // Geri alınamaz: odak "Vazgeç"te başlar
                    PanelActions {
                        PillButton(stringResource(R.string.cancel), Modifier.focusRequester(first), onClick = ::back)
                        PillButton(stringResource(R.string.remove_confirm), danger = true) {
                            vm.removeDevice(device)
                            step = if (vm.homeDevices.size <= 1) HomeStep.Add else HomeStep.List
                        }
                    }
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

private fun HomeDevice.brand() = when (this) {
    is HomeDevice.Purifier -> R.string.brand_dyson
    is HomeDevice.Camera -> R.string.brand_eufy
    else -> R.string.brand_tuya
}

/** Cihaz türünün simgesi ve rengi; panellerde ve menülerde aynı. */
internal fun HomeDevice.icon(): ImageVector = when (this) {
    is HomeDevice.Purifier -> Icons.Rounded.Air
    is HomeDevice.Feeder -> Icons.Rounded.Pets
    is HomeDevice.Relay -> Icons.Rounded.Outlet
    is HomeDevice.Other -> Icons.Rounded.DevicesOther
    is HomeDevice.Camera -> Icons.Rounded.Videocam
}

internal fun HomeDevice.tint(): Color = when (this) {
    is HomeDevice.Purifier -> EvimColors.Dyson
    is HomeDevice.Feeder -> EvimColors.Feeder
    is HomeDevice.Relay, is HomeDevice.Other -> EvimColors.Tuya
    is HomeDevice.Camera -> EvimColors.Camera
}


