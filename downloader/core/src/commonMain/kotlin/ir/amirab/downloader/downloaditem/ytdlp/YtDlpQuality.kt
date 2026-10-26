package ir.amirab.downloader.downloaditem.ytdlp

/**
 * Qualities are stored as short tokens so saved downloads stay valid if the selector logic changes:
 * - [BEST]           best video + best audio
 * - "h<height>"      best video up to that height (e.g. "h1080")
 * - [AUDIO]          audio only, saved as m4a
 */
object YtDlpQuality {
    const val BEST = "best"
    const val AUDIO = "audio"

    fun ofHeight(height: Int) = "h$height"

    /** fixed list for the UI; a height acts as an upper limit, so every choice works for every video */
    val all: List<String> = listOf(BEST) +
            listOf(2160, 1440, 1080, 720, 480, 360, 240, 144).map(::ofHeight) +
            AUDIO

    fun heightOf(quality: String): Int? {
        return quality.takeIf { it.startsWith("h") }?.drop(1)?.toIntOrNull()
    }

    fun isAudioOnly(quality: String) = quality == AUDIO

    fun extensionOf(quality: String) = if (isAudioOnly(quality)) "m4a" else "mp4"

    /** the `-f` argument; mp4/m4a are preferred so merging into mp4 never needs re-encoding */
    fun toFormatSelector(quality: String): String {
        if (isAudioOnly(quality)) {
            return "ba[ext=m4a]/ba/b"
        }
        val h = heightOf(quality)?.let { "[height<=$it]" }.orEmpty()
        return "bv*$h[ext=mp4]+ba[ext=m4a]/bv*$h+ba/b$h/bv*+ba/b"
    }

    fun label(quality: String): String {
        return when {
            quality == BEST -> "Best quality"
            isAudioOnly(quality) -> "Audio only (m4a)"
            else -> "${heightOf(quality)}p"
        }
    }

    /** label for a quality of a known video, e.g. "Best (1080p)" */
    fun labelFor(quality: String, info: YtDlpResponseInfo): String {
        return when {
            quality == BEST -> info.actualHeightOf(quality)?.let { "Best (${it}p)" } ?: label(quality)
            else -> label(quality)
        }
    }

    /** replaces the extension of [name] with the one this quality produces */
    fun fixFileName(name: String, quality: String): String {
        val base = name.substringBeforeLast('.', name).ifBlank { "video" }
        return "$base.${extensionOf(quality)}"
    }
}
