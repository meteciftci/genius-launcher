package dev.evim.tv.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.focus.focusProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class WizardStep { ADB, HOME, CLEANUP, AWAKE, DONE }

/**
 * İlk kurulum, tam ekran sayfalar halinde: solda adımı anlatan görsel, sağda açıklama ve düğmeler.
 * Adımlar: TV'nin kendi ADB'sine izin, Home tuşu, sistem uygulaması temizliği ve isteğe bağlı "uyanık kal".
 * Her adım atlanabilir; yeniden çalıştırınca yapılmış adımlar işaretli görünür.
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

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(EvimColors.Background)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
                Progress(step)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when (step) {
                            WizardStep.ADB -> AdbArt()
                            WizardStep.HOME -> HomeArt()
                            WizardStep.CLEANUP -> CleanupArt()
                            WizardStep.AWAKE -> AwakeArt()
                            WizardStep.DONE -> DoneArt()
                        }
                    }
                    Spacer(Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        when (step) {
                            WizardStep.ADB -> AdbStep(vm, focus, onReady = { adbReady = it }, onNext = ::next)
                            WizardStep.HOME -> HomeStep(vm, focus, onNext = ::next)
                            WizardStep.CLEANUP -> CleanupStep(vm, focus, onNext = ::next)
                            WizardStep.AWAKE -> AwakeStep(vm, focus, onNext = ::next)
                            WizardStep.DONE -> {
                                StepText(stringResource(R.string.wizard_done_title), stringResource(if (adbReady) R.string.wizard_done_body else R.string.wizard_done_no_adb))
                                Actions { PillButton(stringResource(R.string.done), Modifier.focusRequester(focus), primary = true, onClick = ::close) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Üstte adım çubukları ve "Kurulum · adım 2 / 5". */
@Composable
private fun Progress(step: WizardStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.wizard_progress, step.ordinal + 1, WizardStep.entries.size),
            color = EvimColors.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(16.dp))
        WizardStep.entries.forEach {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .background(if (it.ordinal <= step.ordinal) EvimColors.Accent else Color(0x26FFFFFF), RoundedCornerShape(50)),
            )
        }
    }
}

