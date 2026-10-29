package com.abdownloadmanager.desktop.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.prefs.Preferences

/**
 * IDM style: a window opens again at the size the user last dragged it to
 * (the "Download File Info" window and the download window).
 */
object WindowSizeMemory {
    private val prefs: Preferences? = runCatching {
        Preferences.userRoot().node("net-download-manager/window-sizes")
    }.getOrNull()

    fun get(key: String): DpSize? {
        val p = prefs ?: return null
        val w = p.getFloat("$key.width", -1f)
        val h = p.getFloat("$key.height", -1f)
        return if (w > 0f && h > 0f) DpSize(w.dp, h.dp) else null
    }

    fun save(key: String, size: DpSize) {
        val p = prefs ?: return
        runCatching {
            p.putFloat("$key.width", size.width.value)
            p.putFloat("$key.height", size.height.value)
            p.flush()
        }
    }

    /** the saved size, never smaller than [min] (a screen or scale change could make it too small) */
    fun getAtLeast(key: String, min: DpSize): DpSize? {
        val saved = get(key) ?: return null
        return DpSize(maxOf(saved.width, min.width), maxOf(saved.height, min.height))
    }
}

/** saves the window size a moment after the user stops resizing it */
@OptIn(FlowPreview::class)
@Composable
fun RememberWindowSize(key: String, state: WindowState, onlyWidth: Boolean = false) {
    LaunchedEffect(key, state) {
        snapshotFlow { state.size }
            .distinctUntilChanged()
            .debounce(600)
            .collect { size ->
                if (size.width.value > 0f && size.height.value > 0f) {
                    if (onlyWidth) {
                        val old = WindowSizeMemory.get(key)
                        WindowSizeMemory.save(key, DpSize(size.width, old?.height ?: size.height))
                    } else {
                        WindowSizeMemory.save(key, size)
                    }
                }
            }
    }
}
