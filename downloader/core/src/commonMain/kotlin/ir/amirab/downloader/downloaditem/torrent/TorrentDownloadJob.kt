package ir.amirab.downloader.downloaditem.torrent

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpBinary
import ir.amirab.downloader.part.DownloadPart
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.Volatile

/** everything of a torrent lives in one folder: `folder/name` */
class TorrentDownloadDestination(outputFolder: File) : DownloadDestination(outputFolder) {
    override fun getWriterFor(part: DownloadPart): DestWriter = throw UnsupportedOperationException("aria2 writes its own files")
    override fun canGetFileWriter() = false
    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) {
        outputFile.mkdirs()
    }

    override suspend fun isDownloadedPartsIsValid() = true
    override fun flush() {}

    override fun cleanUpJunkFiles() {
        // aria2 control files that let a paused torrent resume
        outputFile.walkTopDown().filter { it.isFile && it.name.endsWith(".aria2") }.forEach { it.delete() }
    }

    override fun deleteOutputFile() {
        outputFile.deleteRecursively()
    }
}

/**
 * Runs aria2c for a magnet link / .torrent. Pausing kills it; aria2's .aria2 control files make
 * the next start continue where it stopped. Seeding stops as soon as the download completes.
 */
class TorrentDownloadJob(
    override val downloadItem: TorrentDownloadItem,
    downloadManager: DownloadManager,
) : DownloadJob(downloadManager) {
    private val listDb by downloadManager::dlListDb
    private lateinit var destination: TorrentDownloadDestination
    override fun getDestination(): DownloadDestination = destination

    private val downloaded = AtomicLong(0)
    private var process: Process? = null
    private val processLock = Mutex()
    private val saveLock = Mutex()
    private var lastSaved: TorrentDownloadItem? = null

    @Volatile
    private var stopRequested = false

    override suspend fun actualBoot() {
        initializeDestination()
        downloaded.set(downloadItem.downloadedBytes)
    }

    override fun initializeDestination() {
        destination = TorrentDownloadDestination(downloadManager.calculateOutputFile(downloadItem))
    }

    override fun getDownloadedSize(): Long = downloaded.get()

    override suspend fun resume() {
        if (isDownloadActive.value) return
        _isDownloadActive.update { true }
        stopRequested = false
        boot()
        val newScope = newScopeBasedOn(scope).also { activeDownloadScope = it }
        newScope.launch(Dispatchers.IO) {
            onDownloadResuming()
            try {
                destination.prepareFile {}
                if (downloadItem.startTime == null) downloadItem.startTime = System.currentTimeMillis()
                runAria2()
            } catch (e: Exception) {
                if (!stopRequested && !ExceptionUtils.isNormalCancellation(e)) {
                    scope.launch { pause(e) }
                }
            }
        }
    }

    private fun aria2(): File {
        return YtDlpBinary.findTool("ABDM_ARIA2", "aria2c")
            ?: throw IllegalStateException("aria2c was not found, it is needed for torrents")
    }

    private fun buildArgs(): List<String> = buildList {
        add(aria2().absolutePath)
        add("--dir=${destination.outputFile.absolutePath}")
        add("--continue=true")
        add("--seed-time=0")
        add("--bt-save-metadata=true")
        add("--follow-torrent=mem")
        add("--file-allocation=none")
        add("--summary-interval=1")
        add("--console-log-level=error")
        add("--enable-color=false")
        add("--bt-max-peers=${(downloadItem.preferredConnectionCount ?: 0).takeIf { it > 0 }?.times(8) ?: 55}")
        val limit = listOf(downloadItem.speedLimit, downloadManager.settings.globalSpeedLimit).filter { it > 0 }.minOrNull()
        limit?.let { add("--max-overall-download-limit=$it") }
        add(downloadItem.link)
    }

    private suspend fun runAria2() {
        val p = processLock.withLock {
            ProcessBuilder(buildArgs()).redirectErrorStream(true).start().also { process = it }
        }
        downloadItem.status = DownloadStatus.Downloading
        onDownloadResumed()
        startAutoSaver()
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (tail.size >= 20) tail.removeFirst()
                tail.addLast(line)
                parseProgress(line)
            }
        }
        val exit = runInterruptible { p.waitFor() }
        processLock.withLock { process = null }
        if (exit != 0) {
            val error = tail.lastOrNull { it.contains("error", ignoreCase = true) || it.contains("Exception") }
                ?: "aria2 failed with code $exit"
            throw IllegalStateException(error.trim())
        }
        downloadItem.contentLength = folderSize().takeIf { it > 0 } ?: downloadItem.contentLength
        downloaded.set(downloadItem.contentLength.coerceAtLeast(downloaded.get()))
        saveState()
        onDownloadFinished()
    }

    private fun folderSize(): Long {
        return destination.outputFile.walkTopDown().filter { it.isFile && !it.name.endsWith(".aria2") && !it.name.endsWith(".torrent") }
            .sumOf { it.length() }
    }

    /** "[#2089b0 400.0KiB/33.2MiB(1%) CN:1 DL:115.7KiB ETA:4m51s]" */
    private val readout = Regex("""\[#\w+\s+(?:SEED\([^)]*\)\s+)?([\d.]+)([KMGT]i)?B/([\d.]+)([KMGT]i)?B""")

    private fun bytes(value: String, unit: String?): Long {
        val factor = when (unit) {
            "Ki" -> 1024.0
            "Mi" -> 1024.0 * 1024
            "Gi" -> 1024.0 * 1024 * 1024
            "Ti" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (value.toDouble() * factor).toLong()
    }

    private fun parseProgress(line: String) {
        if (line.contains("[METADATA]")) {
            // magnet: still fetching the torrent description from peers
            return
        }
        val m = readout.find(line) ?: return
        val done = bytes(m.groupValues[1], m.groupValues[2].ifEmpty { null })
        val total = bytes(m.groupValues[3], m.groupValues[4].ifEmpty { null })
        if (total > 0) downloadItem.contentLength = total
        downloaded.set(done)
        if (total > 0 && done >= total) _status.update { DownloadJobStatus.PreparingFile(null) }
    }

    private suspend fun stopProcess() {
        stopRequested = true
        processLock.withLock {
            process?.let(YtDlpBinary::killTree)
            process = null
        }
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        stopProcess()
        downloadItem.downloadedBytes = downloaded.get()
        onDownloadCanceled(throwable)
    }

    override suspend fun reset() {
        pause(CancellationException())
        destination.deleteOutputFile()
        downloaded.set(0)
        downloadItem.downloadedBytes = 0
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.status = DownloadStatus.Added
        downloadItem.startTime = null
        downloadItem.completeTime = null
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    override suspend fun saveState() {
        saveLock.withLock {
            downloadItem.downloadedBytes = downloaded.get()
            if (lastSaved != downloadItem) {
                listDb.update(downloadItem)
                lastSaved = downloadItem.copy()
            }
        }
    }

    override fun reloadSettings() {}

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?
    ): IDownloadItem {
        boot()
        val previous = downloadItem.copy()
        val newItem = previous.copy().apply(updater)
        val wasActive = isDownloadActive.value
        val moved = downloadManager.calculateOutputFile(previous) != downloadManager.calculateOutputFile(newItem)
        if (wasActive) pause(CancellationException())
        if (moved) destination.moveOutput(downloadManager.calculateOutputFile(newItem))
        downloadItem.applyFrom(newItem)
        initializeDestination()
        saveState()
        if (wasActive) resume()
        return downloadItem
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {}

    override fun onDownloadFinishedBeforeSave() {
        downloadItem.downloadedBytes = downloadItem.contentLength
    }
}
