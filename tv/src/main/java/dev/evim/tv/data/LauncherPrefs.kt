package dev.evim.tv.data

import android.content.Context
import androidx.core.content.edit

/** Kullanıcının sıralama, gizleme ve renk tercihleri. Cihazda saklanır. */
class LauncherPrefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var order: List<String>
        get() = sp.getString(KEY_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = sp.edit { putString(KEY_ORDER, value.joinToString(",")) }

    /** Evim widget'ındaki cihazların sırası (cihaz anahtarları). */
    var deviceOrder: List<String>
        get() = sp.getString(KEY_DEVICE_ORDER, "").orEmpty().split(',').filter { it.isNotBlank() }
        set(value) = sp.edit { putString(KEY_DEVICE_ORDER, value.joinToString(",")) }

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
            region = sp.getString(KEY_REGION, null) ?: City.DEFAULT.region,
        )
        set(value) = sp.edit {
            putString(KEY_CITY, value.name)
            putFloat(KEY_LAT, value.latitude.toFloat())
            putFloat(KEY_LON, value.longitude.toFloat())
            putString(KEY_REGION, value.region)
        }

    /** Kullanıcının launcher'da verdiği cihaz adı; yoksa cihazın kendi adı kullanılır. */
    fun deviceName(key: String): String? = sp.getString(nameKey(key), null)

    fun setDeviceName(key: String, name: String?) = sp.edit {
        if (name.isNullOrBlank()) remove(nameKey(key)) else putString(nameKey(key), name.trim())
    }

    /** Kaldırılan cihazın adı ve sıradaki yeri unutulur. */
    fun forgetDevice(key: String) {
        setDeviceName(key, null)
        deviceOrder = deviceOrder - key
    }

    private fun colorKey(packageName: String) = "color_$packageName"

    private fun nameKey(key: String) = "device_name_$key"

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
        const val KEY_DEVICE_ORDER = "device_order"
        const val KEY_CITY = "city"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_REGION = "region"
    }
}
