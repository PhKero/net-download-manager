package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Locates and runs the external yt-dlp / ffmpeg executables.
 * Lookup order: env var (ABDM_YTDLP / ABDM_FFMPEG), a "tools" folder next to the app, then PATH.
 */
object YtDlpBinary {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun findYtDlp(): File? = find("ABDM_YTDLP", "yt-dlp")
    fun findFfmpeg(): File? = find("ABDM_FFMPEG", "ffmpeg")

    fun requireYtDlp(): File {
        return findYtDlp() ?: throw YtDlpException(
            "yt-dlp was not found. Install it (winget install yt-dlp) or put yt-dlp.exe in the app's tools folder"
        )
    }

    /** shared lookup for bundled helper programs (yt-dlp, ffmpeg, aria2c) */
    fun findTool(envName: String, exeName: String): File? = find(envName, exeName)

    private fun find(envName: String, exeName: String): File? {
        val fileName = if (isWindows) "$exeName.exe" else exeName
        System.getenv(envName)?.let(::File)?.takeIf { it.isFile }?.let { return it }
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File)
        listOfNotNull(resources, appDirectory()).forEach { dir ->
            listOf(dir.resolve("tools").resolve(fileName), dir.resolve(fileName))
                .firstOrNull { it.isFile }
                ?.let { return it }
        }
        return System.getenv("PATH").orEmpty()
            .split(File.pathSeparatorChar)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it.trim('"'), fileName) }
            .firstOrNull { it.isFile }
    }

    private fun appDirectory(): File? {
        return runCatching {
            System.getProperty("compose.application.resources.dir")?.let { File(it).parentFile }
                ?: ProcessHandle.current().info().command().orElse(null)?.let { File(it).parentFile }
        }.getOrNull()
    }

    /** arguments shared by info lookup and download */
    fun commonArgs(credentials: IHttpBasedDownloadCredentials, useBrowserCookies: Boolean): List<String> {
        return buildList {
            add("--no-playlist")
            add("--no-colors")
            add("--no-warnings")
            add("--encoding"); add("utf-8")
            credentials.userAgent?.let { add("--user-agent"); add(it) }
            credentials.downloadPage?.let { add("--referer"); add(it) }
            credentials.headers?.forEach { (k, v) ->
                // these are managed by yt-dlp itself per request
                if (!k.equals("range", true) && !k.equals("user-agent", true)) {
                    add("--add-header"); add("$k:$v")
                }
            }
            credentials.username?.let { add("--username"); add(it) }
            credentials.password?.let { add("--password"); add(it) }
            if (useBrowserCookies) {
                add("--cookies-from-browser"); add("firefox")
            }
        }
    }

    fun newProcessBuilder(args: List<String>): ProcessBuilder {
        return ProcessBuilder(listOf(requireYtDlp().absolutePath) + args).apply {
            environment()["PYTHONIOENCODING"] = "utf-8"
            environment()["PYTHONUTF8"] = "1"
        }
    }

    /** sites that refuse anonymous requests answer with one of these; retrying with browser cookies fixes it */
    fun needsCookies(error: String): Boolean {
        val e = error.lowercase()
        return listOf("sign in", "login required", "confirm you", "cookies", "private video", "age").any { it in e }
    }

    suspend fun fetchInfo(credentials: IHttpBasedDownloadCredentials): YtDlpResponseInfo {
        return try {
            fetchInfo(credentials, useBrowserCookies = false)
        } catch (e: YtDlpException) {
            if (needsCookies(e.message.orEmpty())) fetchInfo(credentials, useBrowserCookies = true) else throw e
        }
    }

    private suspend fun fetchInfo(
        credentials: IHttpBasedDownloadCredentials,
        useBrowserCookies: Boolean,
    ): YtDlpResponseInfo = withContext(Dispatchers.IO) {
        val args = commonArgs(credentials, useBrowserCookies) + listOf("-J", "--", credentials.link)
        val process = newProcessBuilder(args).start()
        try {
            coroutineScope {
                val stderr = async { process.errorStream.bufferedReader(Charsets.UTF_8).readText() }
                val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val exit = runInterruptible { process.waitFor() }
                if (exit != 0) {
                    throw YtDlpException(lastError(stderr.await()) ?: "yt-dlp failed with code $exit")
                }
                YtDlpResponseInfo.parse(stdout)
            }
        } finally {
            if (process.isAlive) killTree(process)
        }
    }

    /** lists the videos of a playlist / channel page without resolving each one (fast even for big lists) */
    suspend fun fetchPlaylist(credentials: IHttpBasedDownloadCredentials): YtDlpPlaylist = withContext(Dispatchers.IO) {
        val args = commonArgs(credentials, useBrowserCookies = false).filter { it != "--no-playlist" } +
                listOf("--yes-playlist", "--flat-playlist", "-J", "--", credentials.link)
        val process = newProcessBuilder(args).start()
        try {
            coroutineScope {
                val stderr = async { process.errorStream.bufferedReader(Charsets.UTF_8).readText() }
                val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val exit = runInterruptible { process.waitFor() }
                if (exit != 0) {
                    throw YtDlpException(lastError(stderr.await()) ?: "yt-dlp failed with code $exit")
                }
                YtDlpPlaylist.parse(stdout)
            }
        } finally {
            if (process.isAlive) killTree(process)
        }
    }

    sealed interface Lookup {
        data class Video(val info: YtDlpResponseInfo) : Lookup
        data class Playlist(val playlist: YtDlpPlaylist) : Lookup
    }

    /**
     * One call that tells whether a page is a single video or a list:
     * `--flat-playlist` keeps lists fast and still returns full formats for a single video.
     */
    suspend fun lookup(credentials: IHttpBasedDownloadCredentials): Lookup {
        return try {
            lookup(credentials, useBrowserCookies = false)
        } catch (e: YtDlpException) {
            if (needsCookies(e.message.orEmpty())) lookup(credentials, useBrowserCookies = true) else throw e
        }
    }

    private suspend fun lookup(
        credentials: IHttpBasedDownloadCredentials,
        useBrowserCookies: Boolean,
    ): Lookup = withContext(Dispatchers.IO) {
        val args = commonArgs(credentials, useBrowserCookies) + listOf("--flat-playlist", "-J", "--", credentials.link)
        val process = newProcessBuilder(args).start()
        try {
            coroutineScope {
                val stderr = async { process.errorStream.bufferedReader(Charsets.UTF_8).readText() }
                val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val exit = runInterruptible { process.waitFor() }
                if (exit != 0) {
                    throw YtDlpException(lastError(stderr.await()) ?: "yt-dlp failed with code $exit")
                }
                if (YtDlpPlaylist.isPlaylistJson(stdout)) {
                    Lookup.Playlist(YtDlpPlaylist.parse(stdout))
                } else {
                    Lookup.Video(YtDlpResponseInfo.parse(stdout))
                }
            }
        } finally {
            if (process.isAlive) killTree(process)
        }
    }

    fun lastError(output: String): String? {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (lines.lastOrNull { it.startsWith("ERROR") } ?: lines.lastOrNull())
            ?.removePrefix("ERROR:")?.trim()
    }

    fun killTree(process: Process) {
        runCatching {
            process.toHandle().descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }
}
