package com.abdownloadmanager.shared.downloaderinui.torrent

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.CredentialAndItemMapper
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.LinkChecker
import com.abdownloadmanager.shared.downloaderinui.LinkCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadInputs
import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadUiChecker
import com.abdownloadmanager.shared.downloaderinui.edit.CanEditDownloadResult
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadInputs
import com.abdownloadmanager.shared.ui.configurable.item.ToggleableSpeedLimitConfigurable
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.FilenameFixer
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsItem
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.torrent.TorrentDownloadCredentials
import ir.amirab.downloader.downloaditem.torrent.TorrentDownloadItem
import ir.amirab.downloader.downloaditem.torrent.TorrentDownloadJob
import ir.amirab.downloader.downloaditem.torrent.TorrentDownloader
import ir.amirab.downloader.downloaditem.torrent.TorrentLinks
import ir.amirab.downloader.monitor.ProcessingDownloadItemFactoryInputs
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.monitor.RangeBasedProcessingDownloadItemState
import ir.amirab.downloader.monitor.UiRangedPart
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.util.FileNameValidator
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.flow.combineStateFlows
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** torrents have nothing to ask a server before starting; the size is known once peers send the metadata */
object TorrentResponseInfo : IResponseInfo {
    override val isSuccessFul = true
    override val requiresAuth = false
    override val requireBasicAuth = false
    override val resumeSupport = true
    override val isWebPage = false
    override val unsuccessFullException: Throwable? = null
}

class TorrentLinkChecker(credentials: TorrentDownloadCredentials) :
    LinkChecker<TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes>(credentials) {
    private val _name = MutableStateFlow<String?>(null)
    override val suggestedName: StateFlow<String?> = _name.asStateFlow()
    override val downloadSize: StateFlow<DownloadSize.Bytes?> = MutableStateFlow(null)

    override fun infoUpdated(responseInfo: TorrentResponseInfo?) {
        _name.value = TorrentLinks.suggestedName(credentials.value.link)?.let(FilenameFixer::fix) ?: "torrent"
    }

    override suspend fun actualCheck(credentials: TorrentDownloadCredentials) = TorrentResponseInfo
}

object TorrentMapper : CredentialAndItemMapper<TorrentDownloadCredentials, TorrentDownloadItem> {
    override fun itemToCredentials(item: TorrentDownloadItem) = TorrentDownloadCredentials(item.link, item.downloadPage)
    override fun appliedCredentialsToItem(item: TorrentDownloadItem, credentials: TorrentDownloadCredentials) =
        item.copy().withCredentials(credentials)

    override fun itemWithEditedName(item: TorrentDownloadItem, name: String) = item.copy(name = name)
    override fun credentialsWithEditedLink(credentials: TorrentDownloadCredentials, link: String) = credentials.copy(link = link)
}

class TorrentNewDownloadUIChecker(
    initCredentials: TorrentDownloadCredentials,
    linkCheckerFactory: LinkCheckerFactory<TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>,
    initialFolder: String,
    initialName: String,
    downloadSystem: DownloadSystem,
    scope: CoroutineScope,
) : NewDownloadUiChecker<TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>(
    initialCredentials = initCredentials,
    linkCheckerFactory = linkCheckerFactory,
    initialFolder = initialFolder,
    initialName = initialName,
    downloadSystem = downloadSystem,
    scope = scope,
)

class TorrentNewDownloadInputs(
    uiChecker: TorrentNewDownloadUIChecker,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : NewDownloadInputs<TorrentDownloadItem, TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>(uiChecker) {
    private val speedLimit = MutableStateFlow(0L)

    override val downloadItem: StateFlow<TorrentDownloadItem> = combineStateFlows(
        credentials, folder, name, speedLimit,
    ) { credentials, folder, name, speedLimit ->
        TorrentDownloadItem(
            id = -1,
            folder = folder,
            name = name,
            link = credentials.link,
            downloadPage = credentials.downloadPage,
            dateAdded = openedTime,
            status = DownloadStatus.Added,
            speedLimit = speedLimit,
        )
    }
    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)
    override fun applyHostSettingsToExtraConfig(extraConfig: PerHostSettingsItem) {}

    override val configurableList = listOf(
        ToggleableSpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = speedLimit,
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(it, sizeAndSpeedUnitProvider.speedUnit.value).asStringSource()
            }
        ),
    )

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource =
        downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
}

class TorrentEditDownloadChecker(
    currentDownloadItem: MutableStateFlow<TorrentDownloadItem>,
    editedDownloadItem: MutableStateFlow<TorrentDownloadItem>,
    linkChecker: TorrentLinkChecker,
    conflictDetector: DownloadConflictDetector,
    scope: CoroutineScope,
) : EditDownloadChecker<TorrentDownloadItem, TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>(
    currentDownloadItem, editedDownloadItem, linkChecker, conflictDetector, scope,
) {
    init {
        editedDownloadItem.onEach { _canEditResult.value = CanEditDownloadResult.Waiting }.launchIn(scope)
    }

    override fun check() {
        val current = currentDownloadItem.value
        val edited = editedDownloadItem.value
        _canEditResult.value = when {
            current == edited -> CanEditDownloadResult.NothingChanged
            !TorrentLinks.isTorrent(edited.link) -> CanEditDownloadResult.InvalidURL
            edited.name != current.name && !FileNameValidator.isValidFileName(edited.name) -> CanEditDownloadResult.InvalidFileName
            edited.name != current.name && conflictDetector.checkAlreadyExists(current, edited) -> CanEditDownloadResult.FileNameAlreadyExists
            else -> CanEditDownloadResult.CanEdit(emptyList())
        }
    }
}

