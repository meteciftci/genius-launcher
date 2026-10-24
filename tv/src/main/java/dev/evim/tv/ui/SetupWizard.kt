package dev.evim.tv.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.AdbState
import dev.evim.tv.data.CleanupApp
import dev.evim.tv.data.SwitchCheck
import kotlinx.coroutines.launch

private enum class WizardStep { ADB, HOME, CLEANUP, AWAKE, DONE }

/**
 * İlk kurulum: TV'nin kendi ADB'sine izin, Home tuşu, sistem uygulaması temizliği ve isteğe bağlı
 * "uyanık kal". Her adım atlanabilir; yeniden çalıştırınca yapılmış adımlar işaretli görünür.
 * Geri tuşu ya da "Bitti" sihirbazı kapatır; ilk açılışta bir daha kendiliğinden gelmez.
 */
@Composable
fun SetupWizardDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(WizardStep.ADB) }
    var adbReady by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { focus.requestFocus() } }

    fun close() {
        vm.finishSetup()
        onDismiss()
    }

    // ADB olmadan sonraki adımların hiçbiri yapılamaz; doğrudan sona geçilir
    fun next() {
        step = if (step == WizardStep.ADB && !adbReady) WizardStep.DONE else WizardStep.entries[step.ordinal + 1]
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 480.dp) {
            Text(
                stringResource(R.string.wizard_progress, step.ordinal + 1, WizardStep.entries.size),
                color = EvimColors.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            when (step) {
                WizardStep.ADB -> AdbStep(vm, focus, onReady = { adbReady = it }, onNext = ::next)
                WizardStep.HOME -> HomeStep(vm, focus, onNext = ::next)
                WizardStep.CLEANUP -> CleanupStep(vm, focus, onNext = ::next)
                WizardStep.AWAKE -> AwakeStep(vm, focus, onNext = ::next)
                WizardStep.DONE -> {
                    PanelTitle(stringResource(R.string.wizard_done_title), null)
                    PanelBody(stringResource(if (adbReady) R.string.wizard_done_body else R.string.wizard_done_no_adb))
                    MenuItem(stringResource(R.string.done), Modifier.focusRequester(focus), ::close)
                }
            }
        }
    }
}

@Composable
private fun AdbStep(vm: LauncherViewModel, focus: FocusRequester, onReady: (Boolean) -> Unit, onNext: () -> Unit) {
    var state by remember { mutableStateOf<AdbState?>(null) }
    val scope = rememberCoroutineScope()
    fun check() {
        state = null
        scope.launch {
            state = vm.adbState()
            onReady(state == AdbState.READY)
        }
    }
    LaunchedEffect(Unit) { check() }

    PanelTitle(stringResource(R.string.wizard_adb_title), null)
    PanelBody(stringResource(R.string.wizard_adb_why))
    when (state) {
        null -> {
            PanelBody(stringResource(R.string.working))
            MenuItem(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onNext)
        }
        AdbState.READY -> {
            PanelBody("✓  " + stringResource(R.string.wizard_adb_ready))
            MenuItem(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), onNext)
        }
        AdbState.NEEDS_PERMISSION -> {
            PanelBody(stringResource(R.string.wizard_adb_permission))
            MenuItem(stringResource(R.string.wizard_check_again), Modifier.focusRequester(focus), ::check)
            MenuItem(stringResource(R.string.wizard_skip), onClick = onNext)
        }
        AdbState.OFF -> {
            PanelBody(stringResource(R.string.wizard_adb_off))
            MenuItem(stringResource(R.string.wizard_open_settings), Modifier.focusRequester(focus)) {
                vm.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
            }
            MenuItem(stringResource(R.string.wizard_check_again), onClick = ::check)
            MenuItem(stringResource(R.string.wizard_skip), onClick = onNext)
        }
    }
}

