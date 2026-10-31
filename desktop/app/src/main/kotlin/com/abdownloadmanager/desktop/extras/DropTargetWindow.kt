package com.abdownloadmanager.desktop.extras

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.ui.widget.Tooltip
import com.abdownloadmanager.shared.util.extractors.linkextractor.DefaultDownloadCredentialsExtractor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.tr
import org.koin.core.context.GlobalContext
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.DataFlavor

/**
 * IDM "drop target": a small round window that stays on top of everything. Drag a link from the
 * browser and drop it here to download it. Move it by dragging. Turned on from Tasks > Show drop target
 * or Settings > Video & Extras.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DropTargetWindow(appComponent: AppComponent) {
    val extras = remember { GlobalContext.get().get<ExtrasSettings>() }
    val enabled by extras.dropTarget.collectAsState()
    if (!enabled) return
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val state = rememberWindowState(
        size = DpSize(64.dp, 64.dp),
        position = WindowPosition((screen.width - 90).dp, (screen.height / 2).dp),
    )
    var hovering by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = { extras.dropTarget.value = false },
        state = state,
        title = "Net Download Manager",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        icon = MyIcons.appIcon.rememberPainter(),
    ) {
        WindowDraggableArea {
            Tooltip(tr("Drop links here to download them", "اسحب اللينكات هنا عشان تتحمل")) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (hovering) myColors.primary else myColors.surface, CircleShape)
                        .border(2.dp, myColors.primary, CircleShape)
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = {
                                it.awtTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)
                            },
                            target = remember {
                                object : DragAndDropTarget {
                                    override fun onEntered(event: DragAndDropEvent) {
                                        hovering = true
                                    }

                                    override fun onExited(event: DragAndDropEvent) {
                                        hovering = false
                                    }

                                    override fun onEnded(event: DragAndDropEvent) {
                                        hovering = false
                                    }

                                    override fun onDrop(event: DragAndDropEvent): Boolean {
                                        hovering = false
                                        val text = runCatching {
                                            event.awtTransferable.getTransferData(DataFlavor.stringFlavor) as String
                                        }.getOrNull() ?: return false
                                        val links = DefaultDownloadCredentialsExtractor.extract(text).distinctBy { it.link }
                                        if (links.isEmpty()) return false
                                        appComponent.openAddDownloadDialog(
                                            links.map { AddDownloadCredentialsInUiProps(credentials = it) },
                                            ImportOptions(),
                                        )
                                        return true
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MyIcon(MyIcons.appIcon, null, Modifier.size(32.dp))
                }
            }
        }
    }
}
