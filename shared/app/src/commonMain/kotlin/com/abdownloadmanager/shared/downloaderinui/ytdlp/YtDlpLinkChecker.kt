package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkChecker
import com.abdownloadmanager.shared.util.FilenameFixer
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpBinary
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.flow.combineStateFlows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Asks yt-dlp what is on the page. The size and the file extension depend on the
 * selected [quality], so both are recomputed when it changes.
 */
class YtDlpLinkChecker(
    credentials: YtDlpDownloadCredentials,
    initialQuality: String = YtDlpQuality.BEST,
) : LinkChecker<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes>(
    initialCredentials = credentials
) {
    val quality = MutableStateFlow(initialQuality)
    private val info = MutableStateFlow<YtDlpResponseInfo?>(null)
    val currentInfo: StateFlow<YtDlpResponseInfo?> = info.asStateFlow()

    override val suggestedName: StateFlow<String?> = combineStateFlows(info, quality) { info, quality ->
        info?.title?.let { YtDlpQuality.fixFileName(FilenameFixer.fix(it) + ".x", quality) }
    }

    override val downloadSize: StateFlow<DownloadSize.Bytes?> = combineStateFlows(info, quality) { info, quality ->
        info?.estimatedSizeOf(quality)?.let(DownloadSize::Bytes)
    }

    val duration: StateFlow<Double?> = combineStateFlows(info, quality) { info, _ -> info?.duration }

    override fun infoUpdated(responseInfo: YtDlpResponseInfo?) {
        info.value = responseInfo
    }

    override suspend fun actualCheck(credentials: YtDlpDownloadCredentials): YtDlpResponseInfo {
        return YtDlpBinary.fetchInfo(credentials)
    }
}
