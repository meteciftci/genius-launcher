package dev.evim.tv.data

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.evim.tv.R
import java.io.File

/** Dosyalar ekranındaki bir depolama alanı: dahili hafıza ya da takılı USB bellek. */
data class StorageRoot(val dir: File, val label: String, val removable: Boolean, val freeBytes: Long, val totalBytes: Long)

enum class FileKind { FOLDER, VIDEO, AUDIO, IMAGE, APK, OTHER }

data class FileEntry(val file: File, val kind: FileKind, val size: Long, val modified: Long) {
    val name: String get() = file.name
}

/**
 * Dosyalar ekranının veri katmanı. Tüm dosyalara erişim izni (Android 11+) Google TV'de ayarlardan
 * verilemediği için launcher bu izni kendi ADB bağlantısıyla kendine verir; APK'lar da aynı yolla kurulur.
 * Dosya işlemleri arka plan iş parçacığından.
 */
class FileStore(private val context: Context) {
    private val adb = LocalAdb(context)

    val hasAccess: Boolean get() = Environment.isExternalStorageManager()

    /** İzni ADB ile verir; ADB'ye ulaşılamazsa hata fırlatır. */
    fun grantAccess() {
        adb.shell("appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow")
    }

    fun roots(): List<StorageRoot> {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume ->
                val dir = volume.directory ?: return@mapNotNull null
                val stat = runCatching { StatFs(dir.path) }.getOrNull()
                StorageRoot(
                    dir = dir,
                    // Sistemin "Dahili olarak paylaşılan depolama alanı" adı panele sığmıyor
                    label = if (volume.isPrimary) context.getString(R.string.files_internal) else volume.getDescription(context),
                    removable = volume.isRemovable,
                    freeBytes = stat?.availableBytes ?: 0,
                    totalBytes = stat?.totalBytes ?: 0,
                )
            }
    }

    /** Klasörler önce, sonra dosyalar; ikisi de ada göre. Gizli (nokta ile başlayan) dosyalar gösterilmez. */
    fun list(dir: File): List<FileEntry> =
        dir.listFiles().orEmpty()
            .filter { !it.name.startsWith(".") }
            .map { FileEntry(it, kindOf(it), if (it.isDirectory) 0 else it.length(), it.lastModified()) }
            .sortedWith(compareBy<FileEntry>({ it.kind != FileKind.FOLDER }, { it.name.lowercase() }))

    fun delete(file: File): Boolean = file.deleteRecursively()

    /** Aynı adda bir dosya varsa ya da ad geçersizse null. */
    fun rename(file: File, name: String): File? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/') || clean == "." || clean == "..") return null
        val target = File(file.parentFile, clean)
        if (target.exists()) return null
        return target.takeIf { file.renameTo(it) }
    }

    /**
     * APK'yı ADB ile kurar. Paket yöneticisi /sdcard'daki dosyayı kendisi okuyamıyor; dosya shell'den
     * boruyla verilir. Başarısız olursa hata fırlatır.
     */
    fun install(apk: File) {
        val path = "'" + apk.path.replace("'", "'\\''") + "'"
        adb.shell("cat $path | pm install -r -S ${apk.length()}")
    }

    /** Dosyayı açabilecek gerçek uygulamalar için niyet; Google TV'nin "uygulama yok" sayfası sayılmaz. */
    fun openWithIntent(file: File): Intent? {
        val mime = mimeOf(file) ?: return null
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull() ?: return null
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .filter { it != STUBS_PACKAGE && it != context.packageName }
        return intent.takeIf { handlers.isNotEmpty() }
    }

    fun start(intent: Intent) {
        runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun mimeOf(file: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

    companion object {
        private const val STUBS_PACKAGE = "com.android.tv.frameworkpackagestubs"
        private val VIDEO = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "ts", "3gp", "mpg", "mpeg", "wmv", "flv")
        private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "amr")
        private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

        fun kindOf(file: File): FileKind = when {
            file.isDirectory -> FileKind.FOLDER
            else -> when (file.extension.lowercase()) {
                in VIDEO -> FileKind.VIDEO
                in AUDIO -> FileKind.AUDIO
                in IMAGE -> FileKind.IMAGE
                "apk" -> FileKind.APK
                else -> FileKind.OTHER
            }
        }
    }
}
