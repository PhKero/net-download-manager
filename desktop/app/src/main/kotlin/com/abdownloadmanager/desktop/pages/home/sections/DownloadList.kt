package com.abdownloadmanager.desktop.pages.home.sections

import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.DOUBLE_CLICK_DELAY
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.downloader.queue.QueueManager
import com.abdownloadmanager.desktop.extras.DownloadDescriptions
import ir.amirab.util.compose.tr
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.text.style.TextOverflow
import com.abdownloadmanager.shared.ui.widget.table.customtable.Table
import com.abdownloadmanager.shared.ui.widget.table.customtable.styled.MyStyledTableHeader
import com.abdownloadmanager.shared.ui.widget.menu.custom.LocalMenuDisabledItemBehavior
import com.abdownloadmanager.shared.ui.widget.menu.custom.MenuDisabledItemBehavior
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import ir.amirab.util.compose.action.MenuItem
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.home.DownloadItemTransferable
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.table.customtable.CellSize
import com.abdownloadmanager.shared.ui.widget.table.customtable.CustomCellRenderer
import com.abdownloadmanager.shared.ui.widget.table.customtable.SortableCell
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableCell
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableState
import com.abdownloadmanager.shared.util.FileIconProvider
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.rememberCategoryOf
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorReason
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import ir.amirab.downloader.monitor.*
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.desktop.isCtrlPressed
import ir.amirab.util.desktop.isShiftPressed
import ir.amirab.util.ifThen
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds


class DownloadListContext(
    val onNewSelection: (List<Long>) -> Unit,
    val downloadList: List<IDownloadItemState>,
    val isAllSelected: Boolean,
) {
    fun newSelection(ids: List<Long>, isSelected: Boolean) {
        onNewSelection(ids.filter { isSelected })
    }

    fun changeAllSelection(isSelected: Boolean) {
        newSelection(downloadList.map { it.id }, isSelected)
    }
}

private val LocalDownloadListContext = compositionLocalOf<DownloadListContext> {
    error("DownloadListContext not provided")
}

