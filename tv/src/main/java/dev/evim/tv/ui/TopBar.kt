package dev.evim.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TR = Locale("tr", "TR")

@Composable
fun TopBar(vm: LauncherViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
            vm.refreshRam()
        }
    }

    // Önbellekli katman: cam kutucuklar yalnızca saat/RAM değişince yeniden çizilir
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        Chip {
            Text(SimpleDateFormat("HH:mm", TR).format(Date(now)), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("EEE d MMM", TR).format(Date(now)), color = EvimColors.TextDim, fontSize = 11.sp)
            Divider()
            val weather = vm.weather
            Text(
                if (weather != null) "${weather.symbol}  ${weather.temperature}°  ·  ${vm.cityName}" else vm.cityName,
                color = EvimColors.TextDim,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Chip {
            Text("⌂", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text("Evim", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  kurulum bekliyor", color = EvimColors.TextDim, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(5.dp).background(EvimColors.Warning, CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        RamChip(vm = vm)
    }
}

@Composable
private fun RamChip(vm: LauncherViewModel) {
    val used = vm.ram.usedFraction
    Chip(endPadding = 4.dp) {
        Text("RAM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("  %${(used * 100).toInt()}", color = EvimColors.TextDim, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(54.dp)
                .height(3.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(used.coerceIn(0f, 1f))
                    .background(EvimColors.Warning, RoundedCornerShape(50)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = vm::freeRam,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = EvimColors.Warning.copy(alpha = 0.18f),
                contentColor = EvimColors.Warning,
                focusedContainerColor = EvimColors.Warning,
                focusedContentColor = Color(0xFF111111),
            ),
        ) {
            Text(
                "⚡ Boşalt",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Chip(
    endPadding: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            // Arkadaki renk sürekli değiştiği için koyu, sade bir zemin: yazı her renkte okunur
            .background(EvimColors.ChipSolid, RoundedCornerShape(50))
            .border(1.dp, EvimColors.ChipBorder, RoundedCornerShape(50))
            .padding(start = 14.dp, end = endPadding),
        content = content,
    )
}

@Composable
private fun Divider() {
    Spacer(Modifier.width(10.dp))
    Box(Modifier.width(1.dp).height(14.dp).background(EvimColors.ChipBorder))
    Spacer(Modifier.width(10.dp))
}
