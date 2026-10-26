package ir.amirab.downloader.downloaditem.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Decides whether a link is a web page (so yt-dlp should look for a video on it)
 * or a plain file (so the normal http downloader is better).
 */
object WebPageProbe {
    private val fileExtensions = setOf(
        "zip", "rar", "7z", "gz", "tgz", "bz2", "xz", "tar", "iso", "img", "bin", "dmg",
        "exe", "msi", "msix", "apk", "xapk", "deb", "rpm", "appimage", "jar",
        "pdf", "epub", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "torrent",
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus",
        "mp4", "mkv", "avi", "mov", "webm", "wmv", "flv", "m4v", "ts", "m3u8", "mpd",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "psd", "ai",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun looksLikeFile(link: String): Boolean {
        val path = runCatching { URI(link).path }.getOrNull().orEmpty()
        val ext = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return ext in fileExtensions
    }

    /** a quick request that only reads the headers; any failure means "don't know" (false) */
    suspend fun isWebPage(link: String): Boolean = withContext(Dispatchers.IO) {
        if (looksLikeFile(link)) return@withContext false
        runCatching {
            val request = Request.Builder()
                .url(link)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36")
                // a GET limited to the first byte works on servers that reject HEAD
                .header("Range", "bytes=0-0")
                .build()
            client.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty().lowercase()
                val disposition = response.header("Content-Disposition").orEmpty().lowercase()
                !disposition.contains("attachment") && (type.contains("text/html") || type.contains("xhtml"))
            }
        }.getOrDefault(false)
    }
}
