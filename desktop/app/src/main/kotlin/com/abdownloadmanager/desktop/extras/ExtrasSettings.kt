package com.abdownloadmanager.desktop.extras

import ir.amirab.downloader.downloaditem.ytdlp.YtDlpOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@Serializable
private data class ExtrasSettingsModel(
    val antivirusScan: Boolean = true,
    val clipboardMonitor: Boolean = true,
    val ytDlpAutoUpdate: Boolean = true,
    val ytDlpLastUpdateCheck: Long = 0,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: String = "en.*,ar.*",
    val quotaEnabled: Boolean = false,
    val quotaMegabytes: Long = 500,
    val quotaPeriodMinutes: Long = 60,
    val markOfTheWeb: Boolean = true,
    val subtitlesAsFile: Boolean = false,
    val speedScheduleEnabled: Boolean = false,
    val speedScheduleFrom: String = "09:00",
    val speedScheduleTo: String = "17:00",
    val speedScheduleLimitKb: Long = 500,
    val autoExtract: Boolean = false,
    val deleteArchiveAfterExtract: Boolean = false,
    val miniWindow: Boolean = false,
)

/**
 * Settings of the features added in this custom build, kept in their own file
 * (~/.abdm/custom-features.json) so the upstream settings format stays untouched.
 */
class ExtrasSettings(scope: CoroutineScope) {
    private val file = File(System.getProperty("user.home"), ".abdm/custom-features.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val initial = runCatching {
        json.decodeFromString<ExtrasSettingsModel>(file.readText())
    }.getOrDefault(ExtrasSettingsModel())

    val antivirusScan = MutableStateFlow(initial.antivirusScan)
    val clipboardMonitor = MutableStateFlow(initial.clipboardMonitor)
    val ytDlpAutoUpdate = MutableStateFlow(initial.ytDlpAutoUpdate)
    val ytDlpLastUpdateCheck = MutableStateFlow(initial.ytDlpLastUpdateCheck)
    val downloadSubtitles = MutableStateFlow(initial.downloadSubtitles)
    val subtitleLanguages = MutableStateFlow(initial.subtitleLanguages)
    val quotaEnabled = MutableStateFlow(initial.quotaEnabled)
    val quotaMegabytes = MutableStateFlow(initial.quotaMegabytes)
    val quotaPeriodMinutes = MutableStateFlow(initial.quotaPeriodMinutes)
    val markOfTheWeb = MutableStateFlow(initial.markOfTheWeb)
    val subtitlesAsFile = MutableStateFlow(initial.subtitlesAsFile)
    val speedScheduleEnabled = MutableStateFlow(initial.speedScheduleEnabled)
    val speedScheduleFrom = MutableStateFlow(initial.speedScheduleFrom)
    val speedScheduleTo = MutableStateFlow(initial.speedScheduleTo)
    val speedScheduleLimitKb = MutableStateFlow(initial.speedScheduleLimitKb)
    val autoExtract = MutableStateFlow(initial.autoExtract)
    val deleteArchiveAfterExtract = MutableStateFlow(initial.deleteArchiveAfterExtract)
    val miniWindow = MutableStateFlow(initial.miniWindow)

    init {
        downloadSubtitles.onEach { YtDlpOptions.downloadSubtitles = it }.launchIn(scope)
        subtitlesAsFile.onEach { YtDlpOptions.subtitlesAsFile = it }.launchIn(scope)
        subtitleLanguages.onEach { langs ->
            YtDlpOptions.subtitleLanguages = langs.split(',', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                // "ar" alone would miss "ar-EG" style tracks
                .joinToString(",") { if (it.contains('*') || it.contains('.')) it else "$it.*" }
                .ifEmpty { "en.*" }
        }.launchIn(scope)
        @OptIn(FlowPreview::class)
        merge(
            antivirusScan, clipboardMonitor, ytDlpAutoUpdate, ytDlpLastUpdateCheck, downloadSubtitles, subtitleLanguages,
            quotaEnabled, quotaMegabytes, quotaPeriodMinutes,
            markOfTheWeb, subtitlesAsFile,
            speedScheduleEnabled, speedScheduleFrom, speedScheduleTo, speedScheduleLimitKb,
            autoExtract, deleteArchiveAfterExtract, miniWindow,
        )
            .debounce(300.milliseconds)
            .onEach { save() }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    private fun save() {
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(
                json.encodeToString(
                    ExtrasSettingsModel.serializer(),
                    ExtrasSettingsModel(
                        antivirusScan = antivirusScan.value,
                        clipboardMonitor = clipboardMonitor.value,
                        ytDlpAutoUpdate = ytDlpAutoUpdate.value,
                        ytDlpLastUpdateCheck = ytDlpLastUpdateCheck.value,
                        downloadSubtitles = downloadSubtitles.value,
                        subtitleLanguages = subtitleLanguages.value,
                        quotaEnabled = quotaEnabled.value,
                        quotaMegabytes = quotaMegabytes.value,
                        quotaPeriodMinutes = quotaPeriodMinutes.value,
                        markOfTheWeb = markOfTheWeb.value,
                        subtitlesAsFile = subtitlesAsFile.value,
                        speedScheduleEnabled = speedScheduleEnabled.value,
                        speedScheduleFrom = speedScheduleFrom.value,
                        speedScheduleTo = speedScheduleTo.value,
                        speedScheduleLimitKb = speedScheduleLimitKb.value,
                        autoExtract = autoExtract.value,
                        deleteArchiveAfterExtract = deleteArchiveAfterExtract.value,
                        miniWindow = miniWindow.value,
                    )
                )
            )
        }
    }
}
