package dev.evim.tv.ui

import java.util.Locale
import java.time.LocalDate
import dev.evim.tv.data.weatherDescription
import dev.evim.tv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.evim.tv.data.City
import dev.evim.tv.data.DailyWeather
import dev.evim.tv.data.Forecast

/** Hava durumu kutucuğuna basınca açılan panel: şu an, 12 saat ve 7 gün. */
@Composable
fun WeatherDialog(
    city: City,
    forecast: Forecast?,
    onChangeCity: () -> Unit,
    onDismiss: () -> Unit,
) {
    val changeButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { changeButton.requestFocus() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(width = 700.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(city.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (city.region.isNotBlank()) Text(city.region, color = EvimColors.TextFaint, fontSize = 11.sp)
                }
                Surface(
                    onClick = onChangeCity,
                    modifier = Modifier.focusRequester(changeButton),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x1AFFFFFF),
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF111111),
                    ),
                ) {
                    Text(
                        stringResource(R.string.weather_change_city),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            if (forecast == null) {
                Spacer(Modifier.height(30.dp))
                Text(stringResource(R.string.weather_loading), color = EvimColors.TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(30.dp))
                return@Panel
            }

            Spacer(Modifier.height(16.dp))
            CurrentWeather(forecast)
            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(R.string.weather_hourly))
            HourlyRow(forecast)
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.weather_daily))
            DailyList(forecast.daily)
        }
    }
}

@Composable
private fun CurrentWeather(forecast: Forecast) {
    val now = forecast.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(now.symbol, color = Color.White, fontSize = 40.sp)
        Spacer(Modifier.width(14.dp))
        Text("${now.temperature}°", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(describe(now.code), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.weather_feels_like, now.feelsLike), color = EvimColors.TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        Detail(stringResource(R.string.weather_humidity), stringResource(R.string.percent, now.humidity))
        Detail(stringResource(R.string.weather_wind), stringResource(R.string.weather_wind_value, now.windKmh))
        Detail(stringResource(R.string.weather_sunrise), forecast.sunrise)
        Detail(stringResource(R.string.weather_sunset), forecast.sunset)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.padding(start = 18.dp), horizontalAlignment = Alignment.End) {
        Text(label, color = EvimColors.TextFaint, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = EvimColors.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun HourlyRow(forecast: Forecast) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
    ) {
        forecast.hourly.forEach { h ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (h.isNow) stringResource(R.string.weather_now) else h.hour, color = EvimColors.TextDim, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Text(h.symbol, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text("${h.temperature}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (h.rainChance >= 10) stringResource(R.string.percent, h.rainChance) else " ",
                    color = RainBlue,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun DailyList(days: List<DailyWeather>) {
    // Sıcaklık çubukları haftanın en düşük/en yüksek değerine göre ölçeklenir
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }.coerceAtLeast(weekMin + 1)
    Column(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(d.date, i), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                Text(d.symbol, color = Color.White, fontSize = 14.sp, modifier = Modifier.width(28.dp))
                Text(describe(d.code), color = EvimColors.TextDim, fontSize = 11.sp, modifier = Modifier.width(120.dp))
                Text(
                    if (d.rainChance >= 10) "☂ " + stringResource(R.string.percent, d.rainChance) else "",
                    color = RainBlue,
                    fontSize = 10.sp,
                    modifier = Modifier.width(56.dp),
                )
                Text("${d.min}°", color = EvimColors.TextDim, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
                TemperatureBar(d.min, d.max, weekMin, weekMax, Modifier.weight(1f).padding(horizontal = 10.dp))
                Text("${d.max}°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(34.dp))
            }
        }
    }
}

@Composable
private fun TemperatureBar(min: Int, max: Int, weekMin: Int, weekMax: Int, modifier: Modifier) {
    val span = (weekMax - weekMin).toFloat()
    Box(modifier.height(4.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(50))) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
            val start = maxWidth * ((min - weekMin) / span)
            val width = maxWidth * ((max - min).coerceAtLeast(1) / span)
            Box(
                Modifier
                    .offset(x = start)
                    .width(width)
                    .fillMaxHeight()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF5AB0F0), EvimColors.Warning)),
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/** Kumandayla şehir adı yazılıp sonuçlardan seçilir. */
@Composable
fun CityPickerDialog(
    results: List<City>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (City) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        keyboard?.show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel(width = 420.dp) {
            PanelHeader(stringResource(R.string.city_title), stringResource(R.string.city_subtitle), Icons.Rounded.LocationOn)
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.focusRequester(field).fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (query.isEmpty()) Text(stringResource(R.string.city_hint), color = EvimColors.TextFaint, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            when {
                searching -> Text(stringResource(R.string.city_searching), color = EvimColors.TextDim, fontSize = 12.sp)
                query.trim().length >= 2 && results.isEmpty() -> Text(stringResource(R.string.city_no_results), color = EvimColors.TextDim, fontSize = 12.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                results.take(6).forEach { city ->
                    MenuItem(
                        if (city.region.isBlank()) city.name else "${city.name}  ·  ${city.region}",
                        onClick = { onSelect(city) },
                    )
                }
            }
        }
    }
}

@Composable
private fun describe(code: Int): String = weatherDescription(code)?.let { stringResource(it) } ?: "—"

/** Bugün ve yarın için kelime, sonraki günler için sistem dilinde gün adı. */
@Composable
private fun dayLabel(date: LocalDate, index: Int): String = when (index) {
    0 -> stringResource(R.string.weather_today)
    1 -> stringResource(R.string.weather_tomorrow)
    else -> {
        val locale = Locale.getDefault()
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
    }
}

private val RainBlue = Color(0xFF7CC4F5)
