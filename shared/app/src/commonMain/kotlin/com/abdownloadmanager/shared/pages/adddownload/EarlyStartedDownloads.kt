package com.abdownloadmanager.shared.pages.adddownload

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * IDM "start download immediately while showing Download File Info": the file already downloads
 * while the dialog is open. These are the downloads started that way and not confirmed yet:
 * - the dialog's duplicate check ignores them (it would otherwise find its own download),
 * - the progress window does not pop up for them until the user presses Start Download,
 * - they are deleted (with their partial file) when the dialog is cancelled.
 */
object EarlyStartedDownloads {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids

    private val _confirmed = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** a download the user confirmed with Start Download (now an ordinary download) */
    val confirmed: SharedFlow<Long> = _confirmed

    fun add(id: Long) = _ids.update { it + id }
    fun remove(id: Long) = _ids.update { it - id }
    fun isEarly(id: Long) = id in _ids.value

    fun confirm(id: Long) {
        remove(id)
        _confirmed.tryEmit(id)
    }
}
