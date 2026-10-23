package dev.evim.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.data.LauncherApp

/** OK basılı tutulunca açılan kart menüsü. */
@Composable
fun AppMenu(
    app: LauncherApp,
    hiddenCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onColor: (Color?) -> Unit,
    onInfo: () -> Unit,
    onShowHidden: () -> Unit,
) {
    var pickingColor by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(pickingColor) { runCatching { first.requestFocus() } }

    Dialog(onDismissRequest = { if (pickingColor) pickingColor = false else onDismiss() }) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, null, Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.width(10.dp))
                Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            if (!pickingColor) {
                MenuItem("Aç", Modifier.focusRequester(first), onOpen)
                MenuItem("Taşı", onClick = onMove)
                MenuItem("Renk seç", onClick = { pickingColor = true })
                MenuItem("Gizle", onClick = onHide)
                MenuItem("Uygulama bilgisi", onClick = onInfo)
                if (hiddenCount > 0) MenuItem("Gizli uygulamalar ($hiddenCount)", onClick = onShowHidden)
            } else {
                Text("Kart ve vitrin rengi", color = EvimColors.TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvimColors.Picks.forEachIndexed { i, color ->
                        ColorDot(
                            color = color,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { onColor(color) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                MenuItem("Otomatik renk (simgeden)", onClick = { onColor(null) })
                MenuItem("Tamam", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun HiddenAppsDialog(
    apps: List<LauncherApp>,
    onUnhide: (LauncherApp) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.size) { runCatching { first.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            Text("Gizli uygulamalar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Seçtiğiniz uygulama listeye geri döner", color = EvimColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.height(14.dp))
            if (apps.isEmpty()) {
                MenuItem("Gizli uygulama yok", Modifier.focusRequester(first), onDismiss)
            }
            apps.forEachIndexed { i, app ->
                MenuItem(
                    "Göster: ${app.label}",
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { onUnhide(app) }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    // Menü OK'a basılı tutulurken açılır; o basışın bırakılması menüde tıklama sayılmasın.
    // Yeni bir tuşa basılana kadar OK/Enter olaylarını yut.
    var armed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .onPreviewKeyEvent { event ->
                if (armed) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown && !event.nativeKeyEvent.isLongPress && event.nativeKeyEvent.repeatCount == 0) {
                    armed = true
                    false
                } else {
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                }
            }
            .width(320.dp)
            .background(Color(0xF2141419), RoundedCornerShape(18.dp))
            .border(1.dp, EvimColors.ChipBorder, RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun MenuItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = EvimColors.TextDim,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
    }
}

@Composable
private fun ColorDot(color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(24.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape),
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
    ) {}
}
