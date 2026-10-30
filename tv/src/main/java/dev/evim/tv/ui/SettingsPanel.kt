package dev.evim.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.runtime.DisposableEffect
import dev.evim.tv.data.KeyTarget
import dev.evim.tv.data.RemoteKeys
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import dev.evim.tv.data.AppUpdate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.BuildConfig
import dev.evim.tv.R
import dev.evim.tv.data.SwitchCheck
import kotlinx.coroutines.launch

// Widget'ın sığdığı aralık: Dyson büyük kart, gerisi tek satır
private val WIDGET_LIMITS = listOf(3, 4, 5, 6)

private enum class Step { MENU, WIDGET, LOCK, KEYS, KEY_LEARN, KEY_PICK, PHONE, ABOUT, UPDATE, TO_GENIUS, TO_GOOGLE, UNDO_SETUP, UNINSTALL }

/**
 * Launcher'ın kendi ayarları: gizli uygulamalar, hava durumu şehri, Home tuşu, hakkında ve kaldırma.
 * Onay ve bilgi adımları ayrı pencere açmadan aynı panelin içinde gösterilir.
 */
@Composable
fun SettingsDialog(
    vm: LauncherViewModel,
    startAtUpdate: Boolean = false,
    onHiddenApps: () -> Unit,
    onCity: () -> Unit,
    onWizard: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(if (startAtUpdate) Step.UPDATE else Step.MENU) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var erasesNetworks by remember { mutableStateOf(false) }
    // Atanan ya da değiştirilen kumanda tuşunun kodu
    var keyCode by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(step) { runCatching { first.requestFocus() } }
    // Sihirbazın geri alınabilecek bir şey yapıp yapmadığı; geri alma ve kaldırma adımları buna göre değişir
    var setupChanged by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(step) {
        val (home, removed, awake) = vm.setupChanges()
        setupChanged = home || removed > 0 || awake
    }

    fun go(next: Step) {
        error = null
        step = next
    }

    Dialog(
        onDismissRequest = {
            when (step) {
                Step.MENU -> onDismiss()
                Step.KEY_LEARN, Step.KEY_PICK -> go(Step.KEYS)
                else -> go(Step.MENU)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Panel(width = 600.dp) {
            when (step) {
                Step.MENU -> {
                    PanelHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle), Icons.Rounded.Tune)
                    val home = stringResource(if (vm.geniusIsHome) R.string.settings_home_genius else R.string.settings_home_google)
                    CardRow {
                        ChoiceCard(
                            Icons.Rounded.VisibilityOff,
                            stringResource(R.string.hidden_apps_title),
                            stringResource(R.string.settings_hidden_count, vm.hiddenApps.size),
                            Modifier.weight(1f).focusRequester(first),
                            onClick = onHiddenApps,
                        )
                        ChoiceCard(Icons.Rounded.LocationOn, stringResource(R.string.settings_city), vm.city.name, Modifier.weight(1f), onClick = onCity)
                        ChoiceCard(
                            Icons.Rounded.SettingsRemote,
                            stringResource(R.string.keys_title),
                            if (vm.keyMappings.isEmpty()) stringResource(R.string.keys_none) else pluralStringResource(R.plurals.keys_count, vm.keyMappings.size, vm.keyMappings.size),
                            Modifier.weight(1f),
                        ) { go(Step.KEYS) }
                        ChoiceCard(Icons.Rounded.Home, stringResource(R.string.settings_home), home, Modifier.weight(1f)) {
                            if (busy) return@ChoiceCard
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
                    }
                    CardRow {
                        if (BuildConfig.SMART_HOME) {
                            ChoiceCard(
                                Icons.Rounded.Dashboard,
                                stringResource(R.string.settings_widget),
                                stringResource(R.string.settings_widget_count, vm.widgetLimit),
                                Modifier.weight(1f),
                            ) { go(Step.WIDGET) }
                        }
                        ChoiceCard(
                            Icons.Rounded.Lock,
                            stringResource(R.string.settings_lock),
                            if (vm.hasPin) stringResource(R.string.settings_lock_count, vm.lockedApps.size) else stringResource(R.string.settings_lock_off),
                            Modifier.weight(1f),
                        ) { go(Step.LOCK) }
                        ChoiceCard(Icons.Rounded.AutoFixHigh, stringResource(R.string.settings_wizard), null, Modifier.weight(1f), onClick = onWizard)
                        // Yeni sürüm varken "Hakkında" kartı güncellemeye götürür
                        val update = vm.update
                        if (update != null) {
                            ChoiceCard(
                                Icons.Rounded.SystemUpdate,
                                stringResource(R.string.update_available),
                                stringResource(R.string.about_version, update.version),
                                Modifier.weight(1f),
                                tint = EvimColors.Online,
                            ) { go(Step.UPDATE) }
                        } else {
                            ChoiceCard(Icons.Rounded.Info, stringResource(R.string.settings_about), stringResource(R.string.about_version, vm.appVersion), Modifier.weight(1f)) { go(Step.ABOUT) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.heightIn(max = 172.dp).verticalScroll(rememberScrollState())) {
                        ListRow(
                            stringResource(R.string.remote_title),
                            stringResource(if (vm.remoteEnabled) R.string.remote_on else R.string.remote_off),
                            Icons.Rounded.PhoneAndroid,
                            trailing = Trailing.Chevron,
                        ) { go(Step.PHONE) }
                        ListRow(stringResource(R.string.settings_uninstall), stringResource(R.string.settings_uninstall_hint), Icons.Rounded.DeleteForever, EvimColors.Danger, trailing = Trailing.Chevron) {
                            go(Step.UNINSTALL)
                        }
                        if (setupChanged) {
                            ListRow(stringResource(R.string.settings_undo_setup), stringResource(R.string.settings_undo_setup_hint), Icons.Rounded.Restore, trailing = Trailing.Chevron) {
                                go(Step.UNDO_SETUP)
                            }
                        }
                    }
                }
                Step.WIDGET -> {
                    PanelHeader(stringResource(R.string.settings_widget), stringResource(R.string.settings_widget_body), Icons.Rounded.Dashboard)
                    CardRow {
                        WIDGET_LIMITS.forEach { limit ->
                            ChoiceCard(
                                if (limit == vm.widgetLimit) Icons.Rounded.CheckCircle else Icons.Rounded.Dashboard,
                                stringResource(R.string.settings_widget_count, limit),
                                null,
                                Modifier.weight(1f).then(if (limit == vm.widgetLimit) Modifier.focusRequester(first) else Modifier),
                                if (limit == vm.widgetLimit) EvimColors.Online else EvimColors.Accent,
                            ) {
                                vm.changeWidgetLimit(limit)
                                go(Step.MENU)
                            }
                        }
                    }
                }
                Step.LOCK -> {
                    var creating by remember { mutableStateOf(false) }
                    PanelHeader(stringResource(R.string.settings_lock), null, Icons.Rounded.Lock)
                    PanelText(stringResource(R.string.lock_body))
                    PanelActions {
                        if (vm.hasPin) {
                            PillButton(stringResource(R.string.lock_change), Modifier.focusRequester(first)) { creating = true }
                            PillButton(stringResource(R.string.lock_remove), danger = true) { vm.removePin(); go(Step.MENU) }
                        } else {
                            PillButton(stringResource(R.string.lock_set), Modifier.focusRequester(first), primary = true) { creating = true }
                        }
                        PillButton(stringResource(R.string.done)) { go(Step.MENU) }
                    }
                    if (creating) {
                        PinDialog(
                            title = stringResource(R.string.pin_create_title),
                            subtitle = null,
                            create = true,
                            onSuccess = { pin -> vm.setPin(pin); creating = false },
                            onDismiss = { creating = false },
                        )
                    }
                }
                Step.PHONE -> {
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
                        PillButton(stringResource(R.string.done)) { go(Step.MENU) }
                    }
                }
                Step.KEYS -> {
                    PanelHeader(stringResource(R.string.keys_title), stringResource(R.string.keys_body), Icons.Rounded.SettingsRemote)
                    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        vm.keyMappings.entries.sortedBy { it.key }.forEachIndexed { index, (code, target) ->
                            ListRow(
                                keyName(code),
                                targetName(vm, target),
                                trailing = Trailing.Chevron,
                                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            ) {
                                keyCode = code
                                go(Step.KEY_PICK)
                            }
                        }
                        ListRow(
                            stringResource(R.string.keys_add),
                            stringResource(R.string.keys_add_hint),
                            Icons.Rounded.Add,
                            modifier = if (vm.keyMappings.isEmpty()) Modifier.focusRequester(first) else Modifier,
                        ) { go(Step.KEY_LEARN) }
                    }
                    PanelActions { PillButton(stringResource(R.string.done)) { go(Step.MENU) } }
                }
                Step.KEY_LEARN -> {
                    var listening by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        busy = true
                        val failure = vm.startKeyLearning()
                        busy = false
                        if (failure != null) {
                            error = failure
                            return@LaunchedEffect
                        }
                        RemoteKeys.learner = { code ->
                            RemoteKeys.learner = null
                            keyCode = code
                            go(Step.KEY_PICK)
                        }
                        listening = true
                    }
                    // Tuşa basılmadan çıkılırsa servis atanmış tuş yoksa yeniden kapanır
                    DisposableEffect(Unit) { onDispose { if (RemoteKeys.learner != null) vm.stopKeyLearning() } }
                    PanelHeader(stringResource(R.string.keys_add), null, Icons.Rounded.SettingsRemote)
                    PanelText(stringResource(R.string.keys_learn_body))
                    if (listening) PanelNote(stringResource(R.string.keys_listening), EvimColors.Accent)
                    PanelActions { PillButton(stringResource(R.string.cancel), Modifier.focusRequester(first)) { go(Step.KEYS) } }
                }
                Step.KEY_PICK -> {
                    val current = vm.keyMappings[keyCode]
                    // Yeni öğrenilen tuş atanmadan bırakılırsa servis gereksiz açık kalmasın
                    DisposableEffect(Unit) { onDispose { vm.stopKeyLearning() } }
                    PanelHeader(keyName(keyCode), stringResource(R.string.keys_pick_body), Icons.Rounded.SettingsRemote)
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        ListRow(
                            stringResource(R.string.keys_target_home),
                            null,
                            Icons.Rounded.Home,
                            trailing = Trailing.Check(current == KeyTarget.Home),
                            modifier = Modifier.focusRequester(first),
                        ) { vm.assignKey(keyCode, KeyTarget.Home); go(Step.KEYS) }
                        vm.apps.forEach { app ->
                            ListRow(app.label, trailing = Trailing.Check(current == KeyTarget.App(app.packageName))) {
                                vm.assignKey(keyCode, KeyTarget.App(app.packageName))
                                go(Step.KEYS)
                            }
                        }
                        if (current != null) {
                            ListRow(stringResource(R.string.keys_clear), null, Icons.Rounded.DeleteForever, EvimColors.Danger) {
                                vm.assignKey(keyCode, null)
                                go(Step.KEYS)
                            }
                        }
                    }
                }
                Step.ABOUT -> {
                    var checking by remember { mutableStateOf(false) }
                    var upToDate by remember { mutableStateOf(false) }
                    PanelHeader(stringResource(R.string.app_name), stringResource(R.string.about_version, vm.appVersion), Icons.Rounded.Info)
                    PanelText(stringResource(R.string.about_body))
                    PanelText(stringResource(R.string.about_source))
                    when {
                        checking -> PanelNote(stringResource(R.string.update_checking), EvimColors.Accent)
                        upToDate -> PanelNote(stringResource(R.string.update_none), EvimColors.Online)
                    }
                    PanelActions {
                        PillButton(stringResource(R.string.done), Modifier.focusRequester(first)) { go(Step.MENU) }
                        PillButton(stringResource(R.string.update_check)) {
                            if (checking) return@PillButton
                            checking = true
                            upToDate = false
                            scope.launch {
                                val result = vm.checkUpdate()
                                checking = false
                                when {
                                    result.isFailure -> error = R.string.update_network
                                    result.getOrNull() != null -> go(Step.UPDATE)
                                    else -> upToDate = true
                                }
                            }
                        }
                    }
                }
                Step.UPDATE -> {
                    val update = vm.update
                    if (update != null) UpdateStep(vm, update, first, onLater = { go(Step.MENU) }) else LaunchedEffect(Unit) { go(Step.MENU) }
                }
                Step.TO_GENIUS -> Confirm(
                    icon = Icons.Rounded.Home,
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
                    icon = Icons.Rounded.Home,
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
                Step.UNDO_SETUP -> Confirm(
                    icon = Icons.Rounded.Restore,
                    title = stringResource(R.string.undo_setup_title),
                    body = listOf(stringResource(R.string.undo_setup_body)),
                    confirm = stringResource(R.string.undo_setup_confirm),
                    busy = busy,
                    focus = first,
                    onConfirm = {
                        busy = true
                        scope.launch {
                            val failure = vm.undoSetup()
                            busy = false
                            if (failure == null) go(Step.MENU) else error = failure
                        }
                    },
                    onCancel = { go(Step.MENU) },
                )
                Step.UNINSTALL -> {
                    val uninstall = { restore: Boolean ->
                        if (!busy) {
                            busy = true
                            vm.uninstall(restoreSetup = restore) { failure ->
                                busy = false
                                error = failure
                            }
                        }
                    }
                    PanelHeader(stringResource(R.string.uninstall_title), null, Icons.Rounded.DeleteForever, EvimColors.Danger)
                    PanelText(stringResource(R.string.uninstall_body))
                    // Sihirbaz bir şey kaldırdıysa ya da değiştirdiyse onların da geri getirilmesi önerilir
                    if (setupChanged) PanelText(stringResource(R.string.uninstall_restore_body))
                    PanelActions {
                        PillButton(stringResource(R.string.cancel), Modifier.focusRequester(first)) { go(Step.MENU) }
                        if (setupChanged) {
                            PillButton(stringResource(R.string.uninstall_only_confirm)) { uninstall(false) }
                            PillButton(stringResource(R.string.uninstall_restore_confirm), danger = true) { uninstall(true) }
                        } else {
                            PillButton(stringResource(R.string.uninstall_confirm), danger = true) { uninstall(false) }
                        }
                    }
                }
            }
            if (busy) PanelNote(stringResource(R.string.working), EvimColors.Accent)
            error?.let { PanelNote(stringResource(it)) }
        }
    }
}

