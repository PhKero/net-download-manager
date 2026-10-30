package com.abdownloadmanager.shared.util

import ir.amirab.util.ifThen
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isWindows

/**
 * This utility class removes characters that are not supported by the current OS.
 *
 * If additional modifications are required, it may be better to create a separate class for each platform.
 */
object FilenameFixer {
    private const val DEFAULT_REPLACEMENT_CHAR = "_"
    private val illegalChars by lazy {
        when (Platform.getCurrentPlatform()) {
            Platform.Desktop.Windows -> setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
            Platform.Desktop.MacOS -> setOf(':')
            Platform.Desktop.Linux,
            Platform.Android,
                -> setOf('/')
        }
    }

    fun fix(name: String): String {
        return buildString {
            name.forEach { char ->
                append(
                    if (char in illegalChars) {
                        DEFAULT_REPLACEMENT_CHAR
                    } else {
                        char
                    }
                )
            }
        }
            .ifThen(Platform.isWindows()) {
                trimEnd(' ', '.')
            }
            .let(::limitLength)
    }

    // Windows refuses names over 255 characters ("Write Error"); links with long tokens produce such names.
    // Keep it well under that so the whole path also stays short.
    private const val MAX_NAME_LENGTH = 120

    /** shortens a too long name and keeps its extension */
    fun limitLength(name: String): String {
        if (name.length <= MAX_NAME_LENGTH) return name
        val extension = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 8 && it.all(Char::isLetterOrDigit) }
        val suffix = extension?.let { ".$it" }.orEmpty()
        val base = name.dropLast(suffix.length).take(MAX_NAME_LENGTH - suffix.length).trimEnd(' ', '.')
        return base + suffix
    }
}
