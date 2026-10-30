package com.abdownloadmanager.desktop.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.RadioButton
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/**
 * IDM style "Grabber": a small wizard that explores a web site and collects the files it links to
 * (images, videos, music, documents...), then hands the chosen ones to the normal add-download dialog.
 * Steps: 1) start page + optional login, 2) what to grab and how deep, 3) explore and pick.
 */
object SiteGrabber {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen
    fun open() {
        _isOpen.value = true
    }

    fun close() {
        _isOpen.value = false
    }
}

enum class GrabTemplate(val extensions: Set<String>) {
    AllFiles(emptySet()),
    Images(setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "tif", "tiff", "avif")),
    Video(setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "mpg", "mpeg", "3gp", "ts")),
    Music(setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")),
    Documents(setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "epub", "csv")),
    Archives(setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "exe", "msi", "apk", "dmg")),
    Custom(emptySet());

    fun title() = when (this) {
        AllFiles -> tr("All files", "كل الملفات")
        Images -> tr("Images", "الصور")
        Video -> tr("Video", "الفيديو")
        Music -> tr("Music", "الموسيقى")
        Documents -> tr("Documents", "المستندات")
        Archives -> tr("Programs and archives", "البرامج والملفات المضغوطة")
        Custom -> tr("Custom extensions", "امتدادات تختارها")
    }
}

data class GrabbedFile(val url: String, val name: String, val extension: String)

private val pageExtensions = setOf("", "html", "htm", "php", "asp", "aspx", "jsp", "shtml", "xhtml", "cgi")
private val knownFileExtensions = GrabTemplate.entries.flatMap { it.extensions }.toSet()
private val linkRegex = Regex("""(?:href|src|data-src)\s*=\s*["']([^"'#<>\s]+)["']""", RegexOption.IGNORE_CASE)

private fun extensionOf(url: String): String {
    val path = runCatching { URI(url).path }.getOrNull() ?: return ""
    val last = path.substringAfterLast('/')
    return if ('.' in last) last.substringAfterLast('.').lowercase() else ""
}

private fun nameOf(url: String): String {
    val path = runCatching { URI(url).path }.getOrNull().orEmpty()
    return java.net.URLDecoder.decode(path.substringAfterLast('/'), Charsets.UTF_8).ifBlank { url }
}

/** explores pages breadth first; reports progress through the callbacks */
private suspend fun crawl(
    startUrl: String,
    wanted: (String) -> Boolean,
    maxDepth: Int,
    maxPages: Int,
    sameSiteOnly: Boolean,
    authHeader: String?,
    onPageScanned: (count: Int, current: String) -> Unit,
    onFileFound: (GrabbedFile) -> Unit,
) = withContext(Dispatchers.IO) {
    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    val startHost = runCatching { URI(startUrl).host }.getOrNull()?.removePrefix("www.")
    val visited = HashSet<String>()
    val found = HashSet<String>()
    var queue = listOf(startUrl)
    var scanned = 0
    for (depth in 0..maxDepth) {
        val next = ArrayList<String>()
        for (page in queue) {
            if (!isActive || scanned >= maxPages) return@withContext
            if (!visited.add(page)) continue
            onPageScanned(scanned + 1, page)
            val html = runCatching {
                val request = HttpRequest.newBuilder(URI(page))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Net Download Manager Grabber")
                    .apply { authHeader?.let { header("Authorization", it) } }
                    .GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val type = response.headers().firstValue("Content-Type").orElse("")
                if (response.statusCode() in 200..299 && type.contains("html", ignoreCase = true)) response.body() else null
            }.getOrNull()
            scanned++
            if (html == null) continue
            val base = runCatching { URI(page) }.getOrNull() ?: continue
            for (match in linkRegex.findAll(html)) {
                val raw = match.groupValues[1].replace("&amp;", "&")
                if (raw.startsWith("javascript:") || raw.startsWith("mailto:") || raw.startsWith("data:")) continue
                val absolute = runCatching { base.resolve(raw.replace(" ", "%20")).toString() }.getOrNull() ?: continue
                if (!absolute.startsWith("http")) continue
                val host = runCatching { URI(absolute).host }.getOrNull()?.removePrefix("www.")
                val ext = extensionOf(absolute)
                if (ext in pageExtensions) {
                    if (depth < maxDepth && (!sameSiteOnly || host == startHost)) next.add(absolute.substringBefore('#'))
                } else if (wanted(ext) && found.add(absolute)) {
                    onFileFound(GrabbedFile(absolute, nameOf(absolute), ext))
                }
            }
        }
        queue = next
        if (queue.isEmpty()) break
    }
}

