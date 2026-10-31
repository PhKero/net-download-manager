package com.abdownloadmanager.desktop.cli

/**
 * Lets the app's exe take a download from the command line, the way people script IDM:
 *
 *     ABDownloadManager.exe /d URL [/p folder] [/f file name] [/n] [/a]
 *
 *  /d URL   the file to download
 *  /p path  where to save it
 *  /f name  the file name
 *  /n       silent: no dialog, the download starts right away (or is only added with /a)
 *  /a       add it to the main queue, don't start it
 *
 * Without /n the normal "Download File Info" dialog opens. Our own `download add ...` command
 * (see [com.abdownloadmanager.desktop.cli.download.Download]) is also reachable from the exe now.
 */
object IdmStyleArgs {
    private const val MAIN_QUEUE_ID = "0"

    /** the CLI arguments to run instead of starting the window, or null when this is a normal start */
    fun toCliArgs(args: Array<String>): Array<String>? {
        if (args.firstOrNull() == "download") return args
        val lower = args.map { it.lowercase() }
        val d = lower.indexOf("/d")
        val link = args.getOrNull(d + 1)?.takeIf { d >= 0 } ?: return null
        fun valueOf(flag: String) = lower.indexOf(flag).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
        val silent = "/n" in lower
        val addOnly = "/a" in lower
        return buildList {
            addAll(listOf("download", "add", "http", "--link", link))
            valueOf("/p")?.let { addAll(listOf("--folder", it)) }
            valueOf("/f")?.let { addAll(listOf("--name", it)) }
            when {
                !silent -> add("--ask")
                addOnly -> addAll(listOf("--queue", MAIN_QUEUE_ID))
                else -> add("--start")
            }
        }.toTypedArray()
    }
}
