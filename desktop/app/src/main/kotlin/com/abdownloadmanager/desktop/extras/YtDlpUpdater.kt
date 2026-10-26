package com.abdownloadmanager.desktop.extras

import ir.amirab.downloader.downloaditem.ytdlp.YtDlpBinary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sites change often and old yt-dlp versions stop working, so it is updated in the background.
 * A pip installation (yt-dlp.exe inside a Python "Scripts" folder) can't update itself; pip does it.
 */
object YtDlpUpdater {
    sealed interface Result {
        data class Updated(val from: String?, val to: String?) : Result
        data object UpToDate : Result
        data class Failed(val reason: String) : Result
    }

    private fun run(vararg command: String, timeoutMinutes: Long = 5): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).apply {
            environment()["PYTHONIOENCODING"] = "utf-8"
        }.start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return -1 to "timed out"
        }
        return process.exitValue() to output
    }

    private fun version(exe: File): String? {
        return runCatching { run(exe.absolutePath, "--version", timeoutMinutes = 1) }
            .getOrNull()?.takeIf { it.first == 0 }?.second?.trim()?.lines()?.lastOrNull()
    }

    suspend fun update(): Result = withContext(Dispatchers.IO) {
        val exe = YtDlpBinary.findYtDlp() ?: return@withContext Result.Failed("yt-dlp not found")
        val before = version(exe)
        val scriptsDir = exe.parentFile
        val python = scriptsDir?.takeIf { it.name.equals("Scripts", ignoreCase = true) }
            ?.parentFile?.let { File(it, "python.exe") }?.takeIf { it.isFile }
        val (code, output) = runInterruptible {
            if (python != null) {
                run(python.absolutePath, "-m", "pip", "install", "--upgrade", "--quiet", "--disable-pip-version-check", "yt-dlp")
            } else {
                run(exe.absolutePath, "-U")
            }
        }
        if (code != 0) {
            return@withContext Result.Failed(output.lines().lastOrNull { it.isNotBlank() } ?: "update failed")
        }
        val after = version(exe)
        if (before != null && after != null && before != after) Result.Updated(before, after) else Result.UpToDate
    }
}
