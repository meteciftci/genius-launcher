package dev.evim.tv.ui

import dev.evim.tv.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import android.graphics.Bitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.data.LauncherApp
import java.util.Calendar

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    var focused by remember { mutableStateOf<LauncherApp?>(null) }
    var menuFor by remember { mutableStateOf<LauncherApp?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var showWeather by remember { mutableStateOf(false) }
    var pickingCity by remember { mutableStateOf(false) }
    val firstCard = remember { FocusRequester() }

    // Odaktaki uygulama listede değiştiyse (renk, sıra) güncel halini kullan
    val current = focused?.let { f -> vm.apps.firstOrNull { it.packageName == f.packageName } } ?: vm.apps.firstOrNull()
    // Animasyonlu renk yalnızca çizim aşamasında okunur; her karede ekran yeniden kurulmaz
    val accentState = animateColorAsState(current?.color ?: EvimColors.Picks.last(), tween(500), label = "accent")
    val accent: () -> Color = { accentState.value }

    LaunchedEffect(vm.apps.isNotEmpty()) {
        if (vm.apps.isNotEmpty()) runCatching { firstCard.requestFocus() }
    }

    // Ana ekranda geri tuşu hiçbir yere gitmez; taşıma modundaysa modu bitirir
    BackHandler { movingPackage = null }

    // Zemin rengini pencere teması çiziyor (themes.xml); burada ikinci kez boyamıyoruz
    Box(Modifier.fillMaxSize()) {
        HeroBackground(accent = accent, letter = current?.label?.firstOrNull()?.uppercase())

        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp)) {
            TopBar(vm = vm, onWeatherClick = { showWeather = true })
            Spacer(Modifier.weight(1f))
            Hero(app = current, accent = accent)

            Spacer(Modifier.height(34.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.apps_title), color = EvimColors.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("  ${vm.apps.size}", color = EvimColors.TextFaint, fontSize = 9.sp)
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                // Odakta büyüyen kart ve arkasındaki hale kesilmesin
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 18.dp),
                modifier = Modifier.offset(x = (-6).dp, y = (-10).dp),
            ) {
                itemsIndexed(vm.apps, key = { _, app -> app.packageName }) { index, app ->
                    AppCard(
                        app = app,
                        moving = movingPackage == app.packageName,
                        modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                        onFocused = { focused = app },
                        onClick = {
                            if (movingPackage != null) movingPackage = null else vm.launch(app)
                        },
                        onLongClick = { if (movingPackage == null) menuFor = app },
                        onMove = { delta -> vm.move(app, delta) },
                    )
                }
            }
            MoveHint(moving = movingPackage != null, modifier = Modifier.offset(y = (-16).dp))
            Spacer(Modifier.height(10.dp))
            HomeDevicesRow()
        }

        menuFor?.let { app ->
            AppMenu(
                app = app,
                hiddenCount = vm.hiddenApps.size,
                onDismiss = { menuFor = null },
                onOpen = { menuFor = null; vm.launch(app) },
                onMove = { menuFor = null; movingPackage = app.packageName },
                onHide = { menuFor = null; vm.hide(app) },
                onColor = { vm.setColor(app, it) },
                onInfo = { menuFor = null; vm.openAppInfo(app) },
                onShowHidden = { menuFor = null; showHidden = true },
            )
        }
        if (showWeather) {
            WeatherDialog(
                city = vm.city,
                forecast = vm.forecast,
                onChangeCity = { showWeather = false; pickingCity = true },
                onDismiss = { showWeather = false },
            )
        }
        if (pickingCity) {
            CityPickerDialog(
                results = vm.citySearchResults,
                searching = vm.citySearching,
                onQueryChange = vm::searchCity,
                onSelect = { city ->
                    vm.selectCity(city)
                    pickingCity = false
                    showWeather = true
                },
                // Vazgeçilirse hava paneline geri dön
                onDismiss = { pickingCity = false; showWeather = true },
            )
        }
        if (showHidden) {
            HiddenAppsDialog(
                apps = vm.hiddenApps,
                onUnhide = vm::unhide,
                onDismiss = { showHidden = false },
            )
        }
    }
}

