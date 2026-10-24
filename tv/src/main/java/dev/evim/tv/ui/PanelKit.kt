package dev.evim.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/*
 * Panellerin ortak parçaları ("B" tasarımı): ortalanmış başlık, az seçenekli adımlarda büyük kartlar,
 * uzun listelerde simgeli iki satırlı satırlar ve altta yuvarlak düğmeler. Odaklanan öğe beyaza döner.
 */

/** Panelin üstü: renkli simge dairesi, başlık ve açıklama, ortalı. */
@Composable
internal fun PanelHeader(title: String, subtitle: String? = null, icon: ImageVector? = null, tint: Color = EvimColors.Accent) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = EvimColors.TextDim, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Açıklama paragrafı; ortalı. */
@Composable
internal fun PanelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = EvimColors.TextDim,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

/** Uyarı ya da durum notu: yumuşak renkli kutu. */
@Composable
internal fun PanelNote(text: String, tint: Color = EvimColors.Warning) {
    Text(
        text,
        color = tint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Kartları eşit genişlikte yan yana dizer. */
@Composable
internal fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/** Az seçenekli adımlar için büyük, simgeli kart. [CardRow] içinde `Modifier.weight(1f)` ile kullanılır. */
@Composable
internal fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    tint: Color = EvimColors.Accent,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, EvimColors.ChipBorder), shape = RoundedCornerShape(18.dp))),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x12FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                )
            }
        }
    }
}

/** Satırın sağında ne olduğu. */
internal sealed interface Trailing {
    data object None : Trailing
    data object Chevron : Trailing
    data class Check(val checked: Boolean) : Trailing
    data class Value(val text: String) : Trailing
}

/** Uzun listeler için simgeli, iki satırlı satır. */
@Composable
internal fun ListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = EvimColors.Accent,
    trailing: Trailing = Trailing.None,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0DFFFFFF),
            contentColor = if (enabled) Color.White else EvimColors.TextFaint,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (enabled) tint else EvimColors.TextFaint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.65f),
                    )
                }
            }
            when (trailing) {
                Trailing.None -> Unit
                Trailing.Chevron -> Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp))
                is Trailing.Check -> Icon(
                    if (trailing.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                    null,
                    tint = if (trailing.checked) EvimColors.Online else LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
                is Trailing.Value -> Text(trailing.text, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

/** Alttaki düğmeler; ortalı, yan yana. */
@Composable
internal fun PanelActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/** Yuvarlak düğme; [primary] olan dolgulu, diğeri saydam. Görünümde en fazla bir dolgulu düğme olur. */
@Composable
internal fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val fill = when {
        danger -> EvimColors.Danger
        primary -> EvimColors.Accent
        else -> Color(0x1FFFFFFF)
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill.copy(alpha = if (primary || danger) 0.85f else fill.alpha),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp))
    }
}