@Composable
private fun HomeStep(vm: LauncherViewModel, focus: FocusRequester, onNext: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var erasesNetworks by remember { mutableStateOf(false) }

    PanelTitle(stringResource(R.string.wizard_home_title), null)
    if (vm.geniusIsHome) {
        PanelBody("✓  " + stringResource(R.string.wizard_home_done))
        MenuItem(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), onNext)
        return
    }
    LaunchedEffect(Unit) {
        vm.checkHomeSwitch { check, failure ->
            when {
                failure != null -> error = failure
                check is SwitchCheck.Blocked -> error = R.string.home_blocked
                check is SwitchCheck.Ready -> erasesNetworks = check.erasesOtherNetworks
            }
        }
    }
    PanelBody(stringResource(R.string.home_to_genius_body))
    if (erasesNetworks) PanelBody(stringResource(R.string.home_erases_networks))
    if (error == null) {
        MenuItem(stringResource(R.string.wizard_home_do)) {
            if (busy) return@MenuItem
            busy = true
            vm.setGeniusHome(true, fromWizard = true) { failure ->
                busy = false
                if (failure == null) onNext() else error = failure
            }
        }
    }
    MenuItem(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onNext)
    if (busy) PanelBody(stringResource(R.string.working))
    error?.let { PanelBody(stringResource(it)) }
}

@Composable
private fun CleanupStep(vm: LauncherViewModel, focus: FocusRequester, onNext: () -> Unit) {
    var apps by remember { mutableStateOf<List<CleanupApp>?>(null) }
    var checked by remember { mutableStateOf(emptySet<String>()) }
    var removedBefore by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        removedBefore = vm.setupChanges().second
        val list = runCatching { vm.cleanupCandidates() }.getOrDefault(emptyList())
        checked = list.filter { it.recommended && !it.ownsCurrentWifi }.map { it.packageName }.toSet()
        apps = list
    }

    PanelTitle(stringResource(R.string.wizard_cleanup_title), stringResource(if (vm.isXiaomi) R.string.wizard_cleanup_xiaomi else R.string.wizard_cleanup_generic))
    if (removedBefore > 0) PanelBody("✓  " + stringResource(R.string.wizard_cleanup_done, removedBefore))
    val list = apps
    when {
        list == null -> {
            PanelBody(stringResource(R.string.working))
            MenuItem(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onNext)
        }
        list.isEmpty() -> {
            PanelBody(stringResource(R.string.wizard_cleanup_none))
            MenuItem(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), onNext)
        }
        else -> {
            PanelBody(stringResource(R.string.wizard_cleanup_body))
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                list.forEachIndexed { index, app ->
                    val mark = when {
                        app.ownsCurrentWifi -> "⊘"
                        app.packageName in checked -> "☑"
                        else -> "☐"
                    }
                    val note = when {
                        app.ownsCurrentWifi -> stringResource(R.string.wizard_cleanup_wifi)
                        app.recommended -> stringResource(R.string.wizard_cleanup_recommended)
                        else -> null
                    }
                    MenuItem("$mark  ${app.label}" + (note?.let { "  ·  $it" } ?: ""), if (index == 0) Modifier.focusRequester(focus) else Modifier) {
                        if (app.ownsCurrentWifi) return@MenuItem
                        checked = if (app.packageName in checked) checked - app.packageName else checked + app.packageName
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MenuItem(stringResource(R.string.wizard_cleanup_do, checked.size)) {
                if (busy || checked.isEmpty()) return@MenuItem
                busy = true
                scope.launch {
                    vm.removeApps(checked.toList())
                    onNext()
                }
            }
            MenuItem(stringResource(R.string.wizard_skip), onClick = onNext)
            if (busy) PanelBody(stringResource(R.string.working))
        }
    }
}

@Composable
private fun AwakeStep(vm: LauncherViewModel, focus: FocusRequester, onNext: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { done = vm.setupChanges().third }

    PanelTitle(stringResource(R.string.wizard_awake_title), stringResource(R.string.wizard_optional))
    PanelBody(stringResource(R.string.wizard_awake_body))
    if (done) {
        PanelBody("✓  " + stringResource(R.string.wizard_awake_done))
        MenuItem(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), onNext)
        return
    }
    MenuItem(stringResource(R.string.wizard_awake_do)) {
        scope.launch {
            error = vm.keepAwake()
            if (error == null) onNext()
        }
    }
    MenuItem(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onNext)
    error?.let { PanelBody(stringResource(it)) }
}
