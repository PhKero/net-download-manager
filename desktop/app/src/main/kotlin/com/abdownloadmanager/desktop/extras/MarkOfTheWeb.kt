package com.abdownloadmanager.desktop.extras

import java.io.File

/**
 * Tags a downloaded file as "came from the internet" (Zone.Identifier stream, like browsers do),
 * so Windows SmartScreen / Office protected view check it before it runs.
 */
object MarkOfTheWeb {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    fun apply(file: File, hostUrl: String?, referrerUrl: String?) {
        if (!isWindows || !file.isFile) return
        runCatching {
            val text = buildString {
                append("[ZoneTransfer]\r\n")
                // 3 = Internet zone
                append("ZoneId=3\r\n")
                referrerUrl?.takeIf { it.startsWith("http") }?.let { append("ReferrerUrl=$it\r\n") }
                hostUrl?.takeIf { it.startsWith("http") }?.let { append("HostUrl=$it\r\n") }
            }
            // NTFS alternate data stream; silently skipped on file systems without streams (FAT32 / exFAT)
            File(file.path + ":Zone.Identifier").writeText(text, Charsets.US_ASCII)
        }
    }
}
