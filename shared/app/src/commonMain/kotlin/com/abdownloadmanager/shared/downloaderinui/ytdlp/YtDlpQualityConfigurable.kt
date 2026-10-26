package com.abdownloadmanager.shared.downloaderinui.ytdlp

import ir.amirab.util.compose.tr
import com.abdownloadmanager.shared.ui.configurable.item.EnumConfigurable
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.convertPositiveSizeToHumanReadable
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpQuality
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.combineStringSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** "1080p — 245 MB" style quality picker, the first thing shown when adding a video */
fun createYtDlpQualityConfigurable(
    quality: MutableStateFlow<String>,
    info: StateFlow<YtDlpResponseInfo?>,
    sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
): EnumConfigurable<String> {
    fun title(q: String): StringSource {
        val i = info.value
        val label = when {
            i == null -> YtDlpQuality.label(q)
            YtDlpQuality.isAudioOnly(q) -> YtDlpQuality.label(q)
            q == YtDlpQuality.BEST -> i.actualHeightOf(q)?.let { "Best (${it}p)" } ?: YtDlpQuality.label(q)
            else -> {
                val actual = i.actualHeightOf(q)
                val wanted = YtDlpQuality.heightOf(q)
                if (actual != null && wanted != null && actual < wanted) "${wanted}p (video has ${actual}p)"
                else YtDlpQuality.label(q)
            }
        }
        val size = i?.estimatedSizeOf(q)?.let {
            convertPositiveSizeToHumanReadable(it, sizeAndSpeedUnitProvider.sizeUnit.value)
        }
        return if (size == null) label.asStringSource()
        else listOf(label.asStringSource(), size).combineStringSources(" — ")
    }
    return EnumConfigurable(
        title = tr("Quality", "الجودة"),
        description = tr("Video quality to download. Sizes are estimates from the site", "جودة الفيديو المطلوب تحميله. الأحجام تقديرية من الموقع"),
        backedBy = quality,
        itemTitle = ::title,
        possibleValues = YtDlpQuality.all,
    )
}
