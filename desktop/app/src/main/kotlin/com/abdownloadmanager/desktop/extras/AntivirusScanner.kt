package com.abdownloadmanager.desktop.extras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** scans a finished download with Microsoft Defender (no admin rights needed) */
object AntivirusScanner {
    sealed interface Result {
        data object Clean : Result
        data object ThreatFound : Result
        data class Failed(val reason: String) : Result
    }

    private fun findMpCmdRun(): File? {
        // the platform folder holds the up to date engine; pick the newest version
        val platform = File(System.getenv("ProgramData") ?: "C:\\ProgramData", "Microsoft\\Windows Defender\\Platform")
        val newest = platform.listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
            .map { File(it, "MpCmdRun.exe") }
            .firstOrNull { it.isFile }
        return newest ?: File(System.getenv("ProgramFiles") ?: "C:\\Program Files", "Windows Defender\\MpCmdRun.exe")
            .takeIf { it.isFile }
    }

    suspend fun scan(file: File): Result = withContext(Dispatchers.IO) {
        val scanner = findMpCmdRun() ?: return@withContext Result.Failed("Microsoft Defender was not found")
        if (!file.exists()) return@withContext Result.Failed("file not found")
        val process = ProcessBuilder(
            scanner.absolutePath, "-Scan", "-ScanType", "3", "-File", file.absolutePath,
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        val finished = runInterruptible { process.waitFor(10, TimeUnit.MINUTES) }
        if (!finished) {
            process.destroyForcibly()
            return@withContext Result.Failed("scan timed out")
        }
        // 0 = no threat, 2 = threat found (Defender quarantines it itself)
        when (process.exitValue()) {
            0 -> Result.Clean
            2 -> Result.ThreatFound
            else -> Result.Failed("Defender exited with code ${process.exitValue()}")
        }
    }
}