/** Sağ sütunun başı: büyük başlık ve açıklama, sola hizalı. */
@Composable
private fun StepText(title: String, body: String, eyebrow: String? = null) {
    if (eyebrow != null) Text(eyebrow, color = EvimColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    Spacer(Modifier.height(10.dp))
    Text(body, color = EvimColors.TextDim, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun Status(text: String, tint: Color) {
    Row(
        Modifier.padding(bottom = 10.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun Actions(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
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
    // İzin verilince ya da hata ayıklama açılıp geri gelinince kendiliğinden fark edilsin
    LaunchedEffect(Unit) {
        while (true) {
            val next = vm.adbState()
            if (next != state) {
                state = next
                onReady(next == AdbState.READY)
            }
            if (next == AdbState.READY) break
            delay(RECHECK_MS)
        }
    }

    StepText(stringResource(R.string.wizard_adb_title), stringResource(R.string.wizard_adb_why))
    when (state) {
        null -> {
            Status(stringResource(R.string.working), EvimColors.Accent)
            Actions { PillButton(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onClick = onNext) }
        }
        AdbState.READY -> {
            Status(stringResource(R.string.wizard_adb_ready), EvimColors.Online)
            Actions { PillButton(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), primary = true, onClick = onNext) }
        }
        AdbState.NEEDS_PERMISSION -> {
            Status(stringResource(R.string.wizard_adb_permission), EvimColors.Warning)
            Actions {
                PillButton(stringResource(R.string.wizard_check_again), Modifier.focusRequester(focus), primary = true, onClick = ::check)
                PillButton(stringResource(R.string.wizard_skip), onClick = onNext)
            }
        }
        AdbState.OFF -> {
            Status(stringResource(R.string.wizard_adb_off), EvimColors.Warning)
            Actions {
                PillButton(stringResource(R.string.wizard_open_settings), Modifier.focusRequester(focus), primary = true) {
                    vm.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
                }
                PillButton(stringResource(R.string.wizard_check_again), onClick = ::check)
                PillButton(stringResource(R.string.wizard_skip), onClick = onNext)
            }
        }
    }
}

@Composable
private fun HomeStep(vm: LauncherViewModel, focus: FocusRequester, onNext: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var erasesNetworks by remember { mutableStateOf(false) }

    StepText(stringResource(R.string.wizard_home_title), stringResource(R.string.home_to_genius_body))
    if (vm.geniusIsHome) {
        Status(stringResource(R.string.wizard_home_done), EvimColors.Online)
        Actions { PillButton(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), primary = true, onClick = onNext) }
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
    if (erasesNetworks) Status(stringResource(R.string.home_erases_networks), EvimColors.Warning)
    error?.let { Status(stringResource(it), EvimColors.Danger) }
    if (busy) Status(stringResource(R.string.working), EvimColors.Accent)
    // Odak "Atla"da başlar: Home tuşunu almak isteyerek yapılmalı
    Actions {
        PillButton(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onClick = onNext)
        if (error == null) {
            PillButton(stringResource(R.string.wizard_home_do), primary = true) {
                if (busy) return@PillButton
                busy = true
                vm.setGeniusHome(true, fromWizard = true) { failure ->
                    busy = false
                    if (failure == null) onNext() else error = failure
                }
            }
        }
    }
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

    StepText(
        stringResource(R.string.wizard_cleanup_title),
        stringResource(R.string.wizard_cleanup_body),
        eyebrow = stringResource(if (vm.isXiaomi) R.string.wizard_cleanup_xiaomi else R.string.wizard_cleanup_generic),
    )
    if (removedBefore > 0) Status(stringResource(R.string.wizard_cleanup_done, removedBefore), EvimColors.Online)
    val list = apps
    when {
        list == null -> {
            Status(stringResource(R.string.working), EvimColors.Accent)
            Actions { PillButton(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onClick = onNext) }
        }
        list.isEmpty() -> {
            Status(stringResource(R.string.wizard_cleanup_none), EvimColors.Online)
            Actions { PillButton(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), primary = true, onClick = onNext) }
        }
        else -> {
            // Listenin altından aşağı basınca odak en yakın düğme olan "Atla"ya değil "Kaldır"a gelsin
            val remove = remember { FocusRequester() }
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                list.forEachIndexed { index, app ->
                    val note = when {
                        app.ownsCurrentWifi -> stringResource(R.string.wizard_cleanup_wifi)
                        app.recommended -> stringResource(R.string.wizard_cleanup_recommended)
                        else -> null
                    }
                    ListRow(
                        app.label,
                        note,
                        trailing = Trailing.Check(app.packageName in checked),
                        enabled = !app.ownsCurrentWifi,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(focus) else Modifier)
                            .then(if (index == list.lastIndex) Modifier.focusProperties { down = remove } else Modifier),
                    ) {
                        if (app.ownsCurrentWifi) return@ListRow
                        checked = if (app.packageName in checked) checked - app.packageName else checked + app.packageName
                    }
                }
            }
            if (busy) Status(stringResource(R.string.working), EvimColors.Accent)
            Actions {
                PillButton(stringResource(R.string.wizard_cleanup_do, checked.size), Modifier.focusRequester(remove), primary = true) {
                    if (busy || checked.isEmpty()) return@PillButton
                    busy = true
                    scope.launch {
                        vm.removeApps(checked.toList())
                        onNext()
                    }
                }
                PillButton(stringResource(R.string.wizard_skip), onClick = onNext)
            }
        }
    }
}

@Composable
private fun AwakeStep(vm: LauncherViewModel, focus: FocusRequester, onNext: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { done = vm.setupChanges().third }

    StepText(stringResource(R.string.wizard_awake_title), stringResource(R.string.wizard_awake_body), eyebrow = stringResource(R.string.wizard_optional))
    if (done) {
        Status(stringResource(R.string.wizard_awake_done), EvimColors.Online)
        Actions { PillButton(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), primary = true, onClick = onNext) }
        return
    }
    error?.let { Status(stringResource(it), EvimColors.Danger) }
    Actions {
        PillButton(stringResource(R.string.wizard_skip), Modifier.focusRequester(focus), onClick = onNext)
        PillButton(stringResource(R.string.wizard_awake_do), primary = true) {
            scope.launch {
                error = vm.keepAwake()
                if (error == null) onNext()
            }
        }
    }
}

private const val RECHECK_MS = 3_000L
