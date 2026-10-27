package dev.evim.tv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.R
import dev.evim.tv.data.PinCheck
import dev.evim.tv.data.PinLock

/**
 * 4 haneli PIN girişi. Çoğu TV kumandasında rakam tuşu olmadığından ekrandaki tuş takımı yön tuşlarıyla
 * kullanılır; rakam tuşu olan kumandalarda doğrudan yazılır. Dördüncü rakamda kendiliğinden denetlenir.
 * [create] ise yeni PIN iki kez istenir ve [check] kullanılmaz.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String?,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit,
    create: Boolean = false,
    check: (String) -> PinCheck = { PinCheck.OK },
) {
    var entered by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun submit(pin: String) {
        entered = ""
        if (create) {
            val previous = firstEntry
            when (previous) {
                null -> { firstEntry = pin; message = null }
                pin -> onSuccess(pin)
                else -> { firstEntry = null; message = R.string.pin_mismatch }
            }
            return
        }
        when (check(pin)) {
            PinCheck.OK -> onSuccess(pin)
            PinCheck.WRONG -> message = R.string.pin_wrong
            PinCheck.BLOCKED -> message = R.string.pin_blocked
        }
    }

    fun type(digit: Char) {
        if (entered.length >= PinLock.LENGTH) return
        entered += digit
        if (entered.length == PinLock.LENGTH) submit(entered)
    }

    val hint = when {
        !create -> subtitle
        firstEntry == null -> stringResource(R.string.pin_create_first)
        else -> stringResource(R.string.pin_create_confirm)
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel(width = 300.dp) {
            Column(
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (val code = event.nativeKeyEvent.keyCode) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { type('0' + (code - KeyEvent.KEYCODE_0)); true }
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> { type('0' + (code - KeyEvent.KEYCODE_NUMPAD_0)); true }
                        KeyEvent.KEYCODE_DEL -> { entered = entered.dropLast(1); true }
                        else -> false
                    }
                },
            ) {
                PanelHeader(title, hint, Icons.Rounded.Lock)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                    repeat(PinLock.LENGTH) { i ->
                        Box(Modifier.size(14.dp).background(if (i < entered.length) Color.White else Color(0x33FFFFFF), CircleShape))
                    }
                }
                Spacer(Modifier.height(12.dp))
                message?.let { PanelNote(stringResource(it)) }
                Spacer(Modifier.height(4.dp))
                listOf("123", "456", "789").forEachIndexed { row, digits ->
                    KeyRow {
                        digits.forEachIndexed { col, digit ->
                            PinKey(Modifier.then(if (row == 0 && col == 0) Modifier.focusRequester(first) else Modifier), onClick = { type(digit) }) {
                                Text(digit.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                KeyRow {
                    PinKey(onClick = { entered = entered.dropLast(1) }) { KeyIcon(Icons.Rounded.Backspace) }
                    PinKey(onClick = { type('0') }) { Text("0", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    PinKey(onClick = onDismiss) { KeyIcon(Icons.Rounded.Close) }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PinKey(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 64.dp, height = 44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Box(Modifier.size(width = 64.dp, height = 44.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun KeyIcon(icon: ImageVector) = Icon(icon, null, Modifier.size(20.dp))
