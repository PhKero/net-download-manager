package com.abdownloadmanager.desktop.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.abdownloadmanager.desktop.window.custom.CustomWindow
import com.abdownloadmanager.desktop.window.custom.WindowIcon
import com.abdownloadmanager.desktop.window.custom.rememberWindowController
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

/**
 * IDM "Preview" for ZIP files: lists what is inside before downloading.
 * A ZIP keeps its table of contents at the end of the file, so only the last bytes are read
 * (with HTTP range requests), never the whole file.
 */
object ZipPreview {
    data class Entry(val name: String, val size: Long, val isFolder: Boolean)

    class PreviewException(message: String) : Exception(message)

    private const val EOCD = 0x06054b50
    private const val ZIP64_LOCATOR = 0x07064b50
    private const val CENTRAL_ENTRY = 0x02014b50
    private const val MAX_DIRECTORY = 16L * 1024 * 1024

    fun isZip(name: String?) = name?.lowercase()?.endsWith(".zip") == true

    suspend fun list(link: String, headers: Map<String, String>): List<Entry> = withContext(Dispatchers.IO) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()

        fun range(spec: String): Pair<ByteArray, Long> {
            val request = HttpRequest.newBuilder(URI(link))
                .timeout(Duration.ofSeconds(30))
                .header("Range", "bytes=$spec")
                .apply {
                    headers.forEach { (k, v) -> runCatching { header(k, v) } }
                    if (headers.keys.none { it.equals("User-Agent", true) }) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Net Download Manager")
                    }
                }
                .GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 206) {
                throw PreviewException("The server does not allow reading part of the file (HTTP ${response.statusCode()}).")
            }
            val total = response.headers().firstValue("Content-Range").orElse("")
                .substringAfter('/', "").toLongOrNull() ?: -1
            return response.body() to total
        }

        // 1. the end of the file: "end of central directory" record (+ up to 64 KB of comment)
        val (tail, total) = range("-65558")
        if (total <= 0) throw PreviewException("Unknown file size.")
        val tailStart = total - tail.size
        val buf = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
        var eocd = -1
        for (i in tail.size - 22 downTo 0) {
            if (buf.getInt(i) == EOCD) { eocd = i; break }
        }
        if (eocd < 0) throw PreviewException("This file is not a ZIP archive.")
        var dirSize = buf.getInt(eocd + 12).toLong() and 0xffffffffL
        var dirOffset = buf.getInt(eocd + 16).toLong() and 0xffffffffL

        // 2. ZIP64 (files over 4 GB): the real numbers are in a separate record
        if (dirOffset == 0xffffffffL || dirSize == 0xffffffffL) {
            val loc = eocd - 20
            if (loc < 0 || buf.getInt(loc) != ZIP64_LOCATOR) throw PreviewException("Unsupported ZIP64 archive.")
            val zip64Eocd = buf.getLong(loc + 8)
            val rec = if (zip64Eocd >= tailStart) {
                ByteBuffer.wrap(tail, (zip64Eocd - tailStart).toInt(), 56).slice().order(ByteOrder.LITTLE_ENDIAN)
            } else {
                ByteBuffer.wrap(range("$zip64Eocd-${zip64Eocd + 55}").first).order(ByteOrder.LITTLE_ENDIAN)
            }
            dirSize = rec.getLong(40)
            dirOffset = rec.getLong(48)
        }
        if (dirSize > MAX_DIRECTORY) throw PreviewException("The list of files is too big to preview.")

        // 3. the table of contents itself
        val dir = if (dirOffset >= tailStart) {
            tail.copyOfRange((dirOffset - tailStart).toInt(), (dirOffset - tailStart + dirSize).toInt())
        } else {
            range("$dirOffset-${dirOffset + dirSize - 1}").first
        }
        val d = ByteBuffer.wrap(dir).order(ByteOrder.LITTLE_ENDIAN)
        val entries = ArrayList<Entry>()
        var p = 0
        while (p + 46 <= dir.size && d.getInt(p) == CENTRAL_ENTRY) {
            val flags = d.getShort(p + 8).toInt()
            var size = d.getInt(p + 24).toLong() and 0xffffffffL
            val nameLen = d.getShort(p + 28).toInt() and 0xffff
            val extraLen = d.getShort(p + 30).toInt() and 0xffff
            val commentLen = d.getShort(p + 32).toInt() and 0xffff
            val nameBytes = dir.copyOfRange(p + 46, p + 46 + nameLen)
            val name = String(nameBytes, if (flags and 0x800 != 0) Charsets.UTF_8 else charset("IBM437"))
            if (size == 0xffffffffL) {
                // ZIP64 extra field: the real uncompressed size
                var e = p + 46 + nameLen
                val end = e + extraLen
                while (e + 4 <= end) {
                    val id = d.getShort(e).toInt() and 0xffff
                    val len = d.getShort(e + 2).toInt() and 0xffff
                    if (id == 0x0001 && len >= 8) { size = d.getLong(e + 4); break }
                    e += 4 + len
                }
            }
            entries += Entry(name, size, name.endsWith("/"))
            p += 46 + nameLen + extraLen + commentLen
        }
        entries
    }
}

private fun humanSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else "%.1f %s".format(v, units[i])
}

@Composable
fun ZipPreviewWindow(
    link: String,
    headers: Map<String, String>,
    onClose: () -> Unit,
) {
    val state = rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Fixed(DpSize(520.dp, 460.dp)),
            positionProvider = WindowPositionProvider.CenteredOnScreen,
        )
    )
    CustomWindow(
        state = state,
        onCloseRequest = onClose,
        onRequestToggleMaximize = null,
        resizable = true,
        alwaysOnTop = true,
        minSize = DpSize(400.dp, 300.dp),
        windowController = rememberWindowController(
            title = tr("What is inside the ZIP", "محتويات ملف الـ ZIP").rememberString(),
        ),
    ) {
        WindowIcon(MyIcons.appIcon)
        var result by remember { mutableStateOf<Result<List<ZipPreview.Entry>>?>(null) }
        LaunchedEffect(link) {
            result = runCatching { ZipPreview.list(link, headers) }
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            val r = result
            when {
                r == null -> Text(tr("Reading the list of files...", "بيقرا قايمة الملفات...").rememberString())
                r.isFailure -> {
                    Text(tr("Could not preview this file.", "مقدرناش نعرض محتويات الملف ده.").rememberString())
                    Spacer(Modifier.height(6.dp))
                    WithContentAlpha(0.75f) {
                        Text(r.exceptionOrNull()?.message.orEmpty(), fontSize = myTextSizes.sm)
                    }
                }

                else -> {
                    val entries = r.getOrThrow()
                    val files = entries.filterNot { it.isFolder }
                    WithContentAlpha(0.8f) {
                        Text(
                            tr(
                                "${files.size} files, ${humanSize(files.sumOf { it.size })} when extracted",
                                "${files.size} ملف، ${humanSize(files.sumOf { it.size })} بعد فك الضغط",
                            ).rememberString(),
                            fontSize = myTextSizes.sm,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().background(myColors.surface / 0.5f).padding(6.dp)) {
                        items(entries) { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(e.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                                if (!e.isFolder) {
                                    Spacer(Modifier.width(8.dp))
                                    WithContentAlpha(0.7f) { Text(humanSize(e.size), fontSize = myTextSizes.sm) }
                                }
                            }
                        }
                    }
                }
            }
            if (result?.isSuccess != true) Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                ActionButton(tr("Close", "إغلاق").rememberString(), onClick = onClose)
            }
        }
    }
}
