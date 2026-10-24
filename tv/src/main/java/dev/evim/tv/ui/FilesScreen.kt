package dev.evim.tv.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dadb.AdbAuthException
import dev.evim.tv.R
import dev.evim.tv.data.FileEntry
import dev.evim.tv.data.FileKind
import dev.evim.tv.data.FileStore
import dev.evim.tv.data.StorageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Ekranda açık olan şey; aynı anda biri. */
private sealed interface FilesOverlay {
    data class Actions(val entry: FileEntry) : FilesOverlay
    data class Rename(val entry: FileEntry) : FilesOverlay
    data class Delete(val entry: FileEntry) : FilesOverlay
    data class Install(val entry: FileEntry) : FilesOverlay
    data class Play(val file: File) : FilesOverlay
    data class Images(val files: List<File>, val start: Int) : FilesOverlay
}

/**
 * Tam ekran dosya yöneticisi: solda depolama alanları (dahili hafıza, USB bellek), sağda klasörün içeriği.
 * OK açar, uzun basış diğer işlemleri gösterir, Geri bir üst klasöre çıkar. Video, müzik ve resimler
 * launcher'ın içinde açılır; APK'lar onaydan sonra ADB ile kurulur.
 */
@Composable
fun FilesDialog(onInstalled: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { FileStore(context) }
    val scope = rememberCoroutineScope()

    // null: izin bekleniyor; false: alınamadı
    var access by remember { mutableStateOf<Boolean?>(null) }
    var accessError by remember { mutableIntStateOf(0) }
    var roots by remember { mutableStateOf<List<StorageRoot>>(emptyList()) }
    var root by remember { mutableStateOf<StorageRoot?>(null) }
    var dir by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<FileEntry>?>(null) }
    // Üst klasöre çıkınca ya da yeniden adlandırınca odak bu ada döner
    var focusName by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf<FilesOverlay?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val granted = withContext(Dispatchers.IO) {
            if (!store.hasAccess) {
                runCatching { store.grantAccess() }.onFailure {
                    accessError = if (it is AdbAuthException) R.string.files_access_denied else R.string.files_access_adb_off
                }
            }
            store.hasAccess
        }
        access = granted
        if (granted) {
            roots = withContext(Dispatchers.IO) { store.roots() }
            root = roots.firstOrNull()
            dir = root?.dir
        }
    }
    LaunchedEffect(dir, reload) {
        val current = dir ?: return@LaunchedEffect
        entries = withContext(Dispatchers.IO) { store.list(current) }
    }

    fun open(entry: FileEntry) {
        val list = entries.orEmpty()
        when (entry.kind) {
            FileKind.FOLDER -> { focusName = null; entries = null; dir = entry.file }
            FileKind.VIDEO, FileKind.AUDIO -> overlay = FilesOverlay.Play(entry.file)
            FileKind.IMAGE -> {
                val images = list.filter { it.kind == FileKind.IMAGE }.map { it.file }
                overlay = FilesOverlay.Images(images, images.indexOf(entry.file).coerceAtLeast(0))
            }
            FileKind.APK -> overlay = FilesOverlay.Install(entry)
            FileKind.OTHER -> {
                val intent = store.openWithIntent(entry.file)
                if (intent != null) store.start(intent) else note = context.getString(R.string.files_cannot_open)
            }
        }
    }

    fun goUp(): Boolean {
        val current = dir ?: return false
        val top = root?.dir ?: return false
        if (current == top) return false
        focusName = current.name
        entries = null
        dir = current.parentFile
        return true
    }

    Dialog(
        onDismissRequest = { if (!goUp()) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(EvimColors.Background)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
                // Sol: başlık ve depolama alanları
                Column(Modifier.width(250.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(EvimColors.Accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FolderOpen, null, tint = EvimColors.Accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.files_title), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(22.dp))
                    roots.forEach { r ->
                        ListRow(
                            title = r.label,
                            subtitle = stringResource(
                                R.string.files_free,
                                Formatter.formatShortFileSize(context, r.freeBytes),
                                Formatter.formatShortFileSize(context, r.totalBytes),
                            ),
                            icon = if (r.removable) Icons.Rounded.Usb else Icons.Rounded.SdStorage,
                            trailing = if (r == root) Trailing.Check(true) else Trailing.None,
                            onClick = { focusName = null; entries = null; root = r; dir = r.dir },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.files_hint), color = EvimColors.TextFaint, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.width(36.dp))
                // Sağ: klasörün içeriği
                Column(Modifier.weight(1f)) {
                    Text(
                        dir?.let { pathLabel(it, root) }.orEmpty(),
                        color = EvimColors.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 12.dp),
                    )
                    when {
                        access == null -> FilesMessage(stringResource(R.string.files_loading))
                        access == false -> FilesMessage(stringResource(accessError.takeIf { it != 0 } ?: R.string.files_access_adb_off))
                        entries == null -> FilesMessage(stringResource(R.string.files_loading))
                        entries!!.isEmpty() -> FilesMessage(stringResource(R.string.files_empty))
                        else -> EntryList(
                            entries = entries!!,
                            focusName = focusName,
                            onOpen = ::open,
                            onActions = { overlay = FilesOverlay.Actions(it) },
                        )
                    }
                }
            }
            note?.let { text ->
                LaunchedEffect(text) {
                    kotlinx.coroutines.delay(3500)
                    note = null
                }
                Text(
                    text,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                        .background(Color(0xE626262E), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    when (val o = overlay) {
        null -> Unit
        is FilesOverlay.Actions -> FileActions(
            entry = o.entry,
            canOpenWith = remember(o) { o.entry.kind != FileKind.FOLDER && store.openWithIntent(o.entry.file) != null },
            onOpen = { overlay = null; open(o.entry) },
            onOpenWith = { overlay = null; store.openWithIntent(o.entry.file)?.let(store::start) },
            onRename = { overlay = FilesOverlay.Rename(o.entry) },
            onDelete = { overlay = FilesOverlay.Delete(o.entry) },
            onDismiss = { overlay = null },
        )
        is FilesOverlay.Rename -> RenameDialog(
            title = stringResource(R.string.files_rename_title),
            subtitle = null,
            current = o.entry.name,
            onSave = { name ->
                overlay = null
                scope.launch {
                    val renamed = withContext(Dispatchers.IO) { store.rename(o.entry.file, name) }
                    if (renamed == null) note = context.getString(R.string.files_rename_failed) else focusName = renamed.name
                    reload++
                }
            },
            onDismiss = { overlay = null },
        )
        is FilesOverlay.Delete -> ConfirmPanel(
            title = stringResource(R.string.files_delete_title, o.entry.name),
            body = stringResource(if (o.entry.kind == FileKind.FOLDER) R.string.files_delete_folder_body else R.string.files_delete_body),
            confirm = stringResource(R.string.files_delete),
            danger = true,
            onConfirm = {
                overlay = null
                scope.launch {
                    val index = entries.orEmpty().indexOf(o.entry)
                    val ok = withContext(Dispatchers.IO) { store.delete(o.entry.file) }
                    if (!ok) note = context.getString(R.string.files_delete_failed)
                    // Odak silinenin yerindeki dosyaya geçsin
                    focusName = entries.orEmpty().getOrNull(index + 1)?.name ?: entries.orEmpty().getOrNull(index - 1)?.name
                    reload++
                }
            },
            onDismiss = { overlay = null },
        )
        is FilesOverlay.Install -> InstallPanel(
            entry = o.entry,
            install = { withContext(Dispatchers.IO) { runCatching { store.install(o.entry.file) }.isSuccess } },
            onInstalled = onInstalled,
            onDismiss = { overlay = null },
        )
        is FilesOverlay.Play -> MediaPlayerDialog(o.file, onDismiss = { overlay = null })
        is FilesOverlay.Images -> ImageViewerDialog(o.files, o.start, onDismiss = { overlay = null })
    }
}

@Composable
private fun EntryList(
    entries: List<FileEntry>,
    focusName: String?,
    onOpen: (FileEntry) -> Unit,
    onActions: (FileEntry) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberLazyListState()
    val target = remember { FocusRequester() }
    val focusIndex = entries.indexOfFirst { it.name == focusName }.coerceAtLeast(0)
    LaunchedEffect(entries) {
        state.scrollToItem((focusIndex - 3).coerceAtLeast(0))
        runCatching { target.requestFocus() }
    }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(entries, key = { _, e -> e.file.path }) { index, entry ->
            val detail = if (entry.kind == FileKind.FOLDER) {
                DateFormat.getMediumDateFormat(context).format(entry.modified)
            } else {
                Formatter.formatShortFileSize(context, entry.size) + "  ·  " + DateFormat.getMediumDateFormat(context).format(entry.modified)
            }
            FileRow(
                entry = entry,
                detail = detail,
                modifier = if (index == focusIndex) Modifier.focusRequester(target) else Modifier,
                onClick = { onOpen(entry) },
                onLongClick = { onActions(entry) },
            )
        }
    }
}

/** [ListRow] gibi, ama uzun basışı da dinler. */
@Composable
private fun FileRow(entry: FileEntry, detail: String, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val (icon, tint) = entry.kind.look()
    androidx.tv.material3.Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0x0DFFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(entry.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(detail, fontSize = 11.sp, maxLines = 1, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun FileActions(
    entry: FileEntry,
    canOpenWith: Boolean,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val (icon, tint) = entry.kind.look()
    Dialog(onDismissRequest = onDismiss) {
        Panel(width = 360.dp) {
            PanelHeader(entry.name, null, icon, tint)
            ListRow(stringResource(R.string.menu_open), icon = Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(first), onClick = onOpen)
            if (canOpenWith) ListRow(stringResource(R.string.files_open_with), icon = Icons.Rounded.OpenInNew, onClick = onOpenWith)
            ListRow(stringResource(R.string.menu_rename), icon = Icons.Rounded.Edit, onClick = onRename)
            ListRow(stringResource(R.string.files_delete), icon = Icons.Rounded.Delete, tint = EvimColors.Danger, onClick = onDelete)
        }
    }
}

@Composable
private fun ConfirmPanel(title: String, body: String, confirm: String, danger: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Panel(width = 420.dp) {
            PanelHeader(title, null, Icons.Rounded.Delete, if (danger) EvimColors.Danger else EvimColors.Accent)
            PanelText(body)
            PanelActions {
                PillButton(stringResource(R.string.cancel), Modifier.focusRequester(cancel), onClick = onDismiss)
                PillButton(confirm, danger = danger, primary = !danger, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun InstallPanel(entry: FileEntry, install: suspend () -> Boolean, onInstalled: () -> Unit, onDismiss: () -> Unit) {
    // result null: henüz kurulmadı; true/false: kurulumun sonucu
    var installing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(installing, result) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = { if (!installing) onDismiss() }) {
        Panel(width = 420.dp) {
            PanelHeader(stringResource(R.string.files_install_title), entry.name, Icons.Rounded.Android, EvimColors.Online)
            when {
                installing -> PanelNote(stringResource(R.string.files_installing), EvimColors.Accent)
                result == true -> PanelNote(stringResource(R.string.files_installed), EvimColors.Online)
                result == false -> PanelNote(stringResource(R.string.files_install_failed), EvimColors.Danger)
                else -> PanelText(stringResource(R.string.files_install_body))
            }
            PanelActions {
                when {
                    installing -> Unit
                    result != null -> PillButton(stringResource(R.string.done), Modifier.focusRequester(first), primary = true, onClick = onDismiss)
                    else -> {
                        PillButton(stringResource(R.string.cancel), onClick = onDismiss)
                        PillButton(stringResource(R.string.files_install), Modifier.focusRequester(first), primary = true) {
                            installing = true
                            scope.launch {
                                val ok = install()
                                if (ok) onInstalled()
                                result = ok
                                installing = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesMessage(text: String) {
    Text(text, color = EvimColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

private fun FileKind.look(): Pair<ImageVector, Color> = when (this) {
    FileKind.FOLDER -> Icons.Rounded.Folder to EvimColors.Accent
    FileKind.VIDEO -> Icons.Rounded.Movie to EvimColors.Camera
    FileKind.AUDIO -> Icons.Rounded.Audiotrack to EvimColors.Tuya
    FileKind.IMAGE -> Icons.Rounded.Image to EvimColors.Dyson
    FileKind.APK -> Icons.Rounded.Android to EvimColors.Online
    FileKind.OTHER -> Icons.Rounded.Description to Color(0xFF8A93A6)
}

/** "Dahili hafıza / Download / Filmler" gibi; kök klasörün kendi yolu yerine alanın adı. */
private fun pathLabel(dir: File, root: StorageRoot?): String {
    val top = root?.dir ?: return dir.path
    val rest = dir.path.removePrefix(top.path).trim('/')
    return if (rest.isEmpty()) root.label else root.label + " / " + rest.replace("/", " / ")
}
