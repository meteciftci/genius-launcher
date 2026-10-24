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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.SwitchCheck

private enum class Step { MENU, ABOUT, TO_GENIUS, TO_GOOGLE, UNINSTALL }

/**
 * Launcher'ın kendi ayarları: gizli uygulamalar, hava durumu şehri, Home tuşu, hakkında ve kaldırma.
 * Onay ve bilgi adımları ayrı pencere açmadan aynı panelin içinde gösterilir.
 */
@Composable
fun SettingsDialog(
    vm: LauncherViewModel,
    onHiddenApps: () -> Unit,
    onCity: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.MENU) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var erasesNetworks by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { first.requestFocus() } }

    fun go(next: Step) {
        error = null
        step = next
    }

    Dialog(
        onDismissRequest = { if (step != Step.MENU) go(Step.MENU) else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Panel(width = 440.dp) {
            when (step) {
                Step.MENU -> {
                    Title(stringResource(R.string.settings_title), null)
                    val home = stringResource(if (vm.geniusIsHome) R.string.settings_home_genius else R.string.settings_home_google)
                    MenuItem(
                        "${stringResource(R.string.hidden_apps_title)}  ·  ${vm.hiddenApps.size}",
                        Modifier.focusRequester(first),
                        onHiddenApps,
                    )
                    MenuItem("${stringResource(R.string.settings_city)}  ·  ${vm.city.name}", onClick = onCity)
                    MenuItem("${stringResource(R.string.settings_home)}  ·  $home") {
                        if (busy) return@MenuItem
                        if (vm.geniusIsHome) {
                            go(Step.TO_GOOGLE)
                        } else {
                            // Önce Wi-Fi denetimi: geçiş bağlı olunan ağı silecekse onay bile istenmez
                            busy = true
                            vm.checkHomeSwitch { check, failure ->
                                busy = false
                                when {
                                    failure != null -> error = failure
                                    check is SwitchCheck.Blocked -> error = R.string.home_blocked
                                    check is SwitchCheck.Ready -> {
                                        erasesNetworks = check.erasesOtherNetworks
                                        go(Step.TO_GENIUS)
                                    }
                                }
                            }
                        }
                    }
                    MenuItem(stringResource(R.string.settings_about), onClick = { go(Step.ABOUT) })
                    MenuItem(stringResource(R.string.settings_uninstall), onClick = { go(Step.UNINSTALL) })
                }
                Step.ABOUT -> {
                    val context = LocalContext.current
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                    }
                    Title(stringResource(R.string.app_name), stringResource(R.string.about_version, version))
                    Body(stringResource(R.string.about_body))
                    Body(stringResource(R.string.about_source))
                    MenuItem(stringResource(R.string.done), Modifier.focusRequester(first)) { go(Step.MENU) }
                }
                Step.TO_GENIUS -> Confirm(
                    title = stringResource(R.string.home_to_genius_title),
                    body = listOfNotNull(
                        stringResource(R.string.home_to_genius_body),
                        if (erasesNetworks) stringResource(R.string.home_erases_networks) else null,
                    ),
                    confirm = stringResource(R.string.switch_confirm),
                    busy = busy,
                    focus = first,
                    onConfirm = {
                        busy = true
                        vm.setGeniusHome(true) { failure ->
                            busy = false
                            if (failure == null) go(Step.MENU) else error = failure
                        }
                    },
                    onCancel = { go(Step.MENU) },
                )
                Step.TO_GOOGLE -> Confirm(
                    title = stringResource(R.string.home_to_google_title),
                    body = listOf(stringResource(R.string.home_to_google_body)),
                    confirm = stringResource(R.string.switch_confirm),
                    busy = busy,
                    focus = first,
                    onConfirm = {
                        busy = true
                        vm.setGeniusHome(false) { failure ->
                            busy = false
                            if (failure == null) go(Step.MENU) else error = failure
                        }
                    },
                    onCancel = { go(Step.MENU) },
                )
                Step.UNINSTALL -> Confirm(
                    title = stringResource(R.string.uninstall_title),
                    body = listOf(stringResource(R.string.uninstall_body)),
                    confirm = stringResource(R.string.uninstall_confirm),
                    busy = busy,
                    focus = first,
                    onConfirm = {
                        busy = true
                        vm.uninstall { failure ->
                            busy = false
                            error = failure
                        }
                    },
                    onCancel = { go(Step.MENU) },
                )
            }
            if (busy) Body(stringResource(R.string.working))
            error?.let { Text(stringResource(it), color = EvimColors.Warning, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

/** Geri alınması zor işlemler için: odak "Vazgeç"te başlar ki yanlışlıkla OK onaylamasın. */
@Composable
private fun Confirm(
    title: String,
    body: List<String>,
    confirm: String,
    busy: Boolean,
    focus: FocusRequester,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Title(title, null)
    body.forEach { Body(it) }
    MenuItem(confirm) { if (!busy) onConfirm() }
    MenuItem(stringResource(R.string.cancel), Modifier.focusRequester(focus), onCancel)
}

@Composable
private fun Title(title: String, subtitle: String?) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = EvimColors.TextDim, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun Body(text: String) {
    Text(text, color = EvimColors.TextDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 10.dp))
}
