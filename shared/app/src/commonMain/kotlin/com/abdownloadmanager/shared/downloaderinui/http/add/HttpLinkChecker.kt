package com.abdownloadmanager.shared.downloaderinui.http.add

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.util.FilenameFixer
import com.abdownloadmanager.shared.downloaderinui.LinkChecker
import ir.amirab.downloader.connection.HttpDownloaderClient
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HttpLinkChecker(
    initialCredentials: HttpDownloadCredentials = HttpDownloadCredentials.empty(),
    private val client: HttpDownloaderClient,
) : LinkChecker<HttpDownloadCredentials, HttpResponseInfo, DownloadSize.Bytes>(initialCredentials) {
    private val _suggestedName = MutableStateFlow(null as String?)
    override val suggestedName = _suggestedName.asStateFlow()

    private val _length = MutableStateFlow(null as Long?)
    override val downloadSize = _length.mapStateFlow {
        it?.let(DownloadSize::Bytes)
    }

    override fun infoUpdated(responseInfo: HttpResponseInfo?) {
        updateNameAndLength(responseInfo)
    }

    override suspend fun actualCheck(credentials: HttpDownloadCredentials): HttpResponseInfo {
        return client.test(credentials)
    }

    private fun updateNameAndLength(responseInfo: HttpResponseInfo?) {
        val suggestedName = (responseInfo?.fileName ?: HttpUrlUtils.extractNameFromLink(credentials.value.link))
            ?.let(FilenameFixer::fix)
            ?.let { withExtensionFromContentType(it, responseInfo) }
        val length = responseInfo?.run {
            totalLength.takeIf { isSuccessFul }
        }
        _suggestedName.update { suggestedName }
        _length.update { length }
    }

    /** a name taken from a link often has no extension (e.g. a token); add the one the server's file type tells */
    private fun withExtensionFromContentType(name: String, responseInfo: HttpResponseInfo?): String {
        if ('.' in name.takeLast(9)) return name
        val type = responseInfo?.responseHeaders?.get("content-type")
            ?.substringBefore(';')?.trim()?.lowercase() ?: return name
        val extension = when (type) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            "video/mp2t" -> "ts"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac" -> "flac"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/x-rar-compressed", "application/vnd.rar" -> "rar"
            "application/x-7z-compressed" -> "7z"
            "application/vnd.android.package-archive" -> "apk"
            "application/x-msdownload" -> "exe"
            "application/x-msi" -> "msi"
            else -> return name
        }
        return FilenameFixer.limitLength("$name.$extension")
    }
}
