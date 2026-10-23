package dev.evim.tv.ui

import androidx.annotation.StringRes
import dev.evim.tv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private data class HomeDevice(val symbol: String, @StringRes val name: Int)

// Anahtarlar alınınca gerçek durum bilgisiyle değişecek
private val Devices = listOf(
    HomeDevice("❋", R.string.device_dyson),
    HomeDevice("◍", R.string.device_feeder_1),
    HomeDevice("◍", R.string.device_feeder_2),
    HomeDevice("▣", R.string.device_litter_box),
    HomeDevice("◐", R.string.device_relay),
    HomeDevice("◉", R.string.device_camera),
)

@Composable
fun HomeDevicesRow() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.home_title), color = EvimColors.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Devices.forEach { DeviceChip(it) }
        }
    }
}

@Composable
private fun DeviceChip(device: HomeDevice) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(1.5.dp, Color.White), shape = shape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EvimColors.Chip,
            contentColor = Color.White,
            focusedContainerColor = Color(0x33FFFFFF),
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier.size(18.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(device.symbol, fontSize = 9.sp)
            }
            Spacer(Modifier.width(7.dp))
            Text(stringResource(device.name), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(4.dp).background(EvimColors.TextFaint, CircleShape))
        }
    }
}
