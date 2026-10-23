package dev.evim.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import android.graphics.Bitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.data.LauncherApp

private val CardShape = RoundedCornerShape(12.dp)

/** Merkezden kenara beyazdan saydama giden küçük oval; kart rengiyle boyanıp kullanılır. */
private val HaloMask: ImageBitmap by lazy {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.RadialGradient(
            size / 2f, size / 2f, size / 2f,
            android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    android.graphics.Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    bitmap.asImageBitmap()
}

@Composable
fun AppCard(
    app: LauncherApp,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (moving) EvimColors.Warning else Color.White
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .size(width = 122.dp, height = 75.dp)
            // Bulanık gölge (Glow) ve canlı degrade Mi Box'ta pahalı; hazır bir hale resmi boyanıp geriliyor
            .drawWithCache {
                val tint = ColorFilter.tint(app.color.copy(alpha = 0.55f), BlendMode.Modulate)
                val offset = IntOffset((-size.width * 0.35f).toInt(), (-size.height * 0.25f).toInt())
                val dst = IntSize((size.width * 1.7f).toInt(), (size.height * 1.7f).toInt())
                onDrawBehind {
                    if (isFocused) drawImage(HaloMask, dstOffset = offset, dstSize = dst, colorFilter = tint, filterQuality = FilterQuality.Low)
                }
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                // Taşıma modunda sol/sağ odak değiştirmez, kartı kaydırır
                if (!moving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onMove(-1); true }
                    Key.DirectionRight -> { onMove(1); true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.5.dp, ring), inset = 0.dp, shape = CardShape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Kart içeriği dokuya bir kez çizilir; büyüme animasyonu bu dokuyu ölçekler
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .background(Brush.linearGradient(listOf(app.color.lighter(0.18f), app.color.darker(0.3f)))),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            Text(
                text = if (moving) "◀  ${app.label}  ▶" else app.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
            )
        }
    }
}
