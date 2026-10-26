package com.abdownloadmanager.desktop.extras

import ir.amirab.downloader.DownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

/**
 * Lower speed limit during chosen hours (e.g. 09:00-17:00 while working), back to the
 * normal limit outside them. The user's own speed limit setting is never changed.
 */
class SpeedSchedule(
    private val downloadManager: DownloadManager,
    private val enabled: StateFlow<Boolean>,
    private val from: StateFlow<String>,
    private val to: StateFlow<String>,
    private val limitKb: StateFlow<Long>,
    /** the limit the user set in the app's own settings, 0 = unlimited */
    private val userLimit: () -> Long,
) {
    private var appliedLimit: Long? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(5.seconds)
            }
        }
    }

    private fun parse(text: String): LocalTime? = runCatching { LocalTime.parse(text.trim().padStart(5, '0')) }.getOrNull()

    private fun insideWindow(now: LocalTime): Boolean {
        val start = parse(from.value) ?: return false
        val end = parse(to.value) ?: return false
        return if (start <= end) now >= start && now < end
        // windows like 22:00-06:00 cross midnight
        else now >= start || now < end
    }

    private fun tick() {
        val user = userLimit()
        val wanted = if (enabled.value && insideWindow(LocalTime.now())) {
            val scheduled = limitKb.value.coerceAtLeast(1) * 1024
            if (user > 0) minOf(user, scheduled) else scheduled
        } else {
            // only take over again after we changed something
            if (appliedLimit == null) return
            user
        }
        if (wanted == appliedLimit) return
        downloadManager.settings.globalSpeedLimit = wanted
        downloadManager.limitGlobalSpeed(wanted)
        appliedLimit = if (enabled.value && insideWindow(LocalTime.now())) wanted else null
    }
}
