package dev.evim.tv.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.evim.tv.R
import dev.evim.tv.data.FileKind
import dev.evim.tv.data.FileStore
import dev.evim.tv.data.Incoming
import dev.evim.tv.data.ReceiveServer
import java.io.File

/**
 * "Dosya al": telefon QR kodu okutup açılan sayfadan dosya seçer, dosyalar [folder]'a gelir. Sunucu yalnızca
 * bu ekran açıkken çalışır. Gelen dosyanın yanındaki düğme onu açar ya da (APK ise) kurar.
 */
@Composable
fun ReceiveDialog(folder: File, onOpen: (File) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<Incoming>>(emptyList()) }
    val server = remember { runCatching { ReceiveServer(context, folder) { items = it } }.getOrNull() }
    DisposableEffect(server) { onDispose { server?.close() } }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 720.dp) {
            PanelHeader(stringResource(R.string.receive_title), stringResource(R.string.receive_subtitle), Icons.Rounded.PhoneAndroid)
            val url = server?.url
            if (url == null) {
                PanelNote(stringResource(R.string.receive_no_network), EvimColors.Danger)
            } else {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    QrCode(url, side = 170.dp)
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Step(1, stringResource(R.string.receive_step_scan))
                        Step(2, stringResource(R.string.receive_step_pick))
                        Step(3, stringResource(R.string.receive_step_done))
                        Text(stringResource(R.string.receive_same_wifi), color = EvimColors.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        Text(url, color = EvimColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            // En yeni dosya üstte; panel ekrandan taşmasın diye son ikisi, gerisi İndirilenler'de
            items.takeLast(MAX_ROWS).reversed().forEach { item -> IncomingRow(item, onOpen) }
            if (items.size > MAX_ROWS) {
                Text(
                    stringResource(R.string.receive_more, items.size - MAX_ROWS),
                    color = EvimColors.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            PanelActions { PillButton(stringResource(R.string.done), Modifier.focusRequester(close), onClick = onDismiss) }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).background(EvimColors.Accent.copy(alpha = 0.2f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
            Text("$number", color = EvimColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun IncomingRow(item: Incoming, onOpen: (File) -> Unit) {
    val context = LocalContext.current
    val file = item.file
    val subtitle = when (item.state) {
        Incoming.State.RECEIVING -> stringResource(
            R.string.receive_progress,
            Formatter.formatShortFileSize(context, item.received),
            Formatter.formatShortFileSize(context, item.total),
        )
        Incoming.State.DONE -> Formatter.formatShortFileSize(context, item.total)
        Incoming.State.FAILED -> stringResource(R.string.receive_failed)
        Incoming.State.NO_SPACE -> stringResource(R.string.receive_no_space)
    }
    val tint = when (item.state) {
        Incoming.State.DONE -> EvimColors.Online
        Incoming.State.RECEIVING -> EvimColors.Accent
        else -> EvimColors.Danger
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).background(Color(0x0DFFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = tint, fontSize = 11.sp)
            }
            if (file != null) {
                val apk = FileStore.kindOf(file) == FileKind.APK
                PillButton(stringResource(if (apk) R.string.files_install else R.string.menu_open)) { onOpen(file) }
            }
        }
        if (item.state == Incoming.State.RECEIVING) {
            val fraction = if (item.total > 0) (item.received.toFloat() / item.total).coerceIn(0f, 1f) else 0f
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(EvimColors.Accent, RoundedCornerShape(50)))
            }
        }
    }
}

private const val MAX_ROWS = 2