@Composable
fun DownloadList(
    modifier: Modifier,
    downloadList: List<IDownloadItemState>,
    downloadOptions: MenuItem.SubMenu?,
    onRequestOpenOption: (IDownloadItemState) -> Unit,
    tableState: TableState<IDownloadItemState, DownloadListCells>,
    onRequestCloseOption: () -> Unit,
    selectionList: List<Long>,
    onItemSelectionChange: (Long, Boolean) -> Unit,
    onRequestOpenDownload: (Long) -> Unit,
    onNewSelection: (List<Long>) -> Unit,
    lastSelectedId: Long?,
    fileIconProvider: FileIconProvider,
    categoryManager: CategoryManager,
    queueManager: QueueManager,
    lazyListState: LazyListState,
    failedDownloadReasons: Map<Long, DownloadErrorReason>,
) {
    ShowDownloadOptions(
        downloadOptions, onRequestCloseOption
    )
    val isALlSelected by derivedStateOf {
        val list = downloadList
        if (list.isEmpty()) {
            false
        } else {
            list.map { it.id }.all {
                it in selectionList
            }
        }
    }

    val listToBeDragged by rememberUpdatedState(
        downloadList.filter { it.id in selectionList }
    )

    val tableInteractionSource = remember { MutableInteractionSource() }

    fun newSelection(ids: List<Long>, isSelected: Boolean) {
        onNewSelection(ids.filter { isSelected })
    }

    fun changeAllSelection(isSelected: Boolean) {
        newSelection(downloadList.map { it.id }, isSelected)
    }

    val windowInfo = LocalWindowInfo.current
    CompositionLocalProvider(
        LocalDownloadListContext provides DownloadListContext(
            onNewSelection,
            downloadList,
            isALlSelected,
        )
    ) {
        // IDM style: thin rows close to the window edge
        val itemHorizontalPadding = 4.dp
        // IDM "Q" column: the queue each download is in
        val queues by queueManager.queues.collectAsState()
        val queueNameOf by remember(queues) {
            if (queues.isEmpty()) flowOf(emptyMap())
            else combine(queues.map { it.queueModel }) { models ->
                buildMap { models.forEach { m -> m.queueItems.forEach { put(it, m.name) } } }
            }
        }.collectAsState(emptyMap<Long, String>())
        val descriptions by DownloadDescriptions.all.collectAsState()
        Table(
            tableState = tableState,
            listState = lazyListState,
            gridLinesStartPadding = itemHorizontalPadding,
            key = { it.id },
            list = downloadList,
            modifier = modifier
                .onKeyEvent {
                    if (it.key == Key.A && isCtrlPressed(windowInfo)) {
                        changeAllSelection(true)
                        true
                    } else {
                        false
                    }
                }
                .onKeyEvent {
                    if (it.key == Key.Escape) {
                        changeAllSelection(false)
                        true
                    } else {
                        false
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = tableInteractionSource,
                    onClick = {
                        //deselect all on click empty area
                        changeAllSelection(false)
                    },
                ),
            drawOnEmpty = {
                WithContentAlpha(0.75f) {
                    Text(myStringResource(Res.string.list_is_empty), Modifier.align(Alignment.Center))
                }
            },
            wrapHeader = {
                MyStyledTableHeader(itemHorizontalPadding = itemHorizontalPadding, content = it)
            },
            wrapItem = { _, item, rowContent ->
                val isSelected = selectionList.contains(item.id)
                var shouldWaitForSecondClick by remember {
                    mutableStateOf(false)
                }
                LaunchedEffect(shouldWaitForSecondClick) {
                    delay(DOUBLE_CLICK_DELAY.milliseconds)
                    if (shouldWaitForSecondClick) {
                        shouldWaitForSecondClick = false
                    }
                }
                val itemInteractionSource = remember { MutableInteractionSource() }
                CompositionLocalProvider(
                    LocalDownloadItemProperties provides DownloadItemProperties(
                        isSelected,
                        item,
                    )
                ) {
                    val windowInfo = LocalWindowInfo.current
                    WithContentAlpha(1f) {
                        val shape = myShapes.defaultRounded
                        Box(
                            Modifier
                                .widthIn(min = getTableSize().visibleWidth)
                                .ifThen(isSelected) {
                                    dragAndDropSource(
                                        drawDragDecoration = {},
                                        transferData = {
                                            val selectedDownloads = listToBeDragged
                                            if (selectedDownloads.isEmpty() || !isSelected) {
                                                return@dragAndDropSource null
                                            }
                                            val shiftPressed = isShiftPressed(windowInfo)
                                            val supportedActions = listOf(
                                                if (shiftPressed) {
                                                    DragAndDropTransferAction.Move
                                                } else {
                                                    DragAndDropTransferAction.Copy
                                                }
                                            )
                                            DragAndDropTransferData(
                                                transferable = DragAndDropTransferable(
                                                    DownloadItemTransferable(selectedDownloads)
                                                ),
                                                supportedActions = supportedActions,
                                            )
                                        }
                                    )
                                }
                                .onClick(
                                    interactionSource = itemInteractionSource
                                ) {
                                    if (shouldWaitForSecondClick) {
                                        onRequestOpenDownload(item.id)
                                        shouldWaitForSecondClick = false
                                    } else {
                                        if (isCtrlPressed(windowInfo)) {
                                            onItemSelectionChange(item.id, !isSelected)
                                        } else {
                                            changeAllSelection(false)
                                            onItemSelectionChange(item.id, true)
                                            shouldWaitForSecondClick = true
                                        }
                                    }
                                }
                                .onClick(
                                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                ) {
                                    onRequestOpenOption(item)
                                }
                                .onClick(
                                    enabled = lastSelectedId != null,
                                    keyboardModifiers = {
                                        this.isShiftPressed
                                    }
                                ) {

                                    val lastSelected = lastSelectedId ?: return@onClick
                                    val currentId = item.id

                                    val ids = tableState.getARangeOfItems(
                                        list = downloadList,
                                        id = { it.id },
                                        fromItem = lastSelected,
                                        toItem = currentId,
                                    )
                                    newSelection(ids, true)
                                }
                                .clip(shape)
                                .indication(
                                    interactionSource = itemInteractionSource,
                                    indication = LocalIndication.current
                                )
                                .hoverable(itemInteractionSource)
                                .focusable(
                                    interactionSource = itemInteractionSource
                                )
                                .let {
                                    if (isSelected) {
                                        // classic Windows list: a plain light-blue selection
                                        it.background(myColors.primary / 0.22f)
                                    } else {
                                        it
                                    }
                                }
                                .padding(vertical = 2.dp, horizontal = itemHorizontalPadding)
                        ) {
                            rowContent()
                        }
                    }
                }
            }
        ) { cell, item ->
            when (cell) {
                DownloadListCells.Check -> {
                    CheckCell(
                        onCheckedChange = { downloadId, isChecked ->
                            val currentSelection = selectionList.find {
                                downloadId == it
                            }?.let { true } ?: false
                            onItemSelectionChange(downloadId, !currentSelection)
                        },
                        dItemState = item
                    )
                }

                DownloadListCells.Name -> {
                    NameCell(
                        itemState = item,
                        category = categoryManager.rememberCategoryOf(item.id),
                        fileIconProvider = fileIconProvider,
                    )
                }

                DownloadListCells.DateAdded -> {
                    DateAddedCell(item)
                }

                DownloadListCells.DateStarted -> {
                    DateStartedCell(item)
                }

                DownloadListCells.DateFinished -> {
                    DateFinishedCell(item)
                }

                DownloadListCells.Size -> {
                    SizeCell(item)
                }

                DownloadListCells.Speed -> {
                    SpeedCell(item)
                }

                DownloadListCells.Status -> {
                    val reason = failedDownloadReasons[item.id]
                    StatusCell(item, reason)
                }

                DownloadListCells.TimeLeft -> {
                    TimeLeftCell(item)
                }

                DownloadListCells.Location -> {
                    LocationCell(item)
                }

                DownloadListCells.Source -> {
                    SourceBadge(item.downloadLink)
                }

                DownloadListCells.Queue -> {
                    SimpleTextCell(queueNameOf[item.id].orEmpty())
                }

                DownloadListCells.Description -> {
                    // the user's own description (right-click > Description...), else the link
                    SimpleTextCell(descriptions[item.id] ?: item.downloadLink)
                }
            }
        }
    }
}

sealed interface DownloadListCells : TableCell<IDownloadItemState> {
    data object Check : DownloadListCells,
        CustomCellRenderer {
        override val id: String = "#"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(26.dp)

        @Composable
        override fun drawHeader() {
            val c = LocalDownloadListContext.current
            CheckBox(
                c.isAllSelected,
                {
                    c.changeAllSelection(it)
                },
                modifier = Modifier.size(12.dp)
            )
        }
    }

    data object Name : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.name }

        override val id: String = "Name"
        override val name: StringSource = Res.string.name.asStringSource()
        override val size: CellSize = CellSize.Resizeable(50.dp..1000.dp, 200.dp)
    }

    data object Status : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy(
            {
                it.statusOrFinished().order
            }, {
                when (it) {
                    is CompletedDownloadItemState -> 100
                    is ProcessingDownloadItemState -> it.percent ?: 0
                }
            }
        )

        override val id: String = "Status"
        override val name: StringSource = Res.string.status.asStringSource()
        override val size: CellSize = CellSize.Resizeable(100.dp..140.dp, 120.dp)
    }

    data object Size : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.contentLength }

        override val id: String = "Size"
        override val name: StringSource = Res.string.size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..110.dp, 70.dp)
    }

    data object Speed : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.speedOrNull() ?: 0L }

        override val id: String = "Speed"
        override val name: StringSource = Res.string.speed.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..110.dp, 80.dp)
    }

    data object TimeLeft : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.remainingOrNull() ?: Long.MAX_VALUE }

        override val id: String = "Time Left"
        override val name: StringSource = Res.string.time_left.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..150.dp, 100.dp)
    }

    data object DateAdded : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.dateAdded }

        override val id: String = "Date Added"
        override val name: StringSource = Res.string.date_added.asStringSource()
        override val size: CellSize = CellSize.Resizeable(90.dp..150.dp, 100.dp)
    }

    data object DateStarted : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.startTime }

        // IDM "Last Try Date": when the download was last started
        override val id: String = "Last Try Date"
        override val name: StringSource = tr("Last Try Date", "آخر محاولة")
        override val size: CellSize = CellSize.Resizeable(90.dp..150.dp, 100.dp)
    }

    data object DateFinished : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.completeTime }

        override val id: String = "Date Finished"
        override val name: StringSource = Res.string.date_finished.asStringSource()
        override val size: CellSize = CellSize.Resizeable(90.dp..150.dp, 100.dp)
    }

    data object Location : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.folder }

        override val id: String = "Location"
        override val name: StringSource = Res.string.location.asStringSource()
        override val size: CellSize = CellSize.Resizeable(50.dp..1000.dp, 200.dp)
    }

    // IDM style "Source" column: where the download comes from, as a small coloured badge
    data object Source : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { sourceOf(it.downloadLink).ordinal }

        override val id: String = "Source"
        override val name: StringSource = tr("Source", "المصدر")
        override val size: CellSize = CellSize.Resizeable(44.dp..120.dp, 58.dp)
    }

    data object Queue : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.id }

        override val id: String = "Q"
        override val name: StringSource = "Q".asStringSource()
        override val size: CellSize = CellSize.Resizeable(30.dp..200.dp, 60.dp)
    }

    data object Description : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.downloadLink }

        override val id: String = "Description"
        override val name: StringSource = tr("Description", "الوصف")
        override val size: CellSize = CellSize.Resizeable(80.dp..1000.dp, 220.dp)
    }

    companion object {
        val allCells: List<DownloadListCells> = listOf(
            // IDM order: File Name | Q | Size | Status | Time left | Transfer rate | Last Try Date | Description
            Check,
            Source,
            Name,
            Queue,
            Size,
            Status,
            TimeLeft,
            Speed,
            DateStarted,
            Description,
            DateAdded,
            DateFinished,
            Location,
        )
        private val disabledByDefault = listOf(
            // IDM has no check boxes: select with the mouse (Ctrl / Shift for more)
            Check,
            DateAdded,
            DateFinished,
            Location,
        )
        val initiallyVisibleCells: List<DownloadListCells> = allCells
            .filterNot { it in disabledByDefault }
    }
}

