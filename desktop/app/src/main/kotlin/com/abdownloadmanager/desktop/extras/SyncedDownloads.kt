package com.abdownloadmanager.desktop.extras

import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * IDM "periodic synchronization", per download: a finished download marked "Keep updated" is
 * checked once a day; when the server's copy changed (ETag, Last-Modified or size), the new
 * version is downloaded over the old one. Kept in ~/.abdm/synced-downloads.json.
 */
object SyncedDownloads {
    @Serializable
    data class State(
        val lastCheck: Long = 0,
        val etag: String? = null,
        val lastModified: String? = null,
        val length: Long? = null,
    )

    // next to the download list: the ids belong to that list (a portable copy has its own)
    private val file = com.abdownloadmanager.desktop.utils.AppInfo.definedPaths.downloadDbDir.toFile().resolve("synced-downloads.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = MapSerializer(Long.serializer(), State.serializer())
    private const val DAY = 24 * 60 * 60 * 1000L

    private val _all = MutableStateFlow(
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
    )
    val all: StateFlow<Map<Long, State>> = _all

    fun isSynced(id: Long) = id in _all.value

    fun toggle(id: Long) {
        // a new entry has lastCheck 0: the first check right away records how the file is now
        _all.update { if (id in it) it - id else it + (id to State()) }
        save()
    }

    private fun put(id: Long, state: State) {
        _all.update { if (id in it) it + (id to state) else it }
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(serializer, _all.value))
        }
    }

    fun start(
        scope: CoroutineScope,
        downloadSystem: DownloadSystem,
        onUpdated: (name: String) -> Unit,
    ) {
        // a background thread: a delay() loop on the Main (UI) dispatcher never woke up here
        scope.launch(Dispatchers.Default) {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()
            while (isActive) {
                for ((id, state) in _all.value) {
                    if (System.currentTimeMillis() - state.lastCheck < DAY && state.lastCheck != 0L) continue
                    runCatching { check(id, state, client, downloadSystem, onUpdated) }
                }
                delay(10.minutes)
            }
        }
    }

    private suspend fun check(
        id: Long,
        state: State,
        client: HttpClient,
        downloadSystem: DownloadSystem,
        onUpdated: (String) -> Unit,
    ) {
        val item = downloadSystem.getDownloadItemById(id)
        if (item == null) {
            // deleted from the list
            _all.update { it - id }
            save()
            return
        }
        if (item.status != DownloadStatus.Completed) return
        // the same headers the download used (some servers need its cookies / referer)
        val headers = ((item as? IHttpBasedDownloadCredentials)?.headers).orEmpty()
        val request = HttpRequest.newBuilder(URI(item.link))
            .timeout(Duration.ofSeconds(30))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .apply {
                headers.forEach { (k, v) -> runCatching { header(k, v) } }
                if (headers.keys.none { it.equals("User-Agent", true) }) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Net Download Manager")
                }
            }
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) return
        val h = response.headers()
        val now = State(
            lastCheck = System.currentTimeMillis(),
            etag = h.firstValue("ETag").orElse(null),
            lastModified = h.firstValue("Last-Modified").orElse(null),
            length = h.firstValue("Content-Length").orElse(null)?.toLongOrNull(),
        )
        val firstCheck = state.lastCheck == 0L
        val changed = !firstCheck && (
            (state.etag != null && now.etag != null && state.etag != now.etag) ||
                (state.lastModified != null && now.lastModified != null && state.lastModified != now.lastModified) ||
                (state.length != null && now.length != null && state.length != now.length)
            )
        put(id, now)
        if (changed) {
            downloadSystem.reset(id)
            downloadSystem.userManualResume(id)
            onUpdated(item.name)
        }
    }
}
