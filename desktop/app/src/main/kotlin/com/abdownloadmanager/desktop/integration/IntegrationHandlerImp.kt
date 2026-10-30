package com.abdownloadmanager.desktop.integration

import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.integration.IntegrationHandler
import com.abdownloadmanager.integration.model.*
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUiRegistry
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.pages.adddownload.SilentImportOptions
import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.hls.HLSDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpBinary
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import com.abdownloadmanager.shared.downloaderinui.ytdlp.YtDlpPlaylistExpander
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.OnDuplicateStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class IntegrationHandlerImp : IntegrationHandler, KoinComponent {
    val appComponent by inject<AppComponent>()
    val downloadSystem by inject<DownloadSystem>()
    val queueManager by inject<QueueManager>()
    val appSettings by inject<AppRepository>()
    private val downloaderInUiRegistry by inject<DownloaderInUiRegistry>()

    override suspend fun addDownloadByGui(
        request: AddDownloadsFromIntegration
    ) {
        val list = request.items
        val options = request.options
        appComponent.externalCredentialComingIntoApp(
            list.flatMap {
                if (it is YtDlpDownloadCredentialsFromIntegration && it.playlist) {
                    expandPlaylist(it)
                } else {
                    listOf(convertToDownloadSystemCredentials(it))
                }
            },
            options = ImportOptions(
                silentImport = if (options.silentAdd) {
                    SilentImportOptions(
                        silentDownload = options.silentStart
                    )
                } else null
            )
        )
    }

    override fun listQueues(): List<ApiQueueModel> {
        return queueManager.getAll().map { downloadQueue ->
            val queueModel = downloadQueue.getQueueModel()
            ApiQueueModel(id = queueModel.id, name = queueModel.name)
        }
    }

    override suspend fun addDownload(task: NewDownloadTask): Long {
        val addDownloaderInUiProps = convertToDownloadSystemCredentials(task.downloadSource)
        val downloaderInUi = downloaderInUiRegistry.getDownloaderOf(
            addDownloaderInUiProps.credentials
        ) ?: error("Downloader for ${addDownloaderInUiProps.credentials::class.qualifiedName} not found")
        val downloadItem = downloaderInUi.createBareDownloadItem(
            addDownloaderInUiProps.credentials,
            basicDownloadItem = BasicDownloadItem(
                folder = task.folder ?: appSettings.saveLocation.value,
                // fixed + shortened: a link that ends with a long token must not become an invalid file name
                name = com.abdownloadmanager.shared.util.FilenameFixer.fix(
                    task.name ?: addDownloaderInUiProps.extraConfig.suggestedName
                    ?: task.downloadSource.link.substringAfterLast("/").substringBefore('?')
                ),
            ),
        )
        val id = downloadSystem.addDownload(
            newDownload = NewDownloadItemProps(
                downloadItem = downloadItem,
                onDuplicateStrategy = OnDuplicateStrategy.default(),
                extraConfig = null,
                context = EmptyContext,
            ),
            queueId = task.queueId,
            categoryId = task.categoryId,
        )
        val queueId = task.queueId
        // either start the queue or manually start the download
        // both of them at the same time is not good idea
        if (task.startQueue && queueId != null) {
            val queue = queueManager.getQueue(queueId)
            queue.start()
        } else {
            if (task.startDownload) {
                downloadSystem.userManualResume(id)
            }
        }
        return id
    }

    // the bar asks again each time the menu opens; yt-dlp takes seconds, so remember recent answers
    private val videoInfoCache = object : LinkedHashMap<String, Pair<Long, VideoInfoResponse>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, VideoInfoResponse>>?) = size > 50
    }

    override suspend fun videoInfo(request: VideoInfoRequest): VideoInfoResponse {
        val cacheTime = 10 * 60 * 1000L
        synchronized(videoInfoCache) {
            videoInfoCache[request.link]?.let { (time, response) ->
                if (System.currentTimeMillis() - time < cacheTime) return response
            }
        }
        val credentials = YtDlpDownloadCredentials(link = request.link, downloadPage = request.downloadPage)
        val response = try {
            when (val result = YtDlpBinary.lookup(credentials)) {
                is YtDlpBinary.Lookup.Video -> VideoInfoResponse(
                    type = "video",
                    title = result.info.title,
                    duration = result.info.duration,
                    qualities = result.info.qualityOptions.map {
                        VideoQualityModel(
                            quality = it.quality,
                            label = YtDlpQuality.labelFor(it.quality, result.info),
                            size = it.estimatedSize,
                        )
                    },
                )

                is YtDlpBinary.Lookup.Playlist -> VideoInfoResponse(
                    type = "playlist",
                    title = result.playlist.title,
                    count = result.playlist.entries.size,
                )
            }
        } catch (e: Exception) {
            // errors are not cached, the next try may succeed
            return VideoInfoResponse(type = "error", error = e.message ?: "yt-dlp failed")
        }
        synchronized(videoInfoCache) {
            videoInfoCache[request.link] = System.currentTimeMillis() to response
        }
        return response
    }

    private suspend fun expandPlaylist(item: YtDlpDownloadCredentialsFromIntegration): List<AddDownloadCredentialsInUiProps> {
        return YtDlpPlaylistExpander.expand(
            link = item.link,
            quality = item.quality,
            headers = item.headers,
            downloadPage = item.downloadPage,
        )
    }

    companion object {
        private fun convertToDownloadSystemCredentials(it: IDownloadCredentialsFromIntegration): AddDownloadCredentialsInUiProps {
            val credentials = when (it) {
                is HttpDownloadCredentialsFromIntegration -> {
                    HttpDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }

                is HLSDownloadCredentialsFromIntegration -> {
                    HLSDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                    )
                }

                is YtDlpDownloadCredentialsFromIntegration -> {
                    YtDlpDownloadCredentials(
                        link = it.link,
                        headers = it.headers,
                        downloadPage = it.downloadPage,
                        quality = it.quality,
                    )
                }
            }
            return AddDownloadCredentialsInUiProps(
                credentials = credentials,
                extraConfig = AddDownloadCredentialsInUiProps.Configs(
                    suggestedName = it.suggestedName,
                )
            )
        }
    }
}
