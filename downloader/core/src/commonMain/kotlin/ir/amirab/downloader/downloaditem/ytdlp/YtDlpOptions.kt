package ir.amirab.downloader.downloaditem.ytdlp

import kotlin.concurrent.Volatile

/** global yt-dlp preferences, set by the app from its settings */
object YtDlpOptions {
    /** embed subtitles into the video file */
    @Volatile
    var downloadSubtitles: Boolean = false

    /** comma separated yt-dlp language patterns, e.g. "en.*,ar.*" */
    @Volatile
    var subtitleLanguages: String = "en.*,ar.*"

    /** save subtitles as .srt files next to the video instead of embedding them */
    @Volatile
    var subtitlesAsFile: Boolean = false
}
