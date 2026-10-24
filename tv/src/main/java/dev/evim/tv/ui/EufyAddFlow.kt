package dev.evim.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.text.input.KeyboardType
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.EufyCamera
import kotlinx.coroutines.launch

private sealed interface CameraStep {
    data object Intro : CameraStep
    data object Ip : CameraStep
    data class User(val ip: String) : CameraStep
    data class Password(val ip: String, val user: String) : CameraStep
    data object Checking : CameraStep
    data class Failed(val message: Int) : CameraStep
}

/**
 * Eufy kamera ekleme: kameranın RTSP yayını açılır, IP adresi ile RTSP kullanıcı adı ve parolası yazılır.
 * Kaydetmeden önce kamerayla konuşulup bilgiler denenir. Bilgiler yalnızca bu TV'de, uygulamanın kendi
 * deposunda kalır.
 */
@Composable
internal fun EufyAddFlow(vm: LauncherViewModel, focus: FocusRequester, onDone: () -> Unit) {
    var step by remember { mutableStateOf<CameraStep>(CameraStep.Intro) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(step) { runCatching { focus.requestFocus() } }
    val defaultName = stringResource(R.string.camera_default_name)

    PanelHeader(stringResource(R.string.card_camera_title), stringResource(R.string.brand_eufy), Icons.Rounded.Videocam, EvimColors.Camera)
    when (val current = step) {
        CameraStep.Intro -> {
            PanelText(stringResource(R.string.camera_intro))
            PanelActions { PillButton(stringResource(R.string.tuya_continue), Modifier.focusRequester(focus), primary = true) { step = CameraStep.Ip } }
        }
        CameraStep.Ip -> Field(stringResource(R.string.camera_ip_body), "192.168.", KeyboardType.Decimal, secret = false, focus) { ip ->
            step = CameraStep.User(ip)
        }
        is CameraStep.User -> Field(stringResource(R.string.camera_user_body), "", KeyboardType.Ascii, secret = false, focus) { user ->
            step = CameraStep.Password(current.ip, user)
        }
        is CameraStep.Password -> Field(stringResource(R.string.camera_password_body), "", KeyboardType.Password, secret = true, focus) { password ->
            step = CameraStep.Checking
            scope.launch {
                val result = vm.addCamera(defaultName, current.ip, current.user, password)
                if (result == EufyCamera.Check.OK) {
                    onDone()
                } else {
                    step = CameraStep.Failed(
                        when (result) {
                            EufyCamera.Check.WRONG_LOGIN -> R.string.camera_wrong_login
                            EufyCamera.Check.NO_STREAM -> R.string.camera_no_stream
                            else -> R.string.camera_unreachable
                        },
                    )
                }
            }
        }
        CameraStep.Checking -> {
            PanelNote(stringResource(R.string.camera_checking), EvimColors.Accent)
            PanelActions { PillButton(stringResource(R.string.cancel), Modifier.focusRequester(focus)) { step = CameraStep.Ip } }
        }
        is CameraStep.Failed -> {
            PanelNote(stringResource(current.message), EvimColors.Danger)
            PanelActions { PillButton(stringResource(R.string.tuya_retry), Modifier.focusRequester(focus), primary = true) { step = CameraStep.Ip } }
        }
    }
}
