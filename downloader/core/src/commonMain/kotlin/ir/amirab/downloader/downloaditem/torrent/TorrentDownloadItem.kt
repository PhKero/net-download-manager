package ir.amirab.downloader.downloaditem.torrent

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URLDecoder

/** magnet links, http(s) links to .torrent files, or a .torrent file on disk */
object TorrentLinks {
    fun isMagnet(link: String) = link.trim().startsWith("magnet:?", ignoreCase = true)

    fun isTorrentFileLink(link: String): Boolean {
        val l = link.trim().lowercase()
        return (l.startsWith("http://") || l.startsWith("https://")) &&
                l.substringBefore('?').substringBefore('#').endsWith(".torrent")
    }

    fun isLocalTorrentFile(link: String) = link.lowercase().endsWith(".torrent") && File(link).isFile

    fun isTorrent(link: String) = isMagnet(link) || isTorrentFileLink(link) || isLocalTorrentFile(link)

    /** display name from a magnet's dn= or from the .torrent file name */
    fun suggestedName(link: String): String? {
        if (isMagnet(link)) {
            return link.substringAfter('?').split('&')
                .firstOrNull { it.startsWith("dn=") }
                ?.removePrefix("dn=")
                ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrDefault(it) }
                ?.takeIf { it.isNotBlank() }
        }
        return link.substringBefore('?').substringAfterLast('/').substringAfterLast('\\')
            .removeSuffix(".torrent").takeIf { it.isNotBlank() }
    }
}

@Serializable
@SerialName("torrent")
data class TorrentDownloadCredentials(
    override val link: String,
    override val downloadPage: String? = null,
) : IDownloadCredentials {
    override fun copy(link: Option<String>, downloadPage: Option<String?>): IDownloadCredentials {
        return copy(
            link = link.getOrElse { this.link },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
        )
    }

    override fun validateCredentials() {
        require(TorrentLinks.isTorrent(link)) { "not a magnet link or .torrent file" }
    }
}

/**
 * A torrent is saved as a folder `folder/name` holding everything the torrent contains.
 */
@Serializable
data class TorrentDownloadItem(
    override var id: Long,
    override var folder: String,
    override var name: String,
    override var link: String,
    override var contentLength: Long = -1,
    override var downloadPage: String? = null,
    override var dateAdded: Long,
    override var startTime: Long? = null,
    override var completeTime: Long? = null,
    override var status: DownloadStatus = DownloadStatus.Added,
    override var preferredConnectionCount: Int? = null,
    override var speedLimit: Long = 0,
    override var fileChecksum: String? = null,
    var downloadedBytes: Long = 0,
) : IDownloadItem {
    override fun copy(
        id: Option<Long>,
        folder: Option<String>,
        name: Option<String>,
        link: Option<String>,
        contentLength: Option<Long>,
        downloadPage: Option<String?>,
        dateAdded: Option<Long>,
        startTime: Option<Long?>,
        completeTime: Option<Long?>,
        status: Option<DownloadStatus>,
        preferredConnectionCount: Option<Int?>,
        speedLimit: Option<Long>,
        fileChecksum: Option<String?>
    ): IDownloadItem {
        return copy(
            id = id.getOrElse { this.id },
            folder = folder.getOrElse { this.folder },
            name = name.getOrElse { this.name },
            link = link.getOrElse { this.link },
            contentLength = contentLength.getOrElse { this.contentLength },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
            dateAdded = dateAdded.getOrElse { this.dateAdded },
            startTime = startTime.getOrElse { this.startTime },
            completeTime = completeTime.getOrElse { this.completeTime },
            status = status.getOrElse { this.status },
            preferredConnectionCount = preferredConnectionCount.getOrElse { this.preferredConnectionCount },
            speedLimit = speedLimit.getOrElse { this.speedLimit },
            fileChecksum = fileChecksum.getOrElse { this.fileChecksum },
        )
    }

    override fun copy(link: Option<String>, downloadPage: Option<String?>): TorrentDownloadItem {
        return copy(
            link = link.getOrElse { this.link },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
        )
    }

    override fun validateItem() = validateCredentials()

    override fun validateCredentials() {
        require(TorrentLinks.isTorrent(link)) { "not a magnet link or .torrent file" }
    }

    override fun withCredentials(credentials: IDownloadCredentials): TorrentDownloadItem {
        link = credentials.link
        downloadPage = credentials.downloadPage
        return this
    }
}

fun TorrentDownloadItem.applyFrom(other: TorrentDownloadItem) {
    link = other.link
    downloadPage = other.downloadPage
    id = other.id
    folder = other.folder
    name = other.name
    contentLength = other.contentLength
    dateAdded = other.dateAdded
    startTime = other.startTime
    completeTime = other.completeTime
    status = other.status
    preferredConnectionCount = other.preferredConnectionCount
    speedLimit = other.speedLimit
    fileChecksum = other.fileChecksum
    downloadedBytes = other.downloadedBytes
}
