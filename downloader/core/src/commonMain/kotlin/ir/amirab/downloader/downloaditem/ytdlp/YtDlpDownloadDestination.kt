package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.destination.DestWriter
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.part.DownloadPart
import java.io.File

/**
 * yt-dlp writes the files itself, so this destination only knows where the output goes
 * and how to clean up the temporary files yt-dlp leaves behind.
 */
class YtDlpDownloadDestination(
    outputFile: File,
) : DownloadDestination(outputFile) {
    /** yt-dlp output template; the real extension is decided by yt-dlp, we rename afterwards */
    val outputTemplate: String
        get() = File(outputFile.parentFile, "${baseName()}.%(ext)s").absolutePath

    fun baseName(): String = outputFile.nameWithoutExtension

    override fun getWriterFor(part: DownloadPart): DestWriter {
        throw UnsupportedOperationException("yt-dlp writes its own files")
    }

    override fun canGetFileWriter() = false
    override suspend fun prepareFile(onProgressUpdate: (Int?) -> Unit) {
        prepareDestinationFolder(outputFile)
    }

    override suspend fun isDownloadedPartsIsValid() = true
    override fun flush() {}

    private fun junkFiles(): List<File> {
        val base = baseName()
        val folder = outputFile.parentFile ?: return emptyList()
        val tempPattern = Regex("^" + Regex.escape(base) + """(\.f[\w-]+)?\.\w+(\.part(-Frag\d+)?|\.ytdl|\.temp)$""")
        // format streams are "name.f137.mp4"; the digit keeps subtitles like "name.fr.srt" safe
        val formatPattern = Regex("^" + Regex.escape(base) + """\.f\d[\w-]*\.\w+$""")
        return folder.listFiles().orEmpty().filter {
            it.isFile && it != outputFile && (tempPattern.matches(it.name) || formatPattern.matches(it.name))
        }
    }

    override fun cleanUpJunkFiles() {
        junkFiles().forEach { it.delete() }
    }

    /** moves the file yt-dlp produced to the name the user picked */
    fun adoptResult(produced: File) {
        val target = outputFile
        if (produced.canonicalFile == target.canonicalFile) return
        if (target.exists()) target.delete()
        if (!produced.renameTo(target)) {
            produced.copyTo(target, overwrite = true)
            produced.delete()
        }
    }
}
