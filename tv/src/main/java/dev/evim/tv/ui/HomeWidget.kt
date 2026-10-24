package dev.evim.tv.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.LauncherViewModel
import dev.evim.tv.R
import dev.evim.tv.data.home.AirQuality
import dev.evim.tv.data.home.HomeDevice
import dev.evim.tv.data.home.Meal
import dev.evim.tv.data.home.celsiusToDyson

/** Widget'ta gösterilen en fazla kart; fazlası "Diğer" kartındaki panelde. */
private const val WIDGET_LIMIT = 4

private val CardShape = RoundedCornerShape(12.dp)

/**
 * Ana ekranın sağındaki Evim widget'ı: cihazlar kullanıcının sırasıyla alt alta.
 * Karta uzun basınca menü açılır (aç, taşı, yeniden adlandır). Taşıma modunda yukarı/aşağı ile
 * yer değiştirir, OK ya da Geri bitirir.
 */
@Composable
fun HomeWidget(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    var feeder by remember { mutableStateOf<HomeDevice.Feeder?>(null) }
    var purifierKey by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var panelMovingKey by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<HomeDevice?>(null) }
    var renaming by remember { mutableStateOf<HomeDevice?>(null) }

    val open: (HomeDevice) -> Unit = { device ->
        when (device) {
            is HomeDevice.Purifier -> purifierKey = device.key
            is HomeDevice.Feeder -> feeder = device
            is HomeDevice.Relay -> if (device.reachable) vm.toggleRelay(device)
            is HomeDevice.Other -> Unit
        }
    }

    BackHandler(enabled = movingKey != null && !showAll) { movingKey = null }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Cihaz yoksa widget hiç görünmez; ekleme Evim kutusundan
        val devices = vm.homeDevices
        val visible = if (devices.size > WIDGET_LIMIT) devices.take(WIDGET_LIMIT - 1) else devices
        visible.forEach { device ->
            key(device.key) {
                DeviceCard(
                    device = device,
                    moving = movingKey == device.key,
                    onClick = { if (movingKey != null) movingKey = null else open(device) },
                    onLongClick = { if (movingKey == null) menuFor = device },
                    onMove = { vm.moveDevice(device, it) },
                )
            }
        }
        if (devices.size > WIDGET_LIMIT) {
            OtherCard(count = devices.size - visible.size) { showAll = true }
        }
        MoveHint(visible = movingKey != null && !showAll)
    }

    if (showAll) {
        AllDevicesDialog(
            devices = vm.homeDevices,
            movingKey = panelMovingKey,
            onOpen = open,
            onMenu = { menuFor = it },
            onMove = vm::moveDevice,
            onStopMoving = { panelMovingKey = null },
            onDismiss = { showAll = false; panelMovingKey = null },
        )
    }
    menuFor?.let { device ->
        DeviceMenu(
            device = device,
            customName = vm.hasCustomName(device),
            onDismiss = { menuFor = null },
            onOpen = { menuFor = null; open(device) },
            onMove = {
                menuFor = null
                if (showAll) panelMovingKey = device.key else movingKey = device.key
            },
            onRename = { menuFor = null; renaming = device },
            onResetName = { menuFor = null; vm.renameDevice(device, null) },
        )
    }
    renaming?.let { device ->
        RenameDialog(
            current = device.name,
            onSave = { vm.renameDevice(device, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    feeder?.let { device ->
        FeederDialog(device, onFeed = { vm.feed(device, it); feeder = null }, onDismiss = { feeder = null })
    }
    // Komut sonrası güncel durumu göstermek için cihaz listeden her seferinde yeniden alınır
    (vm.homeDevices.firstOrNull { it.key == purifierKey } as? HomeDevice.Purifier)?.let { device ->
        PurifierDialog(device, onSet = { vm.setPurifier(device, it) }, onDismiss = { purifierKey = null })
    }
}

/** Tüm cihazlar; widget'a sığmayanlar da burada. Sıralama widget ile aynı. */
@Composable
private fun AllDevicesDialog(
    devices: List<HomeDevice>,
    movingKey: String?,
    onOpen: (HomeDevice) -> Unit,
    onMenu: (HomeDevice) -> Unit,
    onMove: (HomeDevice, Int) -> Unit,
    onStopMoving: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Dialog(
        onDismissRequest = { if (movingKey != null) onStopMoving() else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Panel(width = 420.dp) {
            DialogTitle(stringResource(R.string.all_devices_title), null)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                devices.forEachIndexed { i, device ->
                    key(device.key) {
                        DeviceCard(
                            device = device,
                            moving = movingKey == device.key,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { if (movingKey != null) onStopMoving() else onOpen(device) },
                            onLongClick = { if (movingKey == null) onMenu(device) },
                            onMove = { onMove(device, it) },
                        )
                    }
                }
            }
            MoveHint(visible = movingKey != null)
        }
    }
}

/** Karta uzun basınca açılır; uygulama kartlarındaki menünün eşi. */
@Composable
private fun DeviceMenu(
    device: HomeDevice,
    customName: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onResetName: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Panel {
            DialogTitle(device.name, null)
            MenuItem(stringResource(R.string.menu_open), Modifier.focusRequester(first), onOpen)
            MenuItem(stringResource(R.string.menu_move), onClick = onMove)
            MenuItem(stringResource(R.string.menu_rename), onClick = onRename)
            if (customName) MenuItem(stringResource(R.string.menu_reset_name), onClick = onResetName)
        }
    }
}

/** Kumandanın ekran klavyesiyle yeni ad yazılır; boş bırakılırsa cihazın kendi adına döner. */
@Composable
internal fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    Dialog(onDismissRequest = onDismiss) {
        Panel(width = 420.dp) {
            DialogTitle(stringResource(R.string.rename_title), stringResource(R.string.rename_subtitle))
            TextInput(name, { name = it }, Modifier.focusRequester(field))
            Spacer(Modifier.height(10.dp))
            MenuItem(stringResource(R.string.rename_save)) { onSave(name.text) }
            MenuItem(stringResource(R.string.cancel), onClick = onDismiss)
        }
    }
}

/** Tek satırlık yazı alanı; OK'e basınca ekran klavyesi kapanır. */
@Composable
internal fun TextInput(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) { inner() }
        },
    )
}

