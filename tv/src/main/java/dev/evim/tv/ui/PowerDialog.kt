package dev.evim.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R

/**
 * Kısayollardaki güç simgesinin penceresi: kutuyu yeniden başlatır ya da tamamen kapatır.
 * Odak "Vazgeç"te başlar ki simgeye basan OK yanlışlıkla kutuyu kapatmasın.
 */
@Composable
fun PowerDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun power(restart: Boolean) {
        if (busy) return
        busy = true
        error = null
        vm.power(restart) { failure ->
            busy = false
            error = failure
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 460.dp) {
            PanelHeader(stringResource(R.string.power_title), null, Icons.Rounded.PowerSettingsNew)
            PanelText(stringResource(R.string.power_body))
            PanelActions {
                PillButton(stringResource(R.string.cancel), Modifier.focusRequester(first), onClick = onDismiss)
                PillButton(stringResource(R.string.power_restart), primary = true) { power(restart = true) }
                PillButton(stringResource(R.string.power_shutdown), danger = true) { power(restart = false) }
            }
            if (busy) PanelNote(stringResource(R.string.working), EvimColors.Accent)
            error?.let { PanelNote(stringResource(it)) }
        }
    }
}