@Composable
fun SiteGrabberWindow(onDownload: (links: List<String>, referer: String, username: String?, password: String?) -> Unit) {
    val isOpen by SiteGrabber.isOpen.collectAsState()
    if (!isOpen) return
    val state = rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Fixed(DpSize(620.dp, 480.dp)),
            positionProvider = WindowPositionProvider.CenteredOnScreen,
        )
    )
    val scope = rememberCoroutineScope()
    var crawlJob by remember { mutableStateOf<Job?>(null) }
    val closeWindow = {
        crawlJob?.cancel()
        SiteGrabber.close()
    }
    CustomWindow(
        state = state,
        onCloseRequest = closeWindow,
        onRequestToggleMaximize = null,
        resizable = true,
        minSize = DpSize(520.dp, 420.dp),
        windowController = rememberWindowController(
            title = tr("Grabber", "جامع الملفات").rememberString(),
        ),
    ) {
        WindowIcon(MyIcons.appIcon)
        var step by remember { mutableStateOf(1) }
        var projectName by remember { mutableStateOf("") }
        var startPage by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var template by remember { mutableStateOf(GrabTemplate.AllFiles) }
        var customExtensions by remember { mutableStateOf("") }
        var depth by remember { mutableStateOf(1) }
        var maxPages by remember { mutableStateOf("30") }
        var sameSite by remember { mutableStateOf(true) }
        val found = remember { mutableStateListOf<GrabbedFile>() }
        val unchecked = remember { mutableStateListOf<String>() }
        var scannedCount by remember { mutableStateOf(0) }
        var currentPage by remember { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }

        fun startCrawl() {
            found.clear()
            unchecked.clear()
            scannedCount = 0
            val custom = customExtensions.split(',', ' ', ';').map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }.toSet()
            val wanted: (String) -> Boolean = { ext ->
                when (template) {
                    GrabTemplate.AllFiles -> ext.isNotEmpty() && (ext in knownFileExtensions || ext.length in 2..4)
                    GrabTemplate.Custom -> ext in custom
                    else -> ext in template.extensions
                }
            }
            val auth = if (username.isNotBlank()) {
                "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            } else null
            val url = startPage.trim().let { if (it.startsWith("http")) it else "https://$it" }
            running = true
            crawlJob = scope.launch {
                crawl(
                    startUrl = url,
                    wanted = wanted,
                    maxDepth = depth,
                    maxPages = maxPages.toIntOrNull()?.coerceIn(1, 500) ?: 30,
                    sameSiteOnly = sameSite,
                    authHeader = auth,
                    onPageScanned = { count, page ->
                        scope.launch { scannedCount = count; currentPage = page }
                    },
                    onFileFound = { file -> scope.launch { found.add(file) } },
                )
            }.also { job -> job.invokeOnCompletion { running = false } }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                when (step) {
                    1 -> tr("Step 1 of 3: the start page", "الخطوة 1 من 3: صفحة البداية")
                    2 -> tr("Step 2 of 3: what to grab", "الخطوة 2 من 3: هتجمع إيه")
                    else -> tr("Step 3 of 3: explore and choose files", "الخطوة 3 من 3: البحث واختيار الملفات")
                }.rememberString(),
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.lg,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (step) {
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabeledField(tr("Project name", "اسم المشروع").rememberString(), projectName, { projectName = it }, tr("optional", "اختياري").rememberString())
                        LabeledField(tr("Start page", "صفحة البداية").rememberString(), startPage, { startPage = it }, "https://example.com/files/")
                        Spacer(Modifier.height(6.dp))
                        WithContentAlpha(0.75f) {
                            Text(tr("Authorization (only if the site asks for a login)", "تسجيل الدخول (لو الموقع طالب بس)").rememberString(), fontSize = myTextSizes.sm)
                        }
                        LabeledField(tr("User name", "اسم المستخدم").rememberString(), username, { username = it }, "")
                        LabeledField(tr("Password", "كلمة السر").rememberString(), password, { password = it }, "")
                    }

                    2 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WithContentAlpha(0.75f) {
                            Text(tr("Template", "النوع").rememberString(), fontSize = myTextSizes.sm)
                        }
                        for (t in GrabTemplate.entries) {
                            Row(
                                Modifier.clickable { template = t }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(template == t, { if (it) template = t })
                                Spacer(Modifier.width(8.dp))
                                Text(t.title().rememberString())
                            }
                        }
                        if (template == GrabTemplate.Custom) {
                            MyTextField(customExtensions, { customExtensions = it }, "pdf, zip, mp4", Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Explore links up to level", "استكشف اللينكات لحد مستوى").rememberString())
                            Spacer(Modifier.width(8.dp))
                            for (level in 0..3) {
                                Row(Modifier.clickable { depth = level }.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(depth == level, { if (it) depth = level })
                                    Spacer(Modifier.width(4.dp))
                                    Text("$level")
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Most pages to explore", "أقصى عدد صفحات").rememberString())
                            Spacer(Modifier.width(8.dp))
                            MyTextField(maxPages, { v -> maxPages = v.filter { it.isDigit() }.take(3) }, "30", Modifier.width(70.dp))
                        }
                        Row(Modifier.clickable { sameSite = !sameSite }, verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(sameSite, { sameSite = it })
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Stay on the same site", "خليك في نفس الموقع").rememberString())
                        }
                    }

                    else -> Column {
                        WithContentAlpha(0.8f) {
                            Text(
                                if (running) {
                                    tr("Exploring... pages: $scannedCount, files found: ${found.size}", "بيدور... الصفحات: $scannedCount، الملفات: ${found.size}")
                                } else {
                                    tr("Done. Pages explored: $scannedCount, files found: ${found.size}", "خلص. الصفحات: $scannedCount، الملفات: ${found.size}")
                                }.rememberString(),
                                fontSize = myTextSizes.sm,
                            )
                        }
                        if (running) {
                            Text(currentPage, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = myTextSizes.xs)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(unchecked.isEmpty() && found.isNotEmpty(), { all -> unchecked.clear(); if (!all) unchecked.addAll(found.map { it.url }) })
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Select all", "اختيار الكل").rememberString(), fontSize = myTextSizes.sm)
                        }
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().background(myColors.surface / 0.5f).padding(4.dp)
                        ) {
                            items(found, key = { it.url }) { file ->
                                val checked = file.url !in unchecked
                                Row(
                                    Modifier.fillMaxWidth().clickable { if (checked) unchecked.add(file.url) else unchecked.remove(file.url) }
                                        .padding(vertical = 3.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CheckBox(checked, { if (it) unchecked.remove(file.url) else unchecked.add(file.url) })
                                    Spacer(Modifier.width(8.dp))
                                    Text(file.extension.uppercase(), Modifier.width(44.dp), fontSize = myTextSizes.xs, fontWeight = FontWeight.Bold)
                                    Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // IDM wizard buttons: < Back, Next >, Cancel
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step == 3 && running) {
                    ActionButton(tr("Stop", "إيقاف").rememberString(), onClick = { crawlJob?.cancel(); crawlJob = null })
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    tr("< Back", "< رجوع").rememberString(),
                    enabled = step > 1,
                    onClick = {
                        crawlJob?.cancel()
                        crawlJob = null
                        step--
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (step < 3) {
                    ActionButton(
                        tr("Next >", "التالي >").rememberString(),
                        enabled = startPage.isNotBlank(),
                        onClick = {
                            step++
                            if (step == 3) startCrawl()
                        },
                    )
                } else {
                    val chosen = found.filter { it.url !in unchecked }
                    ActionButton(
                        tr("Download ${chosen.size} files", "تحميل ${chosen.size} ملف").rememberString(),
                        enabled = chosen.isNotEmpty(),
                        onClick = {
                            crawlJob?.cancel()
                            onDownload(
                                chosen.map { it.url },
                                startPage.trim(),
                                username.takeIf { it.isNotBlank() },
                                password.takeIf { username.isNotBlank() },
                            )
                            SiteGrabber.close()
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                ActionButton(tr("Cancel", "إلغاء").rememberString(), onClick = closeWindow)
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(120.dp))
        MyTextField(value, onChange, placeholder, Modifier.weight(1f))
    }
}
