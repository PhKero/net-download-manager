package ir.amirab.downloader.downloaditem.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * ffmpeg is big, so the Setup doesn't carry it: the first video that needs it (joining video + audio,
 * converting audio) downloads it once into the app's data folder.
 * The file is pinned to one version and checked against its SHA-256 before it is used.
 */
object FfmpegFetcher {
    /** the app sets this to a folder the user can write to (inside the app's data folder) */
    const val TOOLS_DIR_PROPERTY = "ndm.tools.dir"

    private const val URL =
        "https://github.com/GyanD/codexffmpeg/releases/download/9.0.2/ffmpeg-9.0.2-essentials_build.zip"
    private const val SHA256 = "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba"
    private val wanted = setOf("ffmpeg.exe", "ffprobe.exe")

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val lock = Mutex()

    fun toolsDir(): File? = System.getProperty(TOOLS_DIR_PROPERTY)?.let(::File)

    /**
     * Returns ffmpeg, downloading it first when it is missing.
     * [onProgress] gets 0..100 while downloading. Returns null when it can't be fetched;
     * yt-dlp then picks a format that needs no joining.
     */
    suspend fun ensure(onProgress: (Int?) -> Unit = {}): File? {
        YtDlpBinary.findFfmpeg()?.let { return it }
        if (!isWindows) return null
        val dir = toolsDir() ?: return null
        return lock.withLock {
            YtDlpBinary.findFfmpeg() ?: runCatching { download(dir, onProgress) }.getOrNull()
        }
    }

    private suspend fun download(dir: File, onProgress: (Int?) -> Unit): File? = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val zip = File(dir, "ffmpeg-download.zip.part")
        try {
            val connection = (URI(URL).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "NetDownloadManager")
            }
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                zip.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!hash.equals(SHA256, ignoreCase = true)) return@withContext null
            onProgress(null)
            ZipInputStream(zip.inputStream().buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && entry.name.contains("/bin/") && name in wanted) {
                        val tmp = File(dir, "$name.part")
                        tmp.outputStream().use { zis.copyTo(it) }
                        val target = File(dir, name)
                        target.delete()
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
            }
            File(dir, "ffmpeg.exe").takeIf { it.isFile }
        } finally {
            zip.delete()
        }
    }
}
