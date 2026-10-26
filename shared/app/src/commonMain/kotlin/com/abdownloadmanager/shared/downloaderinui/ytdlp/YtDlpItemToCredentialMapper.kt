package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.CredentialAndItemMapper
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem

class YtDlpItemToCredentialMapper : CredentialAndItemMapper<YtDlpDownloadCredentials, YtDlpDownloadItem> {
    override fun itemToCredentials(item: YtDlpDownloadItem): YtDlpDownloadCredentials {
        return YtDlpDownloadCredentials(
            link = item.link,
            downloadPage = item.downloadPage,
            headers = item.headers,
            username = item.username,
            password = item.password,
            userAgent = item.userAgent,
            quality = item.quality,
        )
    }

    override fun appliedCredentialsToItem(
        item: YtDlpDownloadItem,
        credentials: YtDlpDownloadCredentials
    ): YtDlpDownloadItem {
        return item.copy().withCredentials(credentials)
    }

    override fun itemWithEditedName(item: YtDlpDownloadItem, name: String): YtDlpDownloadItem {
        return item.copy(name = name)
    }

    override fun credentialsWithEditedLink(
        credentials: YtDlpDownloadCredentials,
        link: String
    ): YtDlpDownloadCredentials {
        return credentials.copy(link = link)
    }
}
