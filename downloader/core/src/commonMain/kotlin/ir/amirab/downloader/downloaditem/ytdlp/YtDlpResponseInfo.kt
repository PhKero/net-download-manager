package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.connection.IResponseInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

data class YtDlpFormat(
    val id: String,
    val ext: String?,
    val height: Int?,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val size: Long?,
)

/** one entry of the quality list shown to the user */
data class YtDlpQualityOption(
    val quality: String,
    val estimatedSize: Long?,
)

/** result of `yt-dlp -J <page>` */
data class YtDlpResponseInfo(
    val title: String?,
    val duration: Double?,
    val isLive: Boolean,
    val formats: List<YtDlpFormat>,
) : IResponseInfo {
    override val isSuccessFul: Boolean get() = true
    override val requiresAuth: Boolean get() = false
    override val requireBasicAuth: Boolean get() = false
    override val resumeSupport: Boolean get() = !isLive
    override val isWebPage: Boolean get() = false
    override val unsuccessFullException: Throwable? get() = null

    private val bestAudioSize: Long? by lazy {
        formats.filter { it.hasAudio && !it.hasVideo }.mapNotNull { it.size }.maxOrNull()
    }

    val qualityOptions: List<YtDlpQualityOption> by lazy {
        val heights = formats.filter { it.hasVideo }.mapNotNull { it.height }.distinct().sortedDescending()
        buildList {
            add(YtDlpQualityOption(YtDlpQuality.BEST, sizeForHeight(heights.firstOrNull())))
            heights.forEach { add(YtDlpQualityOption(YtDlpQuality.ofHeight(it), sizeForHeight(it))) }
            if (formats.any { it.hasAudio }) {
                add(YtDlpQualityOption(YtDlpQuality.AUDIO, bestAudioSize))
            }
        }
    }

    private fun sizeForHeight(height: Int?): Long? {
        if (height == null) return formats.mapNotNull { it.size }.maxOrNull()
        val sameHeight = formats.filter { it.hasVideo && it.height == height }
        // prefer mp4 like the format selector does
        val video = (sameHeight.filter { it.ext == "mp4" }.ifEmpty { sameHeight })
            .filter { it.size != null }
            .maxByOrNull { it.size!! } ?: return null
        return if (video.hasAudio) video.size else video.size!! + (bestAudioSize ?: 0)
    }

    fun estimatedSizeOf(quality: String): Long? {
        val maxHeight = YtDlpQuality.heightOf(quality)
            ?: return qualityOptions.find { it.quality == quality }?.estimatedSize
        // same rule as the format selector: the best height that does not exceed the limit
        val height = formats.filter { it.hasVideo }.mapNotNull { it.height }.filter { it <= maxHeight }.maxOrNull()
            ?: formats.filter { it.hasVideo }.mapNotNull { it.height }.minOrNull()
        return sizeForHeight(height)
    }

    /** the real height a quality will give for this video, used to label the choice */
    fun actualHeightOf(quality: String): Int? {
        val heights = formats.filter { it.hasVideo }.mapNotNull { it.height }
        val maxHeight = YtDlpQuality.heightOf(quality) ?: return heights.maxOrNull()
        return heights.filter { it <= maxHeight }.maxOrNull() ?: heights.minOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(output: String): YtDlpResponseInfo {
            // yt-dlp prints one json object per line; with --no-playlist we only need the first
            val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
                ?: throw YtDlpException("yt-dlp returned no video information")
            val root = json.parseToJsonElement(line).jsonObject
            if (root.string("_type") == "playlist") {
                throw YtDlpException("This link is a playlist. Add it from \"New Download\" or the browser's Playlist button to get all of its videos")
            }
            val formats = (root["formats"] as? JsonArray).orEmpty().mapNotNull { element ->
                val f = element as? JsonObject ?: return@mapNotNull null
                val vcodec = f.string("vcodec")
                val acodec = f.string("acodec")
                val height = f.int("height")
                YtDlpFormat(
                    id = f.string("format_id") ?: return@mapNotNull null,
                    ext = f.string("ext"),
                    height = height,
                    // "none" means the stream is missing; null means unknown, so trust the height
                    hasVideo = vcodec != null && vcodec != "none" || vcodec == null && height != null,
                    hasAudio = acodec != null && acodec != "none",
                    size = f.long("filesize") ?: f.long("filesize_approx"),
                )
            }.filter {
                // storyboards / thumbnails are listed as formats too
                it.ext != "mhtml"
            }
            return YtDlpResponseInfo(
                title = root.string("title"),
                duration = (root["duration"] as? JsonPrimitive)?.doubleOrNull,
                isLive = root.string("is_live") == "true" || root.string("live_status") == "is_live",
                formats = formats,
            )
        }

        private fun JsonObject.string(key: String): String? {
            val p = this[key] as? JsonPrimitive ?: return null
            return if (p.isString) p.content else p.content.takeIf { it != "null" }
        }

        private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
        private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    }
}

class YtDlpException(message: String, cause: Throwable? = null) : Exception(message, cause)
