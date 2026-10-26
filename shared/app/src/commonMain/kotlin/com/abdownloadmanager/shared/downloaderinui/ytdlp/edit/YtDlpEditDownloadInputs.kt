package com.abdownloadmanager.shared.downloaderinui.ytdlp.edit

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.ytdlp.YtDlpItemToCredentialMapper
import com.abdownloadmanager.shared.downloaderinui.ytdlp.YtDlpLinkChecker
import com.abdownloadmanager.shared.downloaderinui.ytdlp.createYtDlpQualityConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.IntConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.StringConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.ToggleableSpeedLimitConfigurable
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.ThreadCountLimitation
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YtDlpEditDownloadInputs(
    currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    mapper: YtDlpItemToCredentialMapper,
    conflictDetector: DownloadConflictDetector,
    linkCheckerFactory: LinkCheckerFactory<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    editDownloadCheckerFactory: EditDownloadCheckerFactory<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    scope: CoroutineScope,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : EditDownloadInputs<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpItemToCredentialMapper
        >(
    currentDownloadItem,
    editedDownloadItem,
    mapper = mapper,
    scope = scope,
    conflictDetector = conflictDetector,
    linkCheckerFactory = linkCheckerFactory,
    editDownloadCheckerFactory = editDownloadCheckerFactory,
) {
    private val quality = editedDownloadItem.mapTwoWayStateFlow(
        map = { it.quality },
        unMap = { copy(quality = it, name = YtDlpQuality.fixFileName(name, it)) }
    )

    override val configurableList = listOf(
        createYtDlpQualityConfigurable(
            quality = quality,
            info = linkChecker.currentInfo,
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        ),
        ToggleableSpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = { it.speedLimit },
                unMap = { copy(speedLimit = it) }
            ),
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(it, sizeAndSpeedUnitProvider.speedUnit.value).asStringSource()
            }
        ),
        IntConfigurable(
            Res.string.settings_download_thread_count.asStringSource(),
            Res.string.settings_download_thread_count_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(
                map = { it.preferredConnectionCount ?: 0 },
                unMap = { copy(preferredConnectionCount = it.takeIf { it >= 1 }) }
            ),
            range = 0..ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT,
            describe = {
                if (it == 0) Res.string.use_global_settings.asStringSource()
                else Res.string.download_item_settings_thread_count_describe
                    .asStringSourceWithARgs(
                        Res.string.download_item_settings_thread_count_describe_createArgs(
                            count = it.toString()
                        )
                    )
            }
        ),
        StringConfigurable(
            Res.string.username.asStringSource(),
            Res.string.download_item_settings_username_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.username.orEmpty() },
                unMap = { copy(username = it.takeIf { it.isNotEmpty() }) }
            ),
            describe = { "".asStringSource() }
        ),
        StringConfigurable(
            Res.string.password.asStringSource(),
            Res.string.download_item_settings_password_description.asStringSource(),
            backedBy = credentials.mapTwoWayStateFlow(
                map = { it.password.orEmpty() },
                unMap = { copy(password = it.takeIf { it.isNotEmpty() }) }
            ),
            describe = { "".asStringSource() }
        ),
    )

    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override fun applyEditedItemTo(item: YtDlpDownloadItem) {
        val edited = editedDownloadItem.value
        item.link = edited.link
        item.headers = edited.headers
        item.username = edited.username
        item.password = edited.password
        item.downloadPage = edited.downloadPage
        item.userAgent = edited.userAgent
        item.folder = edited.folder
        item.name = edited.name
        item.preferredConnectionCount = edited.preferredConnectionCount
        item.speedLimit = edited.speedLimit
        item.fileChecksum = edited.fileChecksum
        if (item.quality != edited.quality) {
            item.quality = edited.quality
            // the old partial streams belong to another quality
            item.downloadedBytes = 0
            item.contentLength = -1
        }
    }

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }
}