@Composable
private fun DeviceCard(
    device: HomeDevice,
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    WidgetSurface(
        moving = moving,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onPreviewKeyEvent { event ->
            // Taşıma modunda yukarı/aşağı odak değiştirmez, kartı kaydırır
            if (!moving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> { onMove(-1); true }
                Key.DirectionDown -> { onMove(1); true }
                Key.DirectionLeft, Key.DirectionRight -> true
                else -> false
            }
        },
    ) {
        if (device is HomeDevice.Purifier) PurifierContent(device) else CompactContent(device)
    }
}

/** Dyson büyük kart: sıcaklık öne çıkar, nem/hedef ve hava kalitesi yanında. */
@Composable
private fun PurifierContent(device: HomeDevice.Purifier) {
    val state = device.state
    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(device.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            state?.airQuality?.let { AirBadge(it) }
            Spacer(Modifier.width(6.dp))
            StatusDot(active = state?.power == true)
        }
        if (state == null) {
            Text(stringResource(R.string.home_unreachable), color = EvimColors.TextDim, fontSize = 11.sp)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.temperatureCelsius?.let { "$it°" } ?: "—", fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            Spacer(Modifier.width(10.dp))
            val details = listOfNotNull(
                state.humidity?.let { stringResource(R.string.purifier_humidity, it) },
                state.targetCelsius?.takeIf { state.heating }?.let { stringResource(R.string.purifier_target, it) },
            ).joinToString("  ·  ")
            Text(details, color = EvimColors.TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

/** Mama kabı ve röle gibi tek satırlık cihazlar. */
@Composable
private fun CompactContent(device: HomeDevice) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            device.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(device.detail(), color = EvimColors.TextDim, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        StatusDot(active = device.reachable && (device !is HomeDevice.Relay || device.on))
    }
}


@Composable
private fun OtherCard(count: Int, onClick: () -> Unit) {
    WidgetSurface(moving = false, onClick = onClick, onLongClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_other), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_other_count, count), color = EvimColors.TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WidgetSurface(
    moving: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(
            // Arka plandaki renk sürekli değiştiği için koyu, ince çerçeveli sade bir kart
            border = Border(BorderStroke(1.dp, if (moving) EvimColors.Warning else EvimColors.ChipBorder), shape = CardShape),
            focusedBorder = Border(BorderStroke(1.5.dp, if (moving) EvimColors.Warning else Color.White), shape = CardShape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EvimColors.ChipSolid,
            contentColor = Color.White,
            focusedContainerColor = Color(0xE6202026),
            focusedContentColor = Color.White,
        ),
    ) {
        content()
    }
}

@Composable
private fun MoveHint(visible: Boolean) {
    if (visible) {
        Text(stringResource(R.string.device_move_hint), color = EvimColors.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(Modifier.size(6.dp).background(if (active) EvimColors.Online else EvimColors.TextFaint, CircleShape))
}

@Composable
private fun AirBadge(quality: AirQuality) {
    // İyi yeşil, orta sarı, daha kötüsü kırmızı tonlarında
    val (background, text) = when (quality) {
        AirQuality.GOOD -> Color(0xFF9FE1CB) to Color(0xFF04342C)
        AirQuality.FAIR -> Color(0xFFFAC775) to Color(0xFF412402)
        else -> Color(0xFFF09595) to Color(0xFF501313)
    }
    Text(
        stringResource(quality.label),
        color = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun HomeDevice.detail(): String {
    if (!reachable) return stringResource(R.string.home_unreachable)
    return when (this) {
        is HomeDevice.Relay -> stringResource(if (on) R.string.relay_on else R.string.relay_off)
        is HomeDevice.Feeder -> nextMeal?.let { stringResource(R.string.feeder_meal, it.time(), it.portions) }.orEmpty()
        is HomeDevice.Purifier -> ""
        is HomeDevice.Other -> stringResource(R.string.home_online)
    }
}

@get:StringRes
private val AirQuality.label: Int
    get() = when (this) {
        AirQuality.GOOD -> R.string.air_good
        AirQuality.FAIR -> R.string.air_fair
        AirQuality.POOR -> R.string.air_poor
        AirQuality.VERY_POOR -> R.string.air_very_poor
        AirQuality.SEVERE -> R.string.air_severe
        AirQuality.EXTREME -> R.string.air_extreme
    }

private fun Meal.time() = "%02d:%02d".format(hour, minute)

@Composable
private fun FeederDialog(device: HomeDevice.Feeder, onFeed: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val feeding = stringResource(R.string.feed_started)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val feed = { portions: Int ->
        onFeed(portions)
        Toast.makeText(context, feeding, Toast.LENGTH_SHORT).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            DialogTitle(device.name, device.nextMeal?.let { stringResource(R.string.feeder_next_meal, it.time(), it.portions) })
            if (device.reachable) {
                // Yanlışlıkla OK'e basınca mama dökülmesin diye odak "Vazgeç"te başlar
                MenuItem(stringResource(R.string.feed_one)) { feed(1) }
                MenuItem(stringResource(R.string.feed_two)) { feed(2) }
                MenuItem(stringResource(R.string.cancel), Modifier.focusRequester(first), onDismiss)
            } else {
                MenuItem(stringResource(R.string.home_unreachable), Modifier.focusRequester(first), onDismiss)
            }
        }
    }
}

@Composable
private fun PurifierDialog(device: HomeDevice.Purifier, onSet: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val state = device.state

    Dialog(onDismissRequest = onDismiss) {
        Panel {
            val humidity = state?.humidity?.let { stringResource(R.string.purifier_humidity, it) }
            val air = state?.airQuality?.let { stringResource(R.string.purifier_air, stringResource(it.label)) }
            val summary = listOfNotNull(
                state?.temperatureCelsius?.let { "$it°" },
                humidity,
                air,
                state?.pm25?.let { "PM2.5 $it" },
            ).joinToString("  ·  ")
            DialogTitle(device.name, summary)
            if (state == null) {
                MenuItem(stringResource(R.string.home_unreachable), Modifier.focusRequester(first), onDismiss)
                return@Panel
            }
            MenuItem(
                stringResource(if (state.power) R.string.purifier_turn_off else R.string.purifier_turn_on),
                Modifier.focusRequester(first),
            ) { onSet(mapOf("fpwr" to if (state.power) "OFF" else "ON")) }
            if (state.power) {
                ToggleItem(R.string.purifier_auto, state.auto) { onSet(mapOf("auto" to if (state.auto) "OFF" else "ON")) }
                val speed = state.fanSpeed
                if (speed != null) {
                    // Elle hız seçmek otomatik modu kapatır
                    if (speed > 1) MenuItem(stringResource(R.string.purifier_fan_down, speed)) { onSet(fanSpeed(speed - 1)) }
                    if (speed < 10) MenuItem(stringResource(R.string.purifier_fan_up, speed)) { onSet(fanSpeed(speed + 1)) }
                }
                ToggleItem(R.string.purifier_heating, state.heating) { onSet(mapOf("hmod" to if (state.heating) "OFF" else "HEAT")) }
                val target = state.targetCelsius
                if (state.heating && target != null) {
                    // Dyson ısıtıcının kabul ettiği aralık 1-37 °C
                    if (target > 1) MenuItem(stringResource(R.string.purifier_temp_down, target)) { onSet(mapOf("hmax" to celsiusToDyson(target - 1))) }
                    if (target < 37) MenuItem(stringResource(R.string.purifier_temp_up, target)) { onSet(mapOf("hmax" to celsiusToDyson(target + 1))) }
                }
            }
        }
    }
}

private fun fanSpeed(speed: Int) = mapOf("auto" to "OFF", "fnsp" to "%04d".format(speed))

@Composable
private fun ToggleItem(@StringRes label: Int, on: Boolean, onClick: () -> Unit) {
    val value = stringResource(if (on) R.string.relay_on else R.string.relay_off)
    MenuItem("${stringResource(label)}: $value", onClick = onClick)
}

@Composable
private fun DialogTitle(title: String, subtitle: String?) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (!subtitle.isNullOrEmpty()) Text(subtitle, color = EvimColors.TextDim, fontSize = 11.sp)
    Spacer(Modifier.height(14.dp))
}
