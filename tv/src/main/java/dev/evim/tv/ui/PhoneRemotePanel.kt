package dev.evim.tv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R

/** Üst şeritteki kumanda simgesinden açılır: telefondan kumandayı açar/kapatır, açıkken QR kodu gösterir. */
@Composable
fun PhoneRemoteDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(vm.remoteEnabled) { runCatching { first.requestFocus() } }
    // Adres boşsa ağ gelene kadar yeniden dene
    LaunchedEffect(vm.remoteEnabled, vm.remoteUrl) {
        if (vm.remoteEnabled && vm.remoteUrl == null) {
            while (true) { vm.refreshRemoteUrl(); delay(2000) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 720.dp) {
            PanelHeader(stringResource(R.string.remote_title), stringResource(R.string.remote_subtitle), Icons.Rounded.PhoneAndroid)
            val url = vm.remoteUrl
            if (vm.remoteEnabled) {
                if (url == null) {
                    PanelNote(stringResource(R.string.receive_no_network), EvimColors.Danger)
                } else {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(url, side = 170.dp)
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            PanelText(stringResource(R.string.remote_body))
                            Text(url, color = EvimColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            } else {
                PanelText(stringResource(R.string.remote_off_body))
            }
            PanelActions {
                PillButton(
                    stringResource(if (vm.remoteEnabled) R.string.remote_turn_off else R.string.remote_turn_on),
                    Modifier.focusRequester(first),
                    primary = !vm.remoteEnabled,
                ) { vm.toggleRemote(!vm.remoteEnabled) }
                if (vm.remoteEnabled) PillButton(stringResource(R.string.remote_reset), danger = true) { vm.resetRemote() }
                PillButton(stringResource(R.string.done), onClick = onDismiss)
            }
        }
    }
}
