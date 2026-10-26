package com.abdownloadmanager.integration.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoRequest(
    val link: String,
    val downloadPage: String? = null,
)

@Serializable
data class VideoQualityModel(
    /** token sent back with the download request (best, h1080, audio...) */
    val quality: String,
    val label: String,
    /** estimated bytes, null when the site doesn't tell */
    val size: Long? = null,
)

@Serializable
data class VideoInfoResponse(
    /** "video", "playlist" or "error" */
    val type: String,
    val title: String? = null,
    val duration: Double? = null,
    val qualities: List<VideoQualityModel> = emptyList(),
    /** number of items when [type] is "playlist" */
    val count: Int? = null,
    val error: String? = null,
)
