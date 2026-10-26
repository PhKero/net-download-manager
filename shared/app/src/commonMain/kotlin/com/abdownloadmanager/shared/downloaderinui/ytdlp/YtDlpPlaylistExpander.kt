package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.util.FilenameFixer
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpBinary
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpPlaylist
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import java.net.URI

object YtDlpPlaylistExpander {
    private val channelPath = Regex("^/(@[^/]+|c/[^/]+|channel/[^/]+|user/[^/]+)(/(videos|shorts|streams|playlists))?/?$")

    /**
     * Recognizes playlist-like links from the url alone (no network), so pasting one
     * in "New Download" can offer every video instead of a single one.
     */
    // collections on other sites; a single item on these sites has a longer / different path
    private val otherSitePatterns: List<Pair<String, Regex>> = listOf(
        "soundcloud.com" to Regex("^/[^/]+/(sets/[^/]+|tracks|albums|popular-tracks|likes|reposts)/?$"),
        "vimeo.com" to Regex("^/(showcase|album|channels|groups)/[^/]+(/videos)?/?$"),
        "dailymotion.com" to Regex("^/playlist/[^/]+/?$"),
        "tiktok.com" to Regex("^/@[^/]+/?$"),
        "bilibili.com" to Regex("^/(medialist|list|festival)/.+"),
        "bandcamp.com" to Regex("^/(album/[^/]+|music)/?$"),
        "twitch.tv" to Regex("^/[^/]+/(videos|clips)/?$"),
        "instagram.com" to Regex("^/[^/]+/(reels|tagged)/?$"),
    )

    fun isPlaylistLink(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return false
        val path = uri.path.orEmpty()
        if (host != "youtube.com" && !host.endsWith(".youtube.com")) {
            return otherSitePatterns.any { (site, pattern) ->
                (host == site || host.endsWith(".$site")) && pattern.matches(path)
            }
        }
        val list = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("list=") }?.removePrefix("list=")
        return when {
            path == "/playlist" -> list != null
            // "RD..." lists are auto generated radio mixes, endless; treat the link as one video
            list != null -> !list.startsWith("RD")
            else -> channelPath.matches(path)
        }
    }

    /** a watch?v=..&list=.. link becomes the playlist page so yt-dlp lists the whole thing */
    private fun normalize(link: String): String {
        val uri = runCatching { URI(link) }.getOrNull() ?: return link
        if (uri.host?.contains("youtube.com") != true) return link
        val list = uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("list=") }?.removePrefix("list=")
            ?: return link
        return "https://www.youtube.com/playlist?list=$list"
    }

    /** one add-dialog row per video, so the user can untick the ones they don't want */
    suspend fun expand(
        link: String,
        quality: String? = null,
        headers: Map<String, String>? = null,
        downloadPage: String? = null,
    ): List<AddDownloadCredentialsInUiProps> {
        val q = quality ?: YtDlpQuality.BEST
        val playlistLink = normalize(link)
        val playlist = YtDlpBinary.fetchPlaylist(
            YtDlpDownloadCredentials(link = playlistLink, headers = headers, downloadPage = downloadPage)
        )
        return rowsOf(playlist, playlistLink, q)
    }

    /** youtube single-video links are recognizable from the url, so they skip the extra lookup */
    fun isObviouslySingleVideo(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return false
        val path = uri.path.orEmpty()
        return host == "youtu.be" ||
                (host.endsWith("youtube.com") && (path == "/watch" || path.startsWith("/shorts/")) && !isPlaylistLink(link))
    }

    /**
     * For sites without a known playlist url pattern: asks yt-dlp what the page is.
     * Returns the rows when it is a list, null when it is a single video.
     */
    suspend fun expandIfAnyPlaylist(link: String, quality: String? = null): List<AddDownloadCredentialsInUiProps>? {
        if (isObviouslySingleVideo(link)) return null
        val result = YtDlpBinary.lookup(YtDlpDownloadCredentials(link = link))
        return (result as? YtDlpBinary.Lookup.Playlist)?.let {
            rowsOf(it.playlist, link, quality ?: YtDlpQuality.BEST)
        }
    }

    private fun rowsOf(
        playlist: YtDlpPlaylist,
        playlistLink: String,
        q: String,
    ): List<AddDownloadCredentialsInUiProps> {
        return playlist.entries.map { entry ->
            AddDownloadCredentialsInUiProps(
                credentials = YtDlpDownloadCredentials(
                    link = entry.url,
                    downloadPage = playlistLink,
                    quality = q,
                ),
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = entry.title?.let {
                        YtDlpQuality.fixFileName(FilenameFixer.fix(it) + ".x", q)
                    },
                )
            )
        }
    }
}