class TorrentEditDownloadInputs(
    currentDownloadItem: MutableStateFlow<TorrentDownloadItem>,
    editedDownloadItem: MutableStateFlow<TorrentDownloadItem>,
    conflictDetector: DownloadConflictDetector,
    linkCheckerFactory: LinkCheckerFactory<TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>,
    editDownloadCheckerFactory: EditDownloadCheckerFactory<TorrentDownloadItem, TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker>,
    scope: CoroutineScope,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : EditDownloadInputs<TorrentDownloadItem, TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker, TorrentMapper>(
    currentDownloadItem, editedDownloadItem, TorrentMapper, scope, conflictDetector, linkCheckerFactory, editDownloadCheckerFactory,
) {
    override val configurableList = listOf(
        ToggleableSpeedLimitConfigurable(
            Res.string.download_item_settings_speed_limit.asStringSource(),
            Res.string.download_item_settings_speed_limit_description.asStringSource(),
            backedBy = editedDownloadItem.mapTwoWayStateFlow(map = { it.speedLimit }, unMap = { copy(speedLimit = it) }),
            describe = {
                if (it == 0L) Res.string.unlimited.asStringSource()
                else convertPositiveSpeedToHumanReadable(it, sizeAndSpeedUnitProvider.speedUnit.value).asStringSource()
            }
        ),
    )
    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override fun applyEditedItemTo(item: TorrentDownloadItem) {
        val edited = editedDownloadItem.value
        item.link = edited.link
        item.downloadPage = edited.downloadPage
        item.folder = edited.folder
        item.name = edited.name
        item.speedLimit = edited.speedLimit
    }

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource =
        downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
}

class TorrentDownloaderInUi(
    downloader: TorrentDownloader,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : DownloaderInUi<
        TorrentDownloadCredentials, TorrentResponseInfo, DownloadSize.Bytes, TorrentLinkChecker,
        TorrentDownloadItem, TorrentNewDownloadInputs, TorrentEditDownloadInputs, TorrentMapper,
        TorrentDownloadJob, TorrentDownloader>(downloader) {

    override fun newDownloadUiChecker(
        initialCredentials: TorrentDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ) = TorrentNewDownloadUIChecker(initialCredentials, this, initialFolder, initialName, downloadSystem, scope)

    override fun acceptDownloadCredentials(item: IDownloadCredentials) = item is TorrentDownloadCredentials
    override fun supportsThisLink(link: String) = TorrentLinks.isTorrent(link)
    override fun createMinimumCredentials(link: String) = TorrentDownloadCredentials(link = link.trim())

    override fun createBareDownloadItem(credentials: TorrentDownloadCredentials, basicDownloadItem: BasicDownloadItem) =
        TorrentDownloadItem(
            id = -1,
            folder = basicDownloadItem.folder,
            name = basicDownloadItem.name,
            link = credentials.link,
            downloadPage = credentials.downloadPage,
            dateAdded = 0,
            speedLimit = basicDownloadItem.speedLimit,
        )

    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<TorrentDownloadJob>
    ): ProcessingDownloadItemState {
        val job = props.downloadJob
        val item = job.downloadItem
        val done = job.getDownloadedSize()
        val total = item.contentLength
        val status = job.status.value
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
            parts = listOf(
                UiRangedPart(
                    id = 0,
                    status = when (status) {
                        is DownloadJobStatus.Finished -> PartDownloadStatus.Completed
                        is DownloadJobStatus.IsActive -> PartDownloadStatus.ReceivingData
                        else -> PartDownloadStatus.IDLE
                    },
                    howMuchProceed = done,
                    percent = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else null,
                    length = total.takeIf { it > 0 },
                    partSpace = 1f,
                )
            ),
            supportResume = true,
            isWaiting = props.isWaiting,
        )
    }

    override val name: StringSource = "Torrent".asStringSource()

    override fun createLinkChecker(initialCredentials: TorrentDownloadCredentials) = TorrentLinkChecker(initialCredentials)

    override fun createEditDownloadChecker(
        currentDownloadItem: MutableStateFlow<TorrentDownloadItem>,
        editedDownloadItem: MutableStateFlow<TorrentDownloadItem>,
        linkChecker: TorrentLinkChecker,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ) = TorrentEditDownloadChecker(currentDownloadItem, editedDownloadItem, linkChecker, conflictDetector, scope)

    override fun createNewDownloadInputs(
        initialCredentials: TorrentDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope
    ) = TorrentNewDownloadInputs(
        newDownloadUiChecker(initialCredentials, initialFolder, initialName, downloadSystem, scope),
        sizeAndSpeedUnitProvider,
    )

    override fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<TorrentDownloadItem>,
        editedDownloadItem: MutableStateFlow<TorrentDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope
    ) = TorrentEditDownloadInputs(
        currentDownloadItem, editedDownloadItem, conflictDetector, this, this, scope, sizeAndSpeedUnitProvider,
    )
}
