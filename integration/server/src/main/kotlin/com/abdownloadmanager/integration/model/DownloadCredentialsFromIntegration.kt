package com.abdownloadmanager.integration.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
sealed interface IDownloadCredentialsFromIntegration {
    val link: String
    val downloadPage: String?

    val suggestedName: String?
}

@SerialName("http")
@Serializable
data class HttpDownloadCredentialsFromIntegration(
    override val link: String,
    val headers: Map<String, String>? = null,
    override val downloadPage: String? = null,
    override val suggestedName: String? = null,
) : IDownloadCredentialsFromIntegration

@SerialName("hls")
@Serializable
data class HLSDownloadCredentialsFromIntegration(
    override val link: String,
    val headers: Map<String, String>? = null,
    override val downloadPage: String? = null,
    override val suggestedName: String? = null,
) : IDownloadCredentialsFromIntegration

/** a video page (youtube etc.) that the app resolves with yt-dlp */
@SerialName("ytdlp")
@Serializable
data class YtDlpDownloadCredentialsFromIntegration(
    override val link: String,
    val headers: Map<String, String>? = null,
    override val downloadPage: String? = null,
    override val suggestedName: String? = null,
    val quality: String? = null,
    /** the link is a playlist; the app adds every video of it */
    val playlist: Boolean = false,
) : IDownloadCredentialsFromIntegration

@Serializable
data class AddDownloadOptionsFromIntegration(
    val silentAdd: Boolean = false,
    val silentStart: Boolean = false,
)

@Serializable
data class AddDownloadsFromIntegration(
    val items: List<IDownloadCredentialsFromIntegration>,
    val options: AddDownloadOptionsFromIntegration = AddDownloadOptionsFromIntegration()
) {
    companion object {
        fun createFromRequest(json: Json, jsonData: String): AddDownloadsFromIntegration {
            return try {
                json.decodeFromString<AddDownloadsFromIntegration>(jsonData)
            } catch (_: SerializationException) {
                // TODO Remove this after a while
                AddDownloadsFromIntegration(
                    items = json.decodeFromString<List<HttpDownloadCredentialsFromIntegration>>(jsonData),
                    AddDownloadOptionsFromIntegration(
                        silentAdd = false,
                        silentStart = false,
                    )
                )
            }
        }
    }
}
