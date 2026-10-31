package com.abdownloadmanager.desktop.extras

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.compose.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * IDM "Description": a note of your own for each download, shown in the Description column.
 * Kept in ~/.abdm/descriptions.json, apart from the download list, so the saved downloads keep
 * their format and older versions still open them.
 */
object DownloadDescriptions {
    // next to the download list: the ids belong to that list (a portable copy has its own)
    private val file = com.abdownloadmanager.desktop.utils.AppInfo.definedPaths.downloadDbDir.toFile().resolve("descriptions.json")
    private val json = Json { prettyPrint = true }
    private val serializer = MapSerializer(Long.serializer(), String.serializer())

    private val _all = MutableStateFlow(
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyMap())
    )
    val all: StateFlow<Map<Long, String>> = _all

    /** the download whose description window is open, if any */
    val editing = MutableStateFlow<Long?>(null)

    fun set(id: Long, text: String) {
        _all.update { if (text.isBlank()) it - id else it + (id to text.trim()) }
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(json.encodeToString(serializer, _all.value))
        }
    }
}

@Composable
fun DownloadDescriptionWindow(nameOf: (Long) -> String?) {
    val id = DownloadDescriptions.editing.collectAsState().value ?: return
    val close = { DownloadDescriptions.editing.value = null }
    key(id) {
        val state = rememberWindowState(
            initialBoundsProvider = WindowBoundsProvider(
                sizeProvider = WindowSizeProvider.Fixed(DpSize(460.dp, 230.dp)),
                positionProvider = WindowPositionProvider.CenteredOnScreen,
            )
        )
        CustomWindow(
            state = state,
            onCloseRequest = close,
            onRequestToggleMaximize = null,
            resizable = true,
            minSize = DpSize(380.dp, 200.dp),
            windowController = rememberWindowController(
                title = tr("Description", "الوصف").rememberString(),
            ),
        ) {
            WindowIcon(MyIcons.appIcon)
            var text by remember { mutableStateOf(DownloadDescriptions.all.value[id].orEmpty()) }
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                WithContentAlpha(0.75f) {
                    Text(nameOf(id).orEmpty(), maxLines = 1, fontSize = myTextSizes.sm)
                }
                Spacer(Modifier.height(8.dp))
                MyTextField(
                    text = text,
                    onTextChange = { text = it.take(500) },
                    placeholder = tr("A note about this download", "ملاحظة عن التحميل ده").rememberString(),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    singleLine = false,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    ActionButton(tr("Save", "حفظ").rememberString(), onClick = {
                        DownloadDescriptions.set(id, text)
                        close()
                    })
                    Spacer(Modifier.width(8.dp))
                    ActionButton(tr("Cancel", "إلغاء").rememberString(), onClick = close)
                }
            }
        }
    }
}