enum class DownloadSource(val label: String, val color: Long) {
    Web("Web", 0xFF2E9E46),
    Video("Video", 0xFFD9363E),
    Torrent("Torrent", 0xFF7B4FC9),
    Ftp("FTP", 0xFF1E7FD0),
}

private val videoHosts = listOf(
    "youtube.", "youtu.be", "facebook.", "fb.watch", "instagram.", "tiktok.", "vimeo.", "twitter.", "x.com",
    "dailymotion.", "twitch.", "reddit.", "soundcloud.", "bilibili.",
)

fun sourceOf(link: String): DownloadSource {
    val l = link.trim().lowercase()
    return when {
        ir.amirab.downloader.downloaditem.torrent.TorrentLinks.isFtp(l) -> DownloadSource.Ftp
        ir.amirab.downloader.downloaditem.torrent.TorrentLinks.isTorrent(l) -> DownloadSource.Torrent
        ".m3u8" in l || videoHosts.any { h -> l.substringAfter("://").substringBefore('/').let { host -> h in host } } -> DownloadSource.Video
        else -> DownloadSource.Web
    }
}

@Composable
private fun SourceBadge(link: String) {
    val source = remember(link) { sourceOf(link) }
    Text(
        source.label,
        Modifier
            .background(androidx.compose.ui.graphics.Color(source.color), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 6.dp),
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = myTextSizes.xs,
        maxLines = 1,
    )
}

@Composable
private fun SimpleTextCell(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = myTextSizes.sm,
    )
}

@Composable
fun ShowDownloadOptions(
    options: MenuItem.SubMenu?,
    onDismiss: () -> Unit,
) {
    if (options != null) {
        CompositionLocalProvider(
            LocalMenuDisabledItemBehavior provides MenuDisabledItemBehavior.LowerOpacity
        ) {
            ShowOptionsInPopup(options, onDismiss)
        }
    }
}