@Composable
private fun keyName(code: Int): String =
    RemoteKeys.knownName(code)?.let { stringResource(it) } ?: stringResource(R.string.keys_code, RemoteKeys.codeName(code))

@Composable
private fun targetName(vm: LauncherViewModel, target: KeyTarget): String = when (target) {
    KeyTarget.Home -> stringResource(R.string.keys_target_home)
    is KeyTarget.App -> (vm.apps + vm.hiddenApps).firstOrNull { it.packageName == target.packageName }?.label
        ?: target.packageName
}

/** Geri alınması zor işlemler için: odak "Vazgeç"te başlar ki yanlışlıkla OK onaylamasın. */
@Composable
private fun Confirm(
    icon: ImageVector,
    title: String,
    body: List<String>,
    confirm: String,
    busy: Boolean,
    focus: FocusRequester,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    PanelHeader(title, null, icon)
    body.forEach { PanelText(it) }
    PanelActions {
        PillButton(stringResource(R.string.cancel), Modifier.focusRequester(focus), onClick = onCancel)
        PillButton(confirm, primary = true) { if (!busy) onConfirm() }
    }
}

/** Yeni sürümün notları ve "Güncelle". Kurulum başlayınca launcher kapanıp yeni sürümle yeniden açılır. */
@Composable
private fun UpdateStep(vm: LauncherViewModel, update: AppUpdate, first: FocusRequester, onLater: () -> Unit) {
    // null: soruluyor; 0..1: indiriliyor; 1 ve üstü: kuruluyor
    var progress by remember { mutableStateOf<Float?>(null) }
    var installing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    PanelHeader(
        stringResource(R.string.update_title, update.version),
        stringResource(R.string.update_current, vm.appVersion),
        Icons.Rounded.SystemUpdate,
        EvimColors.Online,
    )
    if (update.notes.isNotBlank()) {
        Text(
            update.notes,
            color = EvimColors.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 9,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    }
    when {
        failure != null -> PanelNote(stringResource(failure!!), EvimColors.Danger)
        installing -> PanelNote(stringResource(R.string.update_installing), EvimColors.Accent)
        progress != null -> {
            PanelNote(stringResource(R.string.update_downloading, ((progress ?: 0f) * 100).toInt()), EvimColors.Accent)
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(progress ?: 0f).height(4.dp).background(EvimColors.Online, RoundedCornerShape(50)))
            }
        }
    }
    if (progress == null || failure != null) {
        PanelActions {
            PillButton(stringResource(R.string.update_later), onClick = onLater)
            PillButton(stringResource(R.string.update_now), Modifier.focusRequester(first), primary = true) {
                failure = null
                progress = 0f
                scope.launch {
                    failure = vm.installUpdate(update) { p ->
                        progress = p
                        if (p >= 1f) installing = true
                    }
                    installing = false
                }
            }
        }
    }
}
