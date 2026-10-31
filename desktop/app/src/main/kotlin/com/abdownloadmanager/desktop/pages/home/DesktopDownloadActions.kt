package com.abdownloadmanager.desktop.pages.home

import com.abdownloadmanager.shared.pagemanager.EditDownloadDialogManager
import com.abdownloadmanager.shared.pagemanager.FileChecksumDialogManager
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pagemanager.DownloadDialogManager
import com.abdownloadmanager.shared.pages.home.AbstractDownloadActions
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import ir.amirab.downloader.monitor.IDownloadItemState
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.action.buildMenu
import ir.amirab.util.compose.action.simpleAction
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.tr
import com.abdownloadmanager.desktop.extras.DownloadDescriptions
import ir.amirab.util.osfileutil.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DesktopDownloadActions(
    scope: CoroutineScope,
    downloadSystem: DownloadSystem,
    downloadDialogManager: DownloadDialogManager,
    editDownloadDialogManager: EditDownloadDialogManager,
    fileChecksumDialogManager: FileChecksumDialogManager,
    selections: StateFlow<List<IDownloadItemState>>,
    queueManager: QueueManager,
    categoryManager: CategoryManager,
    openFile: (Long) -> Unit,
    openWith: (Long) -> Unit,
    requestDelete: (List<Long>) -> Unit,
    mainItem: StateFlow<Long?>,
    private val openFolder: (Long) -> Unit,
) : AbstractDownloadActions(
    scope = scope,
    downloadSystem = downloadSystem,
    downloadDialogManager = downloadDialogManager,
    editDownloadDialogManager = editDownloadDialogManager,
    fileChecksumDialogManager = fileChecksumDialogManager,
    selections = selections,
    queueManager = queueManager,
    categoryManager = categoryManager,
    openFile = openFile,
    openWith = openWith,
    requestDelete = requestDelete,
    mainItem = mainItem,
) {
    val openFolderAction = simpleAction(
        title = Res.string.open_folder.asStringSource(),
        icon = MyIcons.folderOpen,
        onActionPerformed = {
            scope.launch {
                val d = defaultItem.value ?: return@launch
                openFolder(d.id)
            }
        }
    )

    // IDM: a description of your own for each download (shown in the Description column)
    val descriptionAction = simpleAction(
        title = tr("Description...", "الوصف..."),
        icon = MyIcons.edit,
        onActionPerformed = {
            scope.launch {
                val d = defaultItem.value ?: return@launch
                DownloadDescriptions.editing.value = d.id
            }
        }
    )

    val menu: List<MenuItem> = buildMenu {
        +openFileAction
        if (FileUtils.isOpenFileWithSupported) {
            +openWithAction
        }
        +openFolderAction
        +(resumeAction)
        +pauseAction
        separator()
        +(deleteAction)
        +(reDownloadAction)
        separator()
        +moveToQueueItems
        +moveToCategoryAction
        separator()
        subMenu(Res.string.copy.asStringSource(), MyIcons.copy) {
            +(copyDownloadLinkAction)
            +(copyDownloadCredentialsAsJsonAction)
            +(copyDownloadCredentialsAsCurlAction)
        }
        +editDownloadAction
        +descriptionAction
        +fileChecksumAction
        +(openDownloadDialogAction)
    }
}
