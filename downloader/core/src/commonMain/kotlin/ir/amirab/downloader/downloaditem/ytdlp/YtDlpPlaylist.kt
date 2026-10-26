package ir.amirab.downloader.downloaditem.ytdlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class YtDlpPlaylistEntry(
    val url: String,
    val title: String?,
)

/** result of `yt-dlp --flat-playlist -J <playlist>` */
data class YtDlpPlaylist(
    val title: String?,
    val entries: List<YtDlpPlaylistEntry>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun isPlaylistJson(output: String): Boolean {
            val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("{") } ?: return false
            return runCatching {
                json.parseToJsonElement(line).jsonObject.string("_type") == "playlist"
            }.getOrDefault(false)
        }

        fun parse(output: String): YtDlpPlaylist {
            val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
                ?: throw YtDlpException("yt-dlp returned no playlist information")
            val root = json.parseToJsonElement(line).jsonObject
            val entries = (root["entries"] as? JsonArray).orEmpty().mapNotNull { element ->
                val e = element as? JsonObject ?: return@mapNotNull null
                val title = e.string("title")
                // deleted / private videos stay in playlists with placeholder titles
                if (title == "[Deleted video]" || title == "[Private video]") return@mapNotNull null
                val url = e.string("url")?.takeIf { it.startsWith("http") }
                    ?: e.string("webpage_url")
                    ?: e.string("id")?.let { id ->
                        if (e.string("ie_key") == "Youtube") "https://www.youtube.com/watch?v=$id" else null
                    }
                    ?: return@mapNotNull null
                YtDlpPlaylistEntry(url = url, title = title)
            }
            if (entries.isEmpty()) throw YtDlpException("The playlist is empty or could not be read")
            return YtDlpPlaylist(title = root.string("title"), entries = entries)
        }

        private fun JsonObject.string(key: String): String? {
            val p = this[key] as? JsonPrimitive ?: return null
            return if (p.isString) p.content else p.content.takeIf { it != "null" }
        }
    }
}
