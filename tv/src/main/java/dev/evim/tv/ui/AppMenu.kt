package dev.evim.tv.ui

import dev.evim.tv.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.unit.Dp
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
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onColor: (Color?) -> Unit,
    onInfo: () -> Unit,
) {
    var pickingColor by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(pickingColor) { runCatching { first.requestFocus() } }

    Dialog(onDismissRequest = { if (pickingColor) pickingColor = false else onDismiss() }) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, null, Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.width(10.dp))
                Text(app.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            if (!pickingColor) {
                ListRow(stringResource(R.string.menu_open), icon = Icons.Rounded.OpenInNew, modifier = Modifier.focusRequester(first), onClick = onOpen)
                ListRow(stringResource(R.string.menu_move), icon = Icons.Rounded.SwapHoriz, onClick = onMove)
                ListRow(stringResource(R.string.menu_pick_color), icon = Icons.Rounded.Palette, onClick = { pickingColor = true })
                ListRow(stringResource(R.string.menu_hide), icon = Icons.Rounded.VisibilityOff, onClick = onHide)
                ListRow(stringResource(R.string.menu_app_info), icon = Icons.Rounded.Info, onClick = onInfo)
            } else {
                Text(stringResource(R.string.color_title), color = EvimColors.TextDim, fontSize = 11.sp)
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
                MenuItem(stringResource(R.string.color_auto), onClick = { onColor(null) })
                MenuItem(stringResource(R.string.done), onClick = onDismiss)
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
            PanelHeader(stringResource(R.string.hidden_apps_title), stringResource(R.string.hidden_apps_subtitle), Icons.Rounded.VisibilityOff)
            if (apps.isEmpty()) {
                MenuItem(stringResource(R.string.hidden_apps_empty), Modifier.focusRequester(first), onDismiss)
            }
            apps.forEachIndexed { i, app ->
                MenuItem(
                    stringResource(R.string.hidden_apps_show, app.label),
                    if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { onUnhide(app) }
            }
        }
    }
}

@Composable
internal fun Panel(width: Dp = 320.dp, content: @Composable () -> Unit) {
    // Paneli açan OK basışının bırakılması panelde tıklama sayılmasın.
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
            .width(width)
            .background(Color(0xF717171E), RoundedCornerShape(26.dp))
            .border(1.dp, EvimColors.ChipBorder, RoundedCornerShape(26.dp))
            .padding(horizontal = 26.dp, vertical = 18.dp),
    ) { content() }
}

@Composable
internal fun MenuItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0DFFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
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