@Composable
private fun Hero(app: LauncherApp?, accent: () -> Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(20.dp).height(2.dp).drawBehind { drawRect(accent().lighter(0.15f)) })
            Spacer(Modifier.width(8.dp))
            Text(greeting(), color = EvimColors.TextDim, fontSize = 14.sp)
        }
        AnimatedContent(
            targetState = app?.label.orEmpty(),
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "title",
        ) { label ->
            Text(
                text = label,
                color = Color.White,
                fontSize = 64.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                maxLines = 1,
            )
        }
    }
}

/** Yalnızca taşıma modunda görünür; yer kaplamaya devam eder ki düzen kaymasın. */
@Composable
private fun MoveHint(moving: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.height(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (moving) {
            Text(stringResource(R.string.move_hint), color = EvimColors.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.move_hint_done), color = EvimColors.TextFaint, fontSize = 10.sp)
        }
    }
}

/** Sağ tarafa yayılan renk, ince eğri çizgiler ve büyük silik harf. */
@Composable
private fun HeroBackground(accent: () -> Color, letter: String?) {
    // Tam ekran radyal degradeyi her karede hesaplamak Mi Box'ta ~18 ms sürüyor.
    // Bunun yerine degradenin gri tonlu halini bir kez küçük bir resme çizip ekrana geriyoruz;
    // renk geçişi yalnızca resmin renk filtresini değiştiriyor.
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val mask = glowMask()
                val dst = IntSize(size.width.toInt(), size.height.toInt())
                onDrawBehind {
                    drawImage(
                        mask,
                        dstSize = dst,
                        colorFilter = ColorFilter.tint(accent(), BlendMode.Modulate),
                        filterQuality = FilterQuality.Low,
                    )
                }
            },
    )
    // Çizgiler ve koyulaştırma değişmez: ekran dışı katmanda bir kez çizilip doku olarak saklanır
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val lines = Path().apply {
                    val count = 34
                    for (i in 0 until count) {
                        val y = size.height * (i / count.toFloat()) * 1.1f
                        moveTo(size.width * 0.42f, y + size.height * 0.35f)
                        cubicTo(
                            size.width * 0.62f, y + size.height * 0.05f,
                            size.width * 0.82f, y - size.height * 0.08f,
                            size.width * 1.02f, y - size.height * 0.22f,
                        )
                    }
                }
                val lineStroke = Stroke(width = 1.2f)
                // Metnin okunması için soldan ve alttan koyulaştır
                val leftFade = Brush.horizontalGradient(
                    0f to EvimColors.Background,
                    0.35f to EvimColors.Background.copy(alpha = 0.85f),
                    0.65f to Color.Transparent,
                )
                val bottomFade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to EvimColors.Background)
                onDrawBehind {
                    drawPath(lines, Color.White.copy(alpha = 0.045f), style = lineStroke)
                    drawRect(leftFade)
                    drawRect(bottomFade)
                }
            },
    )
    if (letter != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            AnimatedContent(
                targetState = letter,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "letter",
                modifier = Modifier.padding(top = 70.dp, end = 150.dp),
            ) { l ->
                // Bu boyuttaki harf GPU'nun yazı önbelleğine sığmaz; dokuya bir kez çizilip saklanır
                Text(
                    l,
                    color = Color.White.copy(alpha = 0.10f),
                    fontSize = 240.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                )
            }
        }
    }
}

/**
 * Renkli parıltının şablonu: merkezde beyaz, ortada yarı koyu, kenarda saydam.
 * Modulate ile boyanınca vurgu renginin 0.95 → koyulaşmış 0.55 → saydam degradesini verir.
 */
private fun glowMask(): ImageBitmap {
    val w = 384
    val h = 216
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        shader = android.graphics.RadialGradient(
            w * 0.78f, h * 0.18f, w * 0.62f,
            intArrayOf(
                android.graphics.Color.argb(242, 255, 255, 255),
                android.graphics.Color.argb(140, 128, 128, 128),
                android.graphics.Color.argb(0, 0, 0, 0),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    android.graphics.Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bitmap.asImageBitmap()
}

@Composable
private fun greeting(): String = stringResource(
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    },
)
