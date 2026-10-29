package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * Runs one yt-dlp process per resume. Pausing kills the process; yt-dlp keeps its .part files,
 * so the next resume continues from where it stopped.
 */
class YtDlpDownloadJob(
    override val downloadItem: YtDlpDownloadItem,
    downloadManager: DownloadManager,
) : DownloadJob(downloadManager) {
    val listDb by downloadManager::dlListDb

    private lateinit var destination: YtDlpDownloadDestination
    override fun getDestination(): DownloadDestination = destination

    private val downloadedBytes = AtomicLong(0)
    private var process: Process? = null
    private var processJob: Job? = null
    @Volatile
    private var stopRequested = false
    private val processLock = Mutex()
    private val saveLock = Mutex()
    private var lastSaved: YtDlpDownloadItem? = null

    override suspend fun actualBoot() {
        initializeDestination()
        downloadedBytes.set(downloadItem.downloadedBytes)
    }

    override fun initializeDestination() {
        destination = YtDlpDownloadDestination(downloadManager.calculateOutputFile(downloadItem))
    }

    override fun getDownloadedSize(): Long = downloadedBytes.get()

    override suspend fun resume() {
        if (isDownloadActive.value) return
        _isDownloadActive.update { true }
        stopRequested = false
        boot()
        val newScope = newScopeBasedOn(scope).also { activeDownloadScope = it }
        processJob = newScope.launch(Dispatchers.IO) {
            onDownloadResuming()
            try {
                destination.prepareFile {}
                if (downloadItem.startTime == null) {
                    downloadItem.startTime = System.currentTimeMillis()
                }
                runYtDlp(useBrowserCookies = false, scope = this)
            } catch (e: Exception) {
                // killing the process on pause also ends up here; that is not an error
                if (!stopRequested && !ExceptionUtils.isNormalCancellation(e)) {
                    scope.launch { pause(e) }
                }
            }
        }
    }

    private suspend fun runYtDlp(useBrowserCookies: Boolean, scope: CoroutineScope) {
        val resultFile = downloadManager.downloadDataFolder.resolve(id.toString()).resolve("ytdlp-result.txt")
        resultFile.parentFile.mkdirs()
        resultFile.delete()
        val args = buildArgs(useBrowserCookies, resultFile)
        val p = processLock.withLock {
            YtDlpBinary.newProcessBuilder(args).redirectErrorStream(true).start().also { process = it }
        }
        downloadItem.status = DownloadStatus.Downloading
        onDownloadResumed()
        startAutoSaver()
        val progress = ProgressTracker(downloadedBytes.get())
        currentTracker = progress
        val tail = ArrayDeque<String>()
        p.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (tail.size >= 30) tail.removeFirst()
                tail.addLast(line)
                handleLine(line, progress)
            }
        }
        val exit = runInterruptible { p.waitFor() }
        processLock.withLock { process = null }
        if (exit != 0) {
            val error = YtDlpBinary.lastError(tail.joinToString("\n")) ?: "yt-dlp failed with code $exit"
            if (!useBrowserCookies && YtDlpBinary.needsCookies(error)) {
                runYtDlp(useBrowserCookies = true, scope = scope)
                return
            }
            throw YtDlpException(error)
        }
        val produced = resultFile.takeIf { it.exists() }?.readLines(Charsets.UTF_8)
            ?.lastOrNull { it.isNotBlank() }?.trim()?.let(::File)
        if (produced != null && produced.exists()) {
            destination.adoptResult(produced)
        }
        if (!destination.outputFile.exists()) {
            throw YtDlpException("yt-dlp finished but the output file was not found")
        }
        downloadedBytes.set(destination.outputFile.length())
        saveState()
        onDownloadFinished()
    }

    private fun handleLine(line: String, progress: ProgressTracker) {
        when {
            line.startsWith(PROGRESS_PREFIX) -> {
                val fields = line.removePrefix(PROGRESS_PREFIX).split('|')
                val done = fields.getOrNull(0)?.toDoubleOrNull()?.toLong() ?: return
                val total = fields.getOrNull(1)?.toDoubleOrNull()?.toLong()
                    ?: fields.getOrNull(2)?.toDoubleOrNull()?.toLong()
                val formatId = fields.getOrNull(3).orEmpty()
                downloadedBytes.set(progress.update(formatId, done, total))
                progress.totalEstimate()?.let { estimate ->
                    if (estimate > downloadItem.contentLength) downloadItem.contentLength = estimate
                }
            }
            // merging video+audio / converting audio, the file is almost ready
            line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[VideoConvertor]") -> {
                _status.update { DownloadJobStatus.PreparingFile(null) }
            }
        }
    }

    private fun buildArgs(useBrowserCookies: Boolean, resultFile: File): List<String> {
        val quality = downloadItem.quality
        return buildList {
            addAll(YtDlpBinary.commonArgs(downloadItem, useBrowserCookies))
            add("--newline")
            add("--continue")
            add("--no-mtime")
            add("--windows-filenames")
            add("--retries"); add("10")
            add("--fragment-retries"); add("10")
            add("-N"); add(getThreadCount().toString())
            effectiveSpeedLimit()?.let { add("--limit-rate"); add(it.toString()) }
            YtDlpBinary.findFfmpeg()?.let { add("--ffmpeg-location"); add(it.absolutePath) }
            add("-f"); add(YtDlpQuality.toFormatSelector(quality))
            if (YtDlpQuality.isAudioOnly(quality)) {
                add("-x"); add("--audio-format"); add("m4a")
            } else {
                add("--merge-output-format"); add("mp4")
                if (YtDlpOptions.downloadSubtitles) {
                    // embedded, so the video stays a single file that players pick the subtitles from
                    add("--write-subs"); add("--write-auto-subs")
                    add("--sub-langs"); add(YtDlpOptions.subtitleLanguages)
                    if (YtDlpOptions.subtitlesAsFile) {
                        // "Video.en.srt" next to "Video.mp4", players load it automatically
                        add("--convert-subs"); add("srt")
                    } else {
                        add("--embed-subs")
                    }
                    // one missing language must not fail the whole video
                    add("--ignore-errors")
                }
            }
            add("--progress-template")
            add("download:${PROGRESS_PREFIX}%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(info.format_id)s")
            add("--print-to-file"); add("after_move:filepath"); add(resultFile.absolutePath)
            add("-o"); add(destination.outputTemplate)
            add("--"); add(downloadItem.link)
        }
    }

    private fun getThreadCount(): Int {
        return (downloadItem.preferredConnectionCount ?: downloadManager.settings.defaultThreadCount).coerceIn(1, 32)
    }

    private fun effectiveSpeedLimit(): Long? {
        return listOf(downloadItem.speedLimit, downloadManager.settings.globalSpeedLimit)
            .filter { it > 0 }
            .minOrNull()
    }

    private suspend fun stopProcess() {
        stopRequested = true
        processLock.withLock {
            process?.let(YtDlpBinary::killTree)
            process = null
        }
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
        processJob = null
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        stopProcess()
        downloadItem.downloadedBytes = downloadedBytes.get()
        onDownloadCanceled(throwable)
    }

    override suspend fun reset() {
        pause(CancellationException())
        destination.cleanUpJunkFiles()
        downloadedBytes.set(0)
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
            downloadItem.downloadedBytes = downloadedBytes.get()
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
        val previousFile = downloadManager.calculateOutputFile(previous)
        val newFile = downloadManager.calculateOutputFile(newItem)
        val needsRestart = previous.link != newItem.link || previous.quality != newItem.quality ||
                previous.speedLimit != newItem.speedLimit ||
                previous.preferredConnectionCount != newItem.preferredConnectionCount
        val wasActive = isDownloadActive.value
        if ((previousFile != newFile || needsRestart) && wasActive) {
            pause(CancellationException())
        }
        if (previousFile != newFile) {
            destination.moveOutput(newFile)
        }
        downloadItem.applyFrom(newItem)
        initializeDestination()
        saveState()
        if (wasActive && (previousFile != newFile || needsRestart)) {
            resume()
        }
        return downloadItem
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {}

    override fun onDownloadFinishedBeforeSave() {
        downloadItem.contentLength = destination.outputFile.length()
        downloadItem.downloadedBytes = downloadItem.contentLength
    }

    /**
     * yt-dlp reports progress per stream (video, then audio), so the overall
     * progress is the finished streams plus the current one.
     */
    private class ProgressTracker(initial: Long) {
        private var finishedStreams = 0L
        private var currentFormat: String? = null
        private var currentDone = 0L
        private var currentTotal: Long? = null
        private val resumedFrom = initial
        // every stream yt-dlp downloads (video, audio), in order, with its own progress
        val streams = java.util.concurrent.ConcurrentHashMap<String, StreamProgress>()
        private val order = java.util.concurrent.CopyOnWriteArrayList<String>()

        fun update(formatId: String, done: Long, total: Long?): Long {
            if (currentFormat != null && formatId != currentFormat) {
                finishedStreams += currentTotal ?: currentDone
                currentFormat?.let { old -> streams[old]?.let { streams[old] = it.copy(done = it.total ?: it.done, finished = true) } }
            }
            if (formatId !in order) order.add(formatId)
            streams[formatId] = StreamProgress(done = done, total = total, finished = false)
            currentFormat = formatId
            currentDone = done
            currentTotal = total
            val value = finishedStreams + done
            // before yt-dlp prints the first line of a resumed download, keep showing the saved progress
            return if (value == 0L) resumedFrom else value
        }

        fun totalEstimate(): Long? = currentTotal?.let { finishedStreams + it }

        fun orderedStreams(): List<StreamProgress> = order.mapNotNull { streams[it] }
    }

    data class StreamProgress(val done: Long, val total: Long?, val finished: Boolean)

    @Volatile
    private var currentTracker: ProgressTracker? = null

    /** the streams of this video (usually video + audio) with their real progress, for the download window */
    fun getStreams(): List<StreamProgress> = currentTracker?.orderedStreams().orEmpty()

    companion object {
        private const val PROGRESS_PREFIX = "ABDM|"
    }
}
