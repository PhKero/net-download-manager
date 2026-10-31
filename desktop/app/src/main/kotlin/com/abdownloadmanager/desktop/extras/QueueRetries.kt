package com.abdownloadmanager.desktop.extras

import com.abdownloadmanager.desktop.storage.DesktopExtraQueueSettings
import com.abdownloadmanager.shared.storage.IExtraQueueSettingsStorage
import com.abdownloadmanager.shared.util.DownloadSystem
import ir.amirab.downloader.downloaditem.hls.HLSDownloadJob
import ir.amirab.downloader.downloaditem.http.HttpDownloadJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * IDM "number of retries for each file" per queue: the downloads of a queue get that queue's
 * retry count instead of the general one (0 in the queue = the general setting).
 */
class QueueRetries(
    private val downloadSystem: DownloadSystem,
    private val queueSettings: IExtraQueueSettingsStorage<DesktopExtraQueueSettings>,
) {
    fun start(scope: CoroutineScope) {
        // a background thread: a delay() loop on the Main (UI) dispatcher never woke up here
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                runCatching { apply() }
                delay(5.seconds)
            }
        }
    }

    private fun apply() {
        val jobs = downloadSystem.downloadManager.downloadJobs.associateBy { it.id }
        for (queue in downloadSystem.queueManager.queues.value) {
            val retries = queueSettings.getExtraQueueSettings(queue.id).retriesPerFile.takeIf { it > 0 }
            for (id in queue.getQueueModel().queueItems) {
                when (val job = jobs[id]) {
                    is HttpDownloadJob -> job._maxAllowedRetries = retries
                    is HLSDownloadJob -> job._maxAllowedRetries = retries
                    else -> {}
                }
            }
        }
    }
}
