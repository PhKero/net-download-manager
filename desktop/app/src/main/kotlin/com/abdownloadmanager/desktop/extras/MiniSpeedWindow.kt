package com.abdownloadmanager.desktop.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import org.koin.core.context.GlobalContext
import androidx.compose.runtime.remember
import java.awt.GraphicsEnvironment

/**
 * IDM style always-on-top mini window with the total speed; drag it anywhere,
 * click it to open the main window. Turned on from Settings > Video & Extras.
 */
@Composable
fun MiniSpeedWindow(appComponent: AppComponent) {
    val extras = remember { GlobalContext.get().get<ExtrasSettings>() }
    val appRepository = remember { GlobalContext.get().get<AppRepository>() }
    val enabled by extras.miniWindow.collectAsState()
    if (!enabled) return
    val active by appComponent.downloadSystem.downloadMonitor.activeDownloadListFlow.collectAsState()
    val speedUnit by appRepository.speedUnit.collectAsState()
    val size = DpSize(210.dp, 40.dp)
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val state = rememberWindowState(
        size = size,
        position = WindowPosition((screen.width - 230).dp, (screen.height - 70).dp),
    )
    Window(
        onCloseRequest = { extras.miniWindow.value = false },
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
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .fillMaxSize()
                    .background(myColors.surface, shape)
                    .border(1.dp, myColors.onBackground.copy(alpha = 0.2f), shape)
                    .clickable { appComponent.openHome() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyIcon(MyIcons.appIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                val speed = active.sumOf { it.speed.coerceAtLeast(0) }
                val text = if (active.isEmpty()) {
                    "No active downloads"
                } else {
                    "${convertPositiveSpeedToHumanReadable(speed, speedUnit)}  ·  ${active.size} active"
                }
                Text(text, fontSize = 12.sp, color = myColors.onSurface, maxLines = 1)
            }
        }
    }
}
