package dev.evim.tv.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.DysonAccountDevice
import dev.evim.tv.data.home.DysonChallenge
import dev.evim.tv.data.home.DysonCloudException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Wifi

private sealed interface DysonStep {
    data object Choice : DysonStep
    data object Email : DysonStep
    data class Code(val challenge: DysonChallenge) : DysonStep
    data class Password(val challenge: DysonChallenge, val code: String) : DysonStep
    data object Ssid : DysonStep
    data class StickerPassword(val ssid: String) : DysonStep
    data object Loading : DysonStep
    data class Select(val devices: List<DysonAccountDevice>, val ips: Map<String, String>) : DysonStep
    data class Failed(val message: Int) : DysonStep
}

/**
 * Dyson ekleme ve yeniden bağlanma: MyDyson hesabıyla (e-postaya gelen kod ve şifre) ya da cihazın
 * Wi-Fi etiketinden. Şifre yalnızca giriş isteğinde kullanılır; şifre de jeton da saklanmaz.
 */
@Composable
internal fun DysonAddFlow(vm: LauncherViewModel, focus: FocusRequester, onDone: () -> Unit) {
    var step by remember { mutableStateOf<DysonStep>(DysonStep.Choice) }
    var email by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(step) { runCatching { focus.requestFocus() } }

    fun run(block: suspend () -> DysonStep) {
        job = scope.launch {
            step = DysonStep.Loading
            step = runCatching { block() }.getOrElse { error ->
                if (error is CancellationException) throw error
                DysonStep.Failed(
                    when ((error as? DysonCloudException)?.reason) {
                        DysonCloudException.Reason.NO_ACCOUNT -> R.string.dyson_no_account
                        DysonCloudException.Reason.TOO_FREQUENT -> R.string.dyson_too_frequent
                        DysonCloudException.Reason.WRONG_LOGIN -> R.string.dyson_wrong_login
                        else -> R.string.dyson_network
                    },
                )
            }
        }
    }

    PanelHeader(
        stringResource(R.string.card_dyson_title),
        if (step == DysonStep.Choice) stringResource(R.string.dyson_choice_body) else stringResource(R.string.brand_dyson),
        Icons.Rounded.Air,
        EvimColors.Dyson,
    )
    when (val current = step) {
        DysonStep.Choice -> CardRow {
            ChoiceCard(
                Icons.Rounded.AccountCircle,
                stringResource(R.string.dyson_with_account),
                stringResource(R.string.dyson_with_account_hint),
                Modifier.weight(1f).focusRequester(focus),
                EvimColors.Dyson,
            ) { step = DysonStep.Email }
            ChoiceCard(
                Icons.Rounded.Wifi,
                stringResource(R.string.dyson_with_sticker),
                stringResource(R.string.dyson_with_sticker_hint),
                Modifier.weight(1f),
                EvimColors.Dyson,
            ) { step = DysonStep.Ssid }
        }
        DysonStep.Email -> Field(stringResource(R.string.dyson_email_body), email, KeyboardType.Email, secret = false, focus) { value ->
            email = value
            // Hesabın bölgesi TV'nin bölgesinden; Dyson e-posta girişinde ülke kodunu da istiyor
            val country = Locale.getDefault().country.ifEmpty { "TR" }
            run { DysonStep.Code(vm.dysonRequestCode(value, country)) }
        }
        is DysonStep.Code -> Field(stringResource(R.string.dyson_code_body, email), "", KeyboardType.Number, secret = false, focus) { code ->
            step = DysonStep.Password(current.challenge, code)
        }
        is DysonStep.Password -> Field(stringResource(R.string.dyson_password_body), "", KeyboardType.Password, secret = true, focus) { password ->
            run {
                val (devices, ips) = vm.dysonVerify(current.challenge, current.code, password)
                DysonStep.Select(devices, ips)
            }
        }
        DysonStep.Ssid -> Field(stringResource(R.string.dyson_ssid_body), "DYSON-", KeyboardType.Ascii, secret = false, focus) { ssid ->
            step = DysonStep.StickerPassword(ssid)
        }
        is DysonStep.StickerPassword -> Field(stringResource(R.string.dyson_sticker_password_body), "", KeyboardType.Password, secret = true, focus) { password ->
            run {
                val (devices, ips) = vm.dysonFromSticker(current.ssid, password) ?: return@run DysonStep.Failed(R.string.dyson_bad_ssid)
                DysonStep.Select(devices, ips)
            }
        }
        DysonStep.Loading -> {
            PanelNote(stringResource(R.string.dyson_loading), EvimColors.Accent)
            PanelActions {
                PillButton(stringResource(R.string.cancel), Modifier.focusRequester(focus)) {
                    job?.cancel()
                    step = DysonStep.Choice
                }
            }
        }
        is DysonStep.Select -> SelectDevices(vm, current, focus, onDone)
        is DysonStep.Failed -> {
            PanelNote(stringResource(current.message), EvimColors.Danger)
            PanelActions { PillButton(stringResource(R.string.tuya_retry), Modifier.focusRequester(focus), primary = true) { step = DysonStep.Choice } }
        }
    }
}

/** Tek alanlı adım: açıklama, yazı alanı ve "Devam". Her adımda alan sıfırdan başlar. */
@Composable
internal fun Field(body: String, initial: String, type: KeyboardType, secret: Boolean, focus: FocusRequester, onNext: (String) -> Unit) {
    key(body) {
        var value by remember { mutableStateOf(TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
        val keyboard = LocalSoftwareKeyboardController.current
        PanelText(body)
        TextInput(value, { value = it }, Modifier.focusRequester(focus), keyboardType = type, secret = secret)
        PanelActions {
            PillButton(stringResource(R.string.tuya_continue), primary = true) {
                keyboard?.hide()
                if (value.text.isNotBlank()) onNext(value.text.trim())
            }
        }
    }
}

@Composable
private fun SelectDevices(vm: LauncherViewModel, select: DysonStep.Select, focus: FocusRequester, onDone: () -> Unit) {
    val added = remember { vm.dysonAddedSerials }
    var checked by remember { mutableStateOf(select.devices.map { it.serial }.toSet()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (select.devices.isEmpty()) {
        PanelNote(stringResource(R.string.dyson_no_devices))
        PanelActions { PillButton(stringResource(R.string.done), Modifier.focusRequester(focus), onClick = onDone) }
        return
    }
    PanelText(stringResource(R.string.dyson_select_body))
    select.devices.forEachIndexed { index, device ->
        val notes = listOfNotNull(
            if (device.serial !in select.ips) stringResource(R.string.tuya_not_on_lan) else null,
            if (device.serial in added) stringResource(R.string.tuya_added) else null,
        ).joinToString("  ·  ")
        ListRow(
            device.name,
            notes.ifEmpty { device.serial },
            Icons.Rounded.Air,
            EvimColors.Dyson,
            trailing = Trailing.Check(device.serial in checked),
            modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
        ) {
            checked = if (device.serial in checked) checked - device.serial else checked + device.serial
        }
    }
    PanelActions {
        PillButton(stringResource(R.string.tuya_add_selected, checked.size), primary = true) {
            if (saving || checked.isEmpty()) return@PillButton
            saving = true
            scope.launch {
                vm.addDyson(select.devices.filter { it.serial in checked }, select.ips)
                onDone()
            }
        }
    }
    if (saving) PanelNote(stringResource(R.string.working), EvimColors.Accent)
}
