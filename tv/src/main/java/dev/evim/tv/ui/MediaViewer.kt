package dev.evim.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.evim.tv.R
import dev.evim.tv.data.FileKind
import dev.evim.tv.data.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video ve müzik, tam ekran. TV'de oynatıcı uygulaması olmadığı için dosyalar launcher'ın içinde
 * Media3 ile açılır. Videoda kumanda kontrolleri Media3'ün kendi düğmeleri; müzikte görüntü olmadığı için
 * sade bir ekran: OK duraklatır, sol/sağ 10 saniye sarar.
 */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audio = remember(file) { FileStore.kindOf(file) == FileKind.AUDIO }
    var failed by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (audio) {
                AudioScreen(file, player)
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            keepScreenOn = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            this.player = player
                            isFocusable = true
                            post { requestFocus() }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (failed) {
                Text(
                    stringResource(R.string.files_play_failed),
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioScreen(file: File, player: ExoPlayer) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.playWhenReady
            delay(500)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        player.playWhenReady = !player.playWhenReady
                        playing = player.playWhenReady
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> { player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0)); true }
                    Key.DirectionRight, Key.MediaFastForward -> { player.seekTo(player.currentPosition + SEEK_MS); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp).background(EvimColors.Tuya.copy(alpha = 0.18f), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Audiotrack else Icons.Rounded.Pause, null, tint = EvimColors.Tuya, modifier = Modifier.size(56.dp))
            }
            Text(
                file.nameWithoutExtension,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 20.dp, bottom = 22.dp),
            )
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(EvimColors.Tuya, RoundedCornerShape(50)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(clock(position), color = EvimColors.TextDim, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(clock(duration), color = EvimColors.TextDim, fontSize = 12.sp)
            }
            Text(stringResource(R.string.files_audio_hint), color = EvimColors.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 26.dp))
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val SEEK_MS = 10_000L

/** Resimler, tam ekran; sol/sağ aynı klasördeki önceki/sonraki resme geçer. */
@Composable
fun ImageViewerDialog(images: List<File>, start: Int, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(start) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(index) {
        failed = false
        val decoded = withContext(Dispatchers.IO) { runCatching { decode(images[index]) }.getOrNull() }
        bitmap = decoded
        failed = decoded == null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { if (index > 0) index--; true }
                        Key.DirectionRight -> { if (index < images.lastIndex) index++; true }
                        else -> false
                    }
                },
        ) {
            bitmap?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            if (failed) {
                Text(
                    stringResource(R.string.files_cannot_open),
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Text(
                "${images[index].name}   ${index + 1} / ${images.size}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    .background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Ekrandan büyük resimler okunurken küçültülür; EXIF yönü ImageDecoder tarafından uygulanır. */
private fun decode(file: File): ImageBitmap {
    // Android 9'un ImageDecoder'ı döndürülmüş büyük JPEG'leri küçültürken "invalid scale" hatası veriyor
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return decodeLegacy(file)
    val source = ImageDecoder.createSource(file)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = minOf(1f, MAX_W / info.size.width, MAX_H / info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }.asImageBitmap()
}

/** Android 10 öncesi: ikinin katlarıyla küçültülerek okunur, EXIF yönü elle uygulanır. */
private fun decodeLegacy(file: File): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_W || bounds.outHeight / (sample * 2) >= MAX_H) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("'${file.name}' okunamadı")
    val orientation = runCatching {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return bitmap.asImageBitmap()
    }
    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented !== bitmap) bitmap.recycle()
    return oriented.asImageBitmap()
}

private const val MAX_W = 1920f
private const val MAX_H = 1080f
