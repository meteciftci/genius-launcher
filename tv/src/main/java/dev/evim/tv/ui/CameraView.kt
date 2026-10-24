package dev.evim.tv.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.evim.tv.R

/**
 * Kameranın canlı görüntüsü, tam ekran. Yayın ev ağından RTSP ile gelir (TCP üzerinden; Wi-Fi'de UDP'den
 * daha az kare kaybeder). Ses kapalı başlar, OK ile açılıp kapanır; Geri kapatır ve oynatıcı bırakılır.
 */
@OptIn(UnstableApi::class)
@Composable
fun CameraDialog(name: String, url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(true) }
    var state by remember { mutableStateOf(Player.STATE_BUFFERING) }
    var failed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(url)))
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state = playbackState
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    // Kartı açan OK'in bırakılması sesi açmasın: önce bu ekranda basılmış olmalı
                    if (ok && event.type == KeyEventType.KeyDown) armed = true
                    if (ok && event.type == KeyEventType.KeyUp && armed) {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                        true
                    } else {
                        ok
                    }
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Sol üstte ad ve "canlı" işareti, sağ üstte ses durumu
            Row(
                Modifier.align(Alignment.TopStart).padding(28.dp).background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).background(if (state == Player.STATE_READY) EvimColors.Danger else EvimColors.TextFaint, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.camera_live), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(name, color = EvimColors.TextDim, fontSize = 12.sp)
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(28.dp).background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (muted) R.string.camera_unmute_hint else R.string.camera_mute_hint), color = Color.White, fontSize = 12.sp)
            }
            when {
                failed -> CenterNote(stringResource(R.string.camera_stream_failed))
                state != Player.STATE_READY -> CenterNote(stringResource(R.string.camera_connecting))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CenterNote(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
