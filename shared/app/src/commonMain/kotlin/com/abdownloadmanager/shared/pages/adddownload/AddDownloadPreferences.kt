package com.abdownloadmanager.shared.pages.adddownload

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Add-dialog preferences set by the desktop app (see ExtrasSettings).
 *
 * rememberLastFolder (IDM style): a new download opens in the folder of the last download you
 * added, instead of going back to the category or default folder every time.
 */
object AddDownloadPreferences {
    val rememberLastFolder = MutableStateFlow(true)

    /** IDM: start downloading while the Download File Info dialog is still open (see EarlyStartedDownloads) */
    val startWhileDialogOpen = MutableStateFlow(false)

    /** the folder to suggest, or null to keep the normal category / default folder */
    fun lastFolder(lastUsedLocations: List<String>): String? {
        if (!rememberLastFolder.value) return null
        return lastUsedLocations.firstOrNull()?.takeIf { it.isNotBlank() && File(it).isDirectory }
    }
}
