package com.abdownloadmanager.shared.downloaderinui.ytdlp

import ir.amirab.util.compose.tr
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.ytdlp.add.YtDlpNewDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.ytdlp.add.YtDlpNewDownloadUIChecker
import com.abdownloadmanager.shared.downloaderinui.ytdlp.edit.YtDlpEditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.ytdlp.edit.YtDlpEditDownloadInputs
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.IYtDlpCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadJob
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloader
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.downloader.monitor.ProcessingDownloadItemFactoryInputs
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.monitor.RangeBasedProcessingDownloadItemState
import ir.amirab.downloader.monitor.UiRangedPart
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.util.HttpUrlUtils
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class YtDlpDownloaderInUi(
    downloader: YtDlpDownloader,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : DownloaderInUi<
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpDownloadItem,
        YtDlpNewDownloadInputs,
        YtDlpEditDownloadInputs,
        YtDlpItemToCredentialMapper,
        YtDlpDownloadJob,
        YtDlpDownloader
        >(downloader) {

    override fun newDownloadUiChecker(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ): YtDlpNewDownloadUIChecker {
        return YtDlpNewDownloadUIChecker(
            initCredentials = initialCredentials,
            linkCheckerFactory = this,
            initialFolder = initialFolder,
            initialName = initialName,
            downloadSystem = downloadSystem,
            scope = scope,
        )
    }

    override fun acceptDownloadCredentials(item: IDownloadCredentials): Boolean {
        return item is IYtDlpCredentials
    }

    /** only well known video sites are picked automatically; any other page can still be chosen manually */
    override fun supportsThisLink(link: String): Boolean = isKnownVideoSite(link)

    override fun createMinimumCredentials(link: String): YtDlpDownloadCredentials {
        return YtDlpDownloadCredentials(link = link)
    }

    override fun createBareDownloadItem(
        credentials: YtDlpDownloadCredentials,
        basicDownloadItem: BasicDownloadItem
    ): YtDlpDownloadItem {
        return YtDlpDownloadItem.createWithCredentials(
            id = -1,
            credentials = credentials,
            folder = basicDownloadItem.folder,
            name = basicDownloadItem.name,
            contentLength = basicDownloadItem.contentLength,
            preferredConnectionCount = basicDownloadItem.preferredConnectionCount,
            speedLimit = basicDownloadItem.speedLimit,
            fileChecksum = basicDownloadItem.fileChecksum,
            quality = credentials.quality ?: YtDlpQuality.BEST,
        )
    }

    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<YtDlpDownloadJob>
    ): ProcessingDownloadItemState {
        val job = props.downloadJob
        val item = job.downloadItem
        val downloaded = job.getDownloadedSize()
        val total = item.contentLength
        val status = job.status.value
        val activeStatus = when (status) {
            is DownloadJobStatus.Finished -> PartDownloadStatus.Completed
            is DownloadJobStatus.IsActive -> PartDownloadStatus.ReceivingData
            else -> PartDownloadStatus.IDLE
        }
        // yt-dlp downloads each stream of the video (video, audio) over several connections (-N),
        // but only reports progress per stream, so the window shows one real row per stream
        val streams = job.getStreams()
        val streamsTotal = streams.sumOf { it.total ?: it.done }.coerceAtLeast(1)
        val parts = if (streams.isEmpty()) {
            listOf(
                UiRangedPart(
                    id = 0,
                    status = activeStatus,
                    howMuchProceed = downloaded,
                    percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else null,
                    length = total.takeIf { it > 0 },
                    partSpace = 1f,
                )
            )
        } else {
            streams.mapIndexed { index, stream ->
                val length = stream.total
                UiRangedPart(
                    id = index.toLong(),
                    status = if (stream.finished) PartDownloadStatus.Completed else activeStatus,
                    howMuchProceed = stream.done,
                    percent = length?.takeIf { it > 0 }?.let { ((stream.done * 100) / it).toInt().coerceIn(0, 100) },
                    length = length,
                    partSpace = (length ?: stream.done).toFloat() / streamsTotal,
                )
            }
        }
        return RangeBasedProcessingDownloadItemState(
            id = job.id,
            folder = item.folder,
            name = item.name,
            downloadLink = item.link,
            contentLength = total,
            saveLocation = item.name,
            dateAdded = item.dateAdded,
            startTime = item.startTime ?: 0,
            completeTime = item.completeTime ?: 0,
            status = status,
            speed = props.speed,
            parts = parts,
            supportResume = true,
            isWaiting = props.isWaiting,
        )
    }

    override val name: StringSource = tr("Video (yt-dlp)", "فيديو (yt-dlp)")

    override fun createLinkChecker(initialCredentials: YtDlpDownloadCredentials): YtDlpLinkChecker {
        return YtDlpLinkChecker(
            credentials = initialCredentials,
            initialQuality = initialCredentials.quality ?: YtDlpQuality.BEST,
        )
    }

    override fun createEditDownloadChecker(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        linkChecker: YtDlpLinkChecker,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ): EditDownloadChecker<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker> {
        return YtDlpEditDownloadChecker(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            linkChecker = linkChecker,
            conflictDetector = conflictDetector,
            scope = scope,
        )
    }

    override fun createNewDownloadInputs(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ): YtDlpNewDownloadInputs {
        return YtDlpNewDownloadInputs(
            newDownloadUiChecker(
                initialCredentials = initialCredentials,
                initialFolder = initialFolder,
                initialName = initialName,
                downloadSystem = downloadSystem,
                scope = scope,
            ),
            sizeAndSpeedUnitProvider,
            scope,
        )
    }

    override fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ): YtDlpEditDownloadInputs {
        return YtDlpEditDownloadInputs(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            mapper = YtDlpItemToCredentialMapper(),
            conflictDetector = conflictDetector,
            scope = scope,
            linkCheckerFactory = this,
            editDownloadCheckerFactory = this,
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        )
    }

    companion object {
        fun isKnownVideoSite(link: String): Boolean {
            if (!HttpUrlUtils.isValidUrl(link)) return false
            val host = runCatching { java.net.URI(link).host }.getOrNull()?.lowercase()?.removePrefix("www.")
                ?: return false
            return VIDEO_SITES.any { host == it || host.endsWith(".$it") }
        }

        val VIDEO_SITES = listOf(
            "youtube.com", "youtu.be", "youtube-nocookie.com",
            "facebook.com", "fb.watch", "instagram.com", "tiktok.com",
            "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "dai.ly",
            "twitch.tv", "reddit.com", "soundcloud.com", "bilibili.com",
            "rumble.com", "odysee.com", "streamable.com", "linkedin.com", "pinterest.com",
        )
    }
}
