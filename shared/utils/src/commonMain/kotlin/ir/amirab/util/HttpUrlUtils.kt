package ir.amirab.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

object HttpUrlUtils {
    fun createURL(url: String): HttpUrl {
        return url.toHttpUrl()
    }

    fun isValidUrl(link: String): Boolean {
        return runCatching { createURL(link) }.isSuccess
    }

    // ---- finding the real file name in a link ----
    // Sites hide the name in many ways; we look in this order and never use a meaningless token as the name:
    //  1. query parameters (filename=, file=, name=, title=, response-content-disposition=...)
    //  2. tokens (JWT or base64 JSON) in the path or the query, which often carry the name inside
    //  3. a path part with a file extension (also when it is not the last part, e.g. /movie.mp4/download)
    //  4. the last path part, only if it looks like a real name
    //  5. the site name (the caller adds an extension from the server's file type)

    private val jsonStringField = Regex(""""([A-Za-z_]+)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val fileNameLike = Regex("""^[^/\\?*:|"<>]{1,240}\.[A-Za-z0-9]{1,6}$""")
    private val base64ish = Regex("""^[A-Za-z0-9_\-+/=.%]+$""")
    private val nameKeys = listOf("filename", "file_name", "fileName", "fname", "fn", "name", "file", "title", "dl", "download", "sub")
    private val queryNameKeys = listOf("filename", "file_name", "filename*", "fname", "fn", "name", "file", "title", "dl", "download")

    private fun decodeBase64Text(text: String): String? {
        val clean = text.trimEnd('=')
        for (decoder in listOf(java.util.Base64.getUrlDecoder(), java.util.Base64.getDecoder())) {
            val result = runCatching { String(decoder.decode(clean), Charsets.UTF_8) }.getOrNull()
            if (result != null && result.any { it == '{' }) return result
        }
        return null
    }

    private fun unescapeJson(value: String): String {
        return value
            .replace("\\/", "/")
            .replace(Regex("""\\u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\\"", "\"")
    }

    /** a file name found inside a JSON text (a decoded token) */
    private fun nameFromJson(json: String): String? {
        val fields = jsonStringField.findAll(json).map { it.groupValues[1] to unescapeJson(it.groupValues[2]) }.toList()
        val byKey = fields.associate { (k, v) -> k.lowercase() to v }
        return nameKeys.firstNotNullOfOrNull { key -> byKey[key.lowercase()]?.trim()?.takeIf { fileNameLike.matches(it) } }
            ?: fields.map { it.second.trim() }.firstOrNull { fileNameLike.matches(it) }
    }

    /** a token (JWT, or base64 of JSON) that carries a file name */
    private fun nameFromToken(text: String): String? {
        if (text.length < 16 || !base64ish.matches(text)) return null
        val parts = text.split('.')
        val candidates = if (parts.size in 2..3 && parts[0].startsWith("eyJ")) listOf(parts[1]) else listOf(text) + parts
        for (part in candidates) {
            val json = decodeBase64Text(part) ?: continue
            nameFromJson(json)?.let { return it }
        }
        return null
    }

    /** filename="x" / filename*=UTF-8''x inside a content-disposition text (used by S3, Google storage...) */
    private fun nameFromDisposition(text: String): String? {
        Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(text)?.let {
            return runCatching { java.net.URLDecoder.decode(it.groupValues[1].trim('"', ' '), "UTF-8") }.getOrNull()
        }
        return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
    }

    /** a part of a link that is clearly not a file name: a long code without an extension */
    fun looksLikeToken(name: String): Boolean {
        if (fileNameLike.matches(name)) return false
        if (name.length > 40) return true
        val hasDigit = name.any { it.isDigit() }
        val hasUpper = name.any { it.isUpperCase() }
        val hasLower = name.any { it.isLowerCase() }
        return name.length >= 20 && ' ' !in name && hasDigit && hasUpper && hasLower
    }

    fun extractNameFromLink(link: String): String? {
        fun decode(text: String) = runCatching { FilenameDecoder.decode(text, Charsets.UTF_8) }.getOrDefault(text)

        fun extractNameFromQuery(url: HttpUrl): String? {
            val names = url.queryParameterNames
            for (key in queryNameKeys) {
                val realKey = names.firstOrNull { it.equals(key, ignoreCase = true) } ?: continue
                val value = url.queryParameter(realKey)?.trim()?.takeIf { it.isNotBlank() } ?: continue
                if (fileNameLike.matches(value)) return value
            }
            names.firstOrNull { it.contains("content-disposition", ignoreCase = true) }
                ?.let { url.queryParameter(it) }
                ?.let(::nameFromDisposition)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            return null
        }

        fun extractNameFromTokens(url: HttpUrl): String? {
            val texts = url.pathSegments.asReversed() + url.queryParameterNames.mapNotNull { url.queryParameter(it) }
            return texts.firstNotNullOfOrNull { nameFromToken(it) }
        }

        fun extractNameFromPathWithExtension(url: HttpUrl): String? {
            return url.pathSegments.asReversed()
                .map { decode(it) }
                .firstOrNull { fileNameLike.matches(it) && !it.endsWith(".php") && !it.endsWith(".html") && !it.endsWith(".htm") && !it.endsWith(".aspx") && !it.endsWith(".asp") && !it.endsWith(".jsp") }
        }

        fun extractNameFromLastPath(url: HttpUrl): String? {
            return url.pathSegments
                .lastOrNull { it.isNotBlank() }
                ?.let { decode(it) }
                ?.takeIf { !looksLikeToken(it) }
        }

        return runCatching {
            createURL(link)
        }.map { url ->
            extractNameFromQuery(url)
                ?: extractNameFromTokens(url)
                ?: extractNameFromPathWithExtension(url)
                ?: extractNameFromLastPath(url)
                ?: url.host.replace('.', '_')
        }.getOrNull()
    }

    fun getHost(url: String): String? {
        return kotlin.runCatching {
            createURL(url).host
        }.getOrNull()
    }

}
