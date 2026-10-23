package dev.evim.tv.data

import android.content.Context
import androidx.core.content.edit

/** Kullanıcının sıralama, gizleme ve renk tercihleri. Cihazda saklanır. */
class LauncherPrefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var order: List<String>
        get() = sp.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = sp.edit { putString(KEY_ORDER, value.joinToString(",")) }

    var hidden: Set<String>
        get() = sp.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
        set(value) = sp.edit { putStringSet(KEY_HIDDEN, value) }

    fun customColor(packageName: String): Int? =
        if (sp.contains(colorKey(packageName))) sp.getInt(colorKey(packageName), 0) else null

    fun setCustomColor(packageName: String, argb: Int?) = sp.edit {
        if (argb == null) remove(colorKey(packageName)) else putInt(colorKey(packageName), argb)
    }

    var city: City
        get() = City(
            name = sp.getString(KEY_CITY, null) ?: City.DEFAULT.name,
            latitude = sp.getFloat(KEY_LAT, City.DEFAULT.latitude.toFloat()).toDouble(),
            longitude = sp.getFloat(KEY_LON, City.DEFAULT.longitude.toFloat()).toDouble(),
        )
        set(value) = sp.edit {
            putString(KEY_CITY, value.name)
            putFloat(KEY_LAT, value.latitude.toFloat())
            putFloat(KEY_LON, value.longitude.toFloat())
        }

    private fun colorKey(packageName: String) = "color_$packageName"

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
        const val KEY_CITY = "city"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
    }
}
