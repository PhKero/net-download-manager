package com.abdownloadmanager.desktop.extras

import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.seconds

/**
 * IDM style download quota: at most N megabytes per period. When the limit is reached the
 * active downloads are paused and resumed automatically once the period is over.
 */
class DownloadQuota(
    private val downloadSystem: DownloadSystem,
    private val enabled: StateFlow<Boolean>,
    private val megabytes: StateFlow<Long>,
    private val periodMinutes: StateFlow<Long>,
    private val onLimitReached: (resumeAt: Long) -> Unit,
    private val onResumed: () -> Unit,
) {
    private var periodStart = System.currentTimeMillis()
    private var usedInPeriod = 0L
    private val lastSizes = mutableMapOf<Long, Long>()
    private var pausedByQuota = emptyList<Long>()

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(2.seconds)
                runCatching { tick() }
            }
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val period = periodMinutes.value.coerceAtLeast(1) * 60_000
        if (now - periodStart >= period) {
            periodStart = now
            usedInPeriod = 0
            if (pausedByQuota.isNotEmpty()) {
                val toResume = pausedByQuota
                pausedByQuota = emptyList()
                toResume.forEach { downloadSystem.userManualResume(it) }
                onResumed()
            }
        }
        val jobs = downloadSystem.downloadManager.downloadJobs
        // count what was written since the last tick, whether or not the quota is on
        for (job in jobs) {
            val size = job.getDownloadedSize()
            val last = lastSizes[job.id]
            if (last != null && size > last) usedInPeriod += size - last
            lastSizes[job.id] = size
        }
        if (!enabled.value || pausedByQuota.isNotEmpty()) return
        val limit = megabytes.value.coerceAtLeast(1) * 1024 * 1024
        if (usedInPeriod >= limit) {
            val active = jobs.filter { it.status.value is DownloadJobStatus.IsActive }.map { it.id }
            if (active.isEmpty()) return
            pausedByQuota = active
            active.forEach { downloadSystem.manualPause(it) }
            onLimitReached(periodStart + period)
        }
    }
}
