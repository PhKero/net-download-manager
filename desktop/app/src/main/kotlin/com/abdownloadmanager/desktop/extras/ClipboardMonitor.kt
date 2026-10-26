package com.abdownloadmanager.desktop.extras

import com.abdownloadmanager.shared.downloaderinui.ytdlp.YtDlpDownloaderInUi
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.extractors.linkextractor.StringUrlExtractor
import ir.amirab.downloader.downloaditem.ytdlp.WebPageProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Like IDM: copying a link to a file or a video page offers to download it.
 * Ordinary page links are ignored so copying text while browsing doesn't pop dialogs up.
 */
class ClipboardMonitor(
    private val enabled: StateFlow<Boolean>,
    private val onLinkCopied: (String) -> Unit,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // whatever is in the clipboard when the app starts was not copied for us
            var last = runCatching { ClipboardUtil.read() }.getOrNull()
            while (isActive) {
                delay(1.seconds)
                val text = runCatching { ClipboardUtil.read() }.getOrNull() ?: continue
                if (text == last) continue
                last = text
                if (!enabled.value || text.length > 2048) continue
                val link = StringUrlExtractor.extract(text).singleOrNull() ?: continue
                if (isDownloadable(link)) {
                    onLinkCopied(link)
                }
            }
        }
    }

    private fun isDownloadable(link: String): Boolean {
        return WebPageProbe.looksLikeFile(link) || YtDlpDownloaderInUi.isKnownVideoSite(link)
    }
}
