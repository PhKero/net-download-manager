package com.abdownloadmanager.desktop.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.abdownloadmanager.desktop.window.custom.CustomWindow
import com.abdownloadmanager.desktop.window.custom.WindowIcon
import com.abdownloadmanager.desktop.window.custom.rememberWindowController
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.tr

/**
 * IDM "Customize toolbar": choose which buttons show and in what order.
 * Opened by right-clicking the toolbar. Changes apply at once and are saved.
 */
@Composable
fun ToolbarCustomizeWindow(
    component: HomeComponent,
    onClose: () -> Unit,
) {
    val state = rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Fixed(DpSize(420.dp, 520.dp)),
            positionProvider = WindowPositionProvider.CenteredOnScreen,
        )
    )
    CustomWindow(
        state = state,
        onCloseRequest = onClose,
        onRequestToggleMaximize = null,
        resizable = true,
        minSize = DpSize(360.dp, 420.dp),
        windowController = rememberWindowController(
            title = tr("Customize toolbar", "تخصيص شريط الأدوات").rememberString(),
        ),
    ) {
        WindowIcon(MyIcons.appIcon)
        val saved by component.toolbarLayout.collectAsState()
        val shown = remember(saved) { component.toolbarKeys(saved) }
        // shown buttons first in their order, then the hidden ones
        val all = remember(shown) {
            shown + component.toolbarCatalog.map { it.first }.filter { it !in shown }
        }
        val byKey = remember { component.toolbarCatalog.toMap() }
        val showLabels by component.showLabels.collectAsState()

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            WithContentAlpha(0.75f) {
                Text(
                    tr("Tick the buttons to show and move them up or down.", "علّم على الزراير اللي عايزها وحرّكها لفوق أو لتحت.").rememberString(),
                    fontSize = myTextSizes.sm,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(myColors.surface / 0.5f)
                    .padding(4.dp)
            ) {
                itemsIndexed(all, key = { _, k -> k }) { index, key ->
                    val item = byKey[key] ?: return@itemsIndexed
                    val isShown = key in shown
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(isShown, { on ->
                            val next = if (on) shown + key else shown - key
                            // never an empty toolbar
                            if (next.isNotEmpty()) component.setToolbarKeys(next)
                        })
                        Spacer(Modifier.width(8.dp))
                        val readable = item as? MenuItem.ReadableItem
                        readable?.icon?.collectAsState()?.value?.let { MyIcon(it, null, Modifier.size(16.dp)) }
                        Spacer(Modifier.width(8.dp))
                        val title = readable?.title?.collectAsState()?.value?.rememberString() ?: key
                        Text(title, Modifier.weight(1f), maxLines = 1)
                        if (isShown) {
                            val pos = shown.indexOf(key)
                            MoveButton("▲", enabled = pos > 0) {
                                component.setToolbarKeys(shown.toMutableList().apply { add(pos - 1, removeAt(pos)) })
                            }
                            MoveButton("▼", enabled = pos < shown.size - 1) {
                                component.setToolbarKeys(shown.toMutableList().apply { add(pos + 1, removeAt(pos)) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clickable { component.showLabels.value = !showLabels },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(showLabels, { component.showLabels.value = it })
                Spacer(Modifier.width(8.dp))
                Text(tr("Show labels under the icons (large buttons)", "اعرض الأسماء تحت الأيقونات (زراير كبيرة)").rememberString())
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(tr("Reset", "الوضع الأصلي").rememberString(), onClick = {
                    component.setToolbarKeys(component.toolbarCatalog.map { it.first })
                })
                Spacer(Modifier.weight(1f))
                ActionButton(tr("Close", "إغلاق").rememberString(), onClick = onClose)
            }
        }
    }
}

@Composable
private fun MoveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    WithContentAlpha(if (enabled) 0.9f else 0.25f) {
        Text(
            text,
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = myTextSizes.sm,
        )
    }
}
