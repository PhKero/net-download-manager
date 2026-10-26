package com.abdownloadmanager.integration

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import com.abdownloadmanager.integration.model.VideoInfoRequest
import com.abdownloadmanager.integration.model.VideoInfoResponse

interface IntegrationHandler{
    /**
     * meant to be used by the browser / or when the app needs to use gui
     * the app gui should be involved
     */
    suspend fun addDownloadByGui(request: AddDownloadsFromIntegration)


    fun listQueues(): List<ApiQueueModel>

    /**
     * manually add download
     * all the necessary inputs provided by the cli/api
     * simply add the download using download system
     */
    suspend fun addDownload(task: NewDownloadTask): Long

    /** what is on a page (single video with its qualities, or a playlist), for the browser's quality menu */
    suspend fun videoInfo(request: VideoInfoRequest): VideoInfoResponse
}
