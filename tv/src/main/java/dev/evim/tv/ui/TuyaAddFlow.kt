package dev.evim.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Outlet
import androidx.compose.material.icons.rounded.Pets
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.TuyaAccountDevice
import dev.evim.tv.data.home.TuyaKind
import dev.evim.tv.data.home.TuyaLanDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface TuyaStep {
    data object Code : TuyaStep
    data class Qr(val content: String) : TuyaStep
    data object Loading : TuyaStep
    data class Select(val devices: List<TuyaAccountDevice>, val lan: Map<String, TuyaLanDevice>) : TuyaStep
    data class Failed(val message: String?) : TuyaStep
}

/**
 * Tuya / Smart Life hesabıyla ekleme ve yeniden bağlanma: kullanıcı kodu → uygulamada QR okutma →
 * hesaptaki cihazlardan seçim. Ev ağında bulunanlar ve zaten eklenmiş olanlar seçili gelir.
 * Oturum yalnızca bu akış boyunca bellekte kalır; şifre ya da jeton saklanmaz.
 */
@Composable
internal fun TuyaAddFlow(vm: LauncherViewModel, focus: FocusRequester, onDone: () -> Unit) {
    var step by remember { mutableStateOf<TuyaStep>(TuyaStep.Code) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(step::class) { runCatching { focus.requestFocus() } }

    fun cancel() {
        job?.cancel()
        step = TuyaStep.Code
    }

    fun start(userCode: String) {
        job = scope.launch {
            step = TuyaStep.Loading
            step = runCatching {
                val (qr, token) = vm.tuyaStartLogin(userCode)
                step = TuyaStep.Qr(qr)
                val session = vm.tuyaAwaitLogin(token, userCode) ?: return@runCatching TuyaStep.Failed(null)
                step = TuyaStep.Loading
                val (devices, lan) = vm.tuyaCandidates(session)
                TuyaStep.Select(devices.filter { it.localKey.isNotEmpty() }, lan)
            }.getOrElse { if (it is CancellationException) throw it else TuyaStep.Failed(it.message) }
        }
    }

    val header = @Composable { subtitle: String? -> PanelHeader(stringResource(R.string.brand_tuya), subtitle, Icons.Rounded.Outlet, EvimColors.Tuya) }
    if (!vm.tuyaAvailable) {
        header(null)
        PanelNote(stringResource(R.string.tuya_unavailable))
        PanelActions { PillButton(stringResource(R.string.done), Modifier.focusRequester(focus), onClick = onDone) }
        return
    }
    when (val current = step) {
        TuyaStep.Code -> {
            var code by remember { mutableStateOf(vm.tuyaUserCode.let { TextFieldValue(it, TextRange(it.length)) }) }
            val keyboard = LocalSoftwareKeyboardController.current
            header(stringResource(R.string.tuya_code_title))
            PanelText(stringResource(R.string.tuya_code_body))
            TextInput(code, { code = it }, Modifier.focusRequester(focus))
            PanelActions {
                PillButton(stringResource(R.string.tuya_continue), primary = true) {
                    keyboard?.hide()
                    if (code.text.isNotBlank()) start(code.text.trim())
                }
            }
        }
        is TuyaStep.Qr -> {
            header(stringResource(R.string.tuya_qr_title))
            PanelText(stringResource(R.string.tuya_qr_body))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(current.content) }
            Spacer(Modifier.height(12.dp))
            PanelText(stringResource(R.string.tuya_waiting))
            PanelActions { PillButton(stringResource(R.string.cancel), Modifier.focusRequester(focus), onClick = ::cancel) }
        }
        TuyaStep.Loading -> {
            header(null)
            PanelNote(stringResource(R.string.tuya_loading), EvimColors.Accent)
            // Odak bir yerde dursun diye; Geri ile akış bırakılabilir
            PanelActions { PillButton(stringResource(R.string.cancel), Modifier.focusRequester(focus), onClick = ::cancel) }
        }
        is TuyaStep.Select -> {
            header(stringResource(R.string.tuya_select_body))
            SelectDevices(vm, current, focus, onDone)
        }
        is TuyaStep.Failed -> {
            header(null)
            PanelNote(current.message?.let { stringResource(R.string.tuya_failed, it) } ?: stringResource(R.string.tuya_timeout), EvimColors.Danger)
            PanelActions { PillButton(stringResource(R.string.tuya_retry), Modifier.focusRequester(focus), primary = true, onClick = ::cancel) }
        }
    }
}

@Composable
private fun SelectDevices(vm: LauncherViewModel, select: TuyaStep.Select, focus: FocusRequester, onDone: () -> Unit) {
    val added = remember { vm.tuyaAddedIds }
    var checked by remember { mutableStateOf(select.devices.filter { it.id in select.lan || it.id in added }.map { it.id }.toSet()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (select.devices.isEmpty()) {
        PanelNote(stringResource(R.string.tuya_no_devices))
        PanelActions { PillButton(stringResource(R.string.done), Modifier.focusRequester(focus), onClick = onDone) }
        return
    }
    Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
        select.devices.forEachIndexed { index, device ->
            val notes = listOfNotNull(
                stringResource(device.kind.label),
                if (device.id !in select.lan) stringResource(R.string.tuya_not_on_lan) else null,
                if (device.id in added) stringResource(R.string.tuya_added) else null,
            ).joinToString("  ·  ")
            ListRow(
                device.name,
                notes,
                if (device.kind == TuyaKind.FEEDER) Icons.Rounded.Pets else Icons.Rounded.Outlet,
                if (device.kind == TuyaKind.FEEDER) EvimColors.Feeder else EvimColors.Tuya,
                trailing = Trailing.Check(device.id in checked),
                modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
            ) {
                checked = if (device.id in checked) checked - device.id else checked + device.id
            }
        }
    }
    PanelActions {
        PillButton(stringResource(R.string.tuya_add_selected, checked.size), primary = true) {
        if (saving || checked.isEmpty()) return@PillButton
        saving = true
        scope.launch {
            vm.addTuya(select.devices.filter { it.id in checked }, select.lan)
            onDone()
        }
        }
    }
    if (saving) PanelNote(stringResource(R.string.working), EvimColors.Accent)
}

private val TuyaKind.label: Int
    get() = when (this) {
        TuyaKind.RELAY, TuyaKind.FEEDER -> R.string.tuya_full
        TuyaKind.SWITCH -> R.string.tuya_switch
        TuyaKind.STATUS -> R.string.tuya_status
    }

/** Kumandayla değil telefonla okutulacağı için büyük ve beyaz zeminli. */
@Composable
private fun QrCode(content: String) {
    val matrix: BitMatrix = remember(content) { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) }
    Canvas(
        Modifier
            .size(200.dp)
            .background(Color.White, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
