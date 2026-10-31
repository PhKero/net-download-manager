package com.abdownloadmanager.desktop.extras

import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.exception.TooManyErrorException
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.time.Duration.Companion.seconds

/**
 * Picks a download back up by itself when it stopped because the internet dropped
 * (router restart, Wi-Fi lost, the laptop slept). The download engine only retries a few
 * seconds, so without this a big download stays stopped until the user notices.
 * Downloads the user paused are never touched. Turned off in Settings > Video & Extras.
 */
class AutoResume(
    private val downloadSystem: DownloadSystem,
    private val enabled: StateFlow<Boolean>,
    private val onResumed: (count: Int) -> Unit,
) {
    private class Attempt(var count: Int, var nextTryAt: Long)

    private val attempts = mutableMapOf<Long, Attempt>()

    fun start(scope: CoroutineScope) {
        // a background thread: a delay() loop on the Main (UI) dispatcher never woke up here
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(15.seconds)
                runCatching { tick() }
            }
        }
    }

    private suspend fun tick() {
        val jobs = downloadSystem.downloadManager.downloadJobs
        // a download that runs again (by us or by the user) starts with a clean slate
        jobs.filter { it.status.value is DownloadJobStatus.IsActive || it.status.value is DownloadJobStatus.Finished }
            .forEach { attempts.remove(it.id) }
        if (!enabled.value) return
        val now = System.currentTimeMillis()
        val stoppedByNetwork = jobs.filter { job ->
            val status = job.status.value
            status is DownloadJobStatus.Canceled && isNetworkProblem(status.e) &&
                (attempts[job.id]?.let { it.count < MAX_ATTEMPTS && now >= it.nextTryAt } ?: true)
        }
        if (stoppedByNetwork.isEmpty()) return
        val resumed = stoppedByNetwork.filter { job ->
            val host = runCatching { URI(job.downloadItem.link).host }.getOrNull()
            if (!isReachable(host)) return@filter false
            val attempt = attempts.getOrPut(job.id) { Attempt(0, 0) }
            attempt.count++
            // 30 s, 1 min, 2 min ... up to 10 min between tries, in case the server itself is down
            attempt.nextTryAt = now + (30_000L shl (attempt.count - 1).coerceAtMost(5)).coerceAtMost(600_000L)
            runCatching { downloadSystem.userManualResume(job.id) }.isSuccess
        }
        if (resumed.isNotEmpty()) onResumed(resumed.size)
    }

    private fun isNetworkProblem(e: Throwable): Boolean {
        if (ExceptionUtils.isNormalCancellation(e)) return false // paused by the user
        val cause = if (e is TooManyErrorException) e.findActualDownloadErrorCause() else e
        return generateSequence(cause) { it.cause }.take(5).any { ExceptionUtils.isNetworkError(it) }
    }

    /** the connection is back when the download's own server answers on its port */
    private suspend fun isReachable(host: String?): Boolean = withContext(Dispatchers.IO) {
        val targets = listOfNotNull(host?.let { it to 443 }, host?.let { it to 80 }, "1.1.1.1" to 443)
        targets.any { (h, port) ->
            runCatching { Socket().use { it.connect(InetSocketAddress(h, port), 4000) } }.isSuccess
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 30
    }
}
