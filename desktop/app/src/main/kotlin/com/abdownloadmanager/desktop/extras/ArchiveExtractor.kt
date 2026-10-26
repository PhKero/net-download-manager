package com.abdownloadmanager.desktop.extras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** extracts finished archives into a folder named after the archive, using 7-Zip (or WinRAR for rar) */
object ArchiveExtractor {
    sealed interface Result {
        data class Extracted(val folder: File) : Result
        data object NotAnArchive : Result
        data class Failed(val reason: String) : Result
    }

    private val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

    private fun programFiles(vararg relative: String): File? {
        val roots = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), "C:\\Program Files")
        return roots.flatMap { root -> relative.map { File(root, it) } }.firstOrNull { it.isFile }
    }

    private fun sevenZip() = programFiles("7-Zip\\7z.exe")
    private fun unrar() = programFiles("WinRAR\\UnRAR.exe")

    /**
     * Only the first volume of a split archive is extracted ("x.part1.rar", "x.7z.001");
     * the other volumes are read by the extractor itself.
     */
    fun isArchive(file: File): Boolean {
        val name = file.name.lowercase()
        if (Regex("""\.part(\d+)\.rar$""").find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it != 1 } == true) return false
        if (Regex("""\.(7z|zip)\.(\d{3})$""").find(name)?.groupValues?.get(2)?.let { it != "001" } == true) return false
        if (Regex("""\.(7z|zip)\.001$""").containsMatchIn(name)) return true
        return name.substringAfterLast('.', "") in archiveExtensions
    }

    private fun targetFolder(file: File): File {
        var base = file.name
        listOf(Regex("""\.part\d+\.rar$""", RegexOption.IGNORE_CASE), Regex("""\.(7z|zip)\.\d{3}$""", RegexOption.IGNORE_CASE), Regex("""\.tar\.(gz|bz2|xz)$""", RegexOption.IGNORE_CASE))
            .firstOrNull { it.containsMatchIn(base) }
            ?.let { base = base.replace(it, "") }
            ?: run { base = base.substringBeforeLast('.') }
        return File(file.parentFile, base.ifBlank { "extracted" })
    }

    suspend fun extract(file: File, password: String? = null): Result = withContext(Dispatchers.IO) {
        if (!file.isFile || !isArchive(file)) return@withContext Result.NotAnArchive
        val target = targetFolder(file)
        val isRar = file.name.lowercase().endsWith(".rar")
        val command = sevenZip()?.let {
            listOf(it.absolutePath, "x", file.absolutePath, "-o${target.absolutePath}", "-y", "-p${password.orEmpty()}")
        } ?: unrar()?.takeIf { isRar }?.let {
            listOf(it.absolutePath, "x", "-o+", "-p${password ?: "-"}", file.absolutePath, target.absolutePath + File.separator)
        } ?: return@withContext Result.Failed("7-Zip is not installed")
        target.mkdirs()
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = runInterruptible { process.waitFor(30, TimeUnit.MINUTES) }
        if (!finished) {
            process.destroyForcibly()
            return@withContext Result.Failed("extraction timed out")
        }
        if (process.exitValue() != 0) {
            // don't leave an empty folder behind
            if (target.listFiles().isNullOrEmpty()) target.delete()
            val wrongPassword = output.contains("Wrong password", ignoreCase = true) || output.contains("encrypted", ignoreCase = true)
            return@withContext Result.Failed(if (wrongPassword) "the archive is password protected" else "the archive could not be extracted")
        }
        Result.Extracted(target)
    }
}
