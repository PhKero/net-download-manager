package com.abdownloadmanager.desktop.pages.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.settings.SettingSection.*
import com.abdownloadmanager.desktop.repository.AppRepository
import com.abdownloadmanager.desktop.storage.AppSettingsStorage
import com.abdownloadmanager.desktop.storage.PageStatesStorage
import com.abdownloadmanager.desktop.utils.renderapi.CustomRenderApi
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pagemanager.PerHostSettingsPageManager
import com.abdownloadmanager.shared.settings.BaseSettingsComponent
import com.abdownloadmanager.shared.settings.CommonSettings
import com.abdownloadmanager.shared.storage.impl.DNSStorage
import com.abdownloadmanager.shared.ui.configurable.ConfigurableGroup
import com.abdownloadmanager.shared.ui.configurable.item.BooleanConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.StringConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.LongConfigurable
import com.abdownloadmanager.desktop.extras.ExtrasSettings
import com.abdownloadmanager.shared.ui.theme.ThemeManager
import com.abdownloadmanager.shared.util.proxy.ProxyManager
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.arkivanov.decompose.ComponentContext
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.localizationmanager.LanguageManager
import ir.amirab.util.flow.mapStateFlow
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

sealed class SettingSection(
    val icon: IconSource,
    val name: StringSource,
) {
    data object Appearance :
        SettingSection(MyIcons.appearance, Res.string.appearance.asStringSource())

    //    TODO ADD Network section (proxy , etc..)
    //    data object Network : SettingSections(MyIcons.network, "Network")
    data object DownloadEngine :
        SettingSection(MyIcons.downloadEngine, Res.string.download_engine.asStringSource())

    data object BrowserIntegration :
        SettingSection(MyIcons.network, Res.string.browser_integration.asStringSource())

    /** features added in this custom build */
    data object VideoExtras :
        SettingSection(MyIcons.videoFile, "Video & Extras".asStringSource())
}

interface SettingSectionGetter {
    operator fun get(key: SettingSection): List<ConfigurableGroup>
}

class DesktopSettingsComponent(
    ctx: ComponentContext,
    val perHostSettingsPageManager: PerHostSettingsPageManager,
) : BaseSettingsComponent(ctx),
    KoinComponent {
    private val appSettings by inject<AppSettingsStorage>()
    private val pageStorage by inject<PageStatesStorage>()
    private val appRepository by inject<AppRepository>()
    private val proxyManager by inject<ProxyManager>()
    private val dNSStorage by inject<DNSStorage>()
    private val themeManager by inject<ThemeManager>()
    private val languageManager by inject<LanguageManager>()
    private val fontManager by inject<FontManager>()
    private val customRenderApi by inject<CustomRenderApi>()
    private val extrasSettings by inject<ExtrasSettings>()
    private val allConfigs = object : SettingSectionGetter {
        override operator fun get(key: SettingSection): List<ConfigurableGroup> {
            return when (key) {
                Appearance -> listOf(
                    ConfigurableGroup(
                        mainConfigurable = CommonSettings.themeConfig(themeManager, scope),
                        nestedVisible = themeManager.currentThemeInfo.mapStateFlow {
                            it.id == ThemeManager.systemThemeInfo.id
                        },
                        nestedConfigurable = listOfNotNull(
                            CommonSettings.defaultDarkThemeConfig(themeManager, scope),
                            CommonSettings.defaultLightThemeConfig(themeManager, scope),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.languageConfig(languageManager, scope),
                            DesktopSettings.fontConfig(fontManager, scope),
                            CommonSettings.uiScaleConfig(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOfNotNull(
                            DesktopSettings.useNativeMenuBarConfig(appSettings),
                            DesktopSettings.mergeTopBarWithTitleBarConfig(appSettings),
                            CommonSettings.showIconLabels(appSettings),
                            CommonSettings.useRelativeDateTime(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.autoStartConfig(appSettings),
                            DesktopSettings.useSystemTray(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.sizeUnit(appRepository, scope),
                            CommonSettings.speedUnit(appRepository, scope),
                            CommonSettings.useAverageSpeedConfig(appRepository),
                        )
                    ),
                    ConfigurableGroup(
                        mainConfigurable = CommonSettings.playSoundNotification(appSettings),
                        nestedVisible = appSettings.notificationSound,
                        nestedConfigurable = listOf(
                            CommonSettings.generalNotificationSound(appSettings),
                            CommonSettings.errorNotificationSound(appSettings),
                            CommonSettings.successNotificationSound(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.autoShowDownloadProgressWindow(appSettings),
                            CommonSettings.showDownloadFinishWindow(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            DesktopSettings.renderApi(customRenderApi),
                        )
                    )
                )

//                Network -> listOf()
                SettingSection.VideoExtras -> {
                    fun onOff(it: Boolean) = (if (it) Res.string.enabled else Res.string.disabled).asStringSource()
                    listOf(
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow("Video downloads (yt-dlp)".asStringSource()),
                            nestedConfigurable = listOf(
                                BooleanConfigurable(
                                    title = "Download subtitles".asStringSource(),
                                    description = "Embed subtitles (including auto-generated ones) into downloaded videos".asStringSource(),
                                    backedBy = extrasSettings.downloadSubtitles,
                                    describe = ::onOff,
                                ),
                                StringConfigurable(
                                    title = "Subtitle languages".asStringSource(),
                                    description = "Comma separated language codes, for example: en, ar".asStringSource(),
                                    backedBy = extrasSettings.subtitleLanguages,
                                    describe = { "".asStringSource() },
                                    enabled = extrasSettings.downloadSubtitles,
                                ),
                                BooleanConfigurable(
                                    title = "Save subtitles as .srt files".asStringSource(),
                                    description = "Keep subtitles as separate files next to the video instead of inside it".asStringSource(),
                                    backedBy = extrasSettings.subtitlesAsFile,
                                    describe = ::onOff,
                                    enabled = extrasSettings.downloadSubtitles,
                                ),
                                BooleanConfigurable(
                                    title = "Update yt-dlp automatically".asStringSource(),
                                    description = "Check once a day; sites change often and old versions stop working".asStringSource(),
                                    backedBy = extrasSettings.ytDlpAutoUpdate,
                                    describe = ::onOff,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow("Extras".asStringSource()),
                            nestedConfigurable = listOf(
                                BooleanConfigurable(
                                    title = "Monitor clipboard".asStringSource(),
                                    description = "When you copy a link to a file or a video page, offer to download it".asStringSource(),
                                    backedBy = extrasSettings.clipboardMonitor,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = "Scan downloads with antivirus".asStringSource(),
                                    description = "Scan each finished file with Microsoft Defender".asStringSource(),
                                    backedBy = extrasSettings.antivirusScan,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = "Floating speed window".asStringSource(),
                                    description = "A small always-on-top window with the total speed; drag it anywhere, click it to open the app".asStringSource(),
                                    backedBy = extrasSettings.miniWindow,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = "Extract archives after download".asStringSource(),
                                    description = "Unpack zip, rar and 7z files into a folder with the same name (uses 7-Zip)".asStringSource(),
                                    backedBy = extrasSettings.autoExtract,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = "Delete the archive after extracting".asStringSource(),
                                    description = "Remove the zip / rar file once it was extracted successfully".asStringSource(),
                                    backedBy = extrasSettings.deleteArchiveAfterExtract,
                                    describe = ::onOff,
                                    enabled = extrasSettings.autoExtract,
                                ),
                                BooleanConfigurable(
                                    title = "Mark downloads as from the internet".asStringSource(),
                                    description = "Windows warns before running downloaded programs, like files saved by a browser".asStringSource(),
                                    backedBy = extrasSettings.markOfTheWeb,
                                    describe = ::onOff,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow("Speed schedule".asStringSource()),
                            mainConfigurable = BooleanConfigurable(
                                title = "Limit speed during certain hours".asStringSource(),
                                description = "For example slower while you work, full speed at night".asStringSource(),
                                backedBy = extrasSettings.speedScheduleEnabled,
                                describe = ::onOff,
                            ),
                            nestedEnabled = extrasSettings.speedScheduleEnabled,
                            nestedConfigurable = listOf(
                                StringConfigurable(
                                    title = "From".asStringSource(),
                                    description = "Start time, 24 hour format (HH:MM)".asStringSource(),
                                    backedBy = extrasSettings.speedScheduleFrom,
                                    validate = { it.matches(Regex("""\d{1,2}:\d{2}""")) },
                                    describe = { "".asStringSource() },
                                ),
                                StringConfigurable(
                                    title = "To".asStringSource(),
                                    description = "End time, 24 hour format (HH:MM)".asStringSource(),
                                    backedBy = extrasSettings.speedScheduleTo,
                                    validate = { it.matches(Regex("""\d{1,2}:\d{2}""")) },
                                    describe = { "".asStringSource() },
                                ),
                                LongConfigurable(
                                    title = "Speed limit (KB/s)".asStringSource(),
                                    description = "Maximum total speed inside these hours".asStringSource(),
                                    backedBy = extrasSettings.speedScheduleLimitKb,
                                    describe = { "$it KB/s".asStringSource() },
                                    range = 1L..10_000_000L,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow("Download quota".asStringSource()),
                            mainConfigurable = BooleanConfigurable(
                                title = "Limit how much is downloaded".asStringSource(),
                                description = "Pause all downloads when the amount is reached, and resume them when the period ends".asStringSource(),
                                backedBy = extrasSettings.quotaEnabled,
                                describe = ::onOff,
                            ),
                            nestedEnabled = extrasSettings.quotaEnabled,
                            nestedConfigurable = listOf(
                                LongConfigurable(
                                    title = "Megabytes".asStringSource(),
                                    description = "Maximum amount to download in each period".asStringSource(),
                                    backedBy = extrasSettings.quotaMegabytes,
                                    describe = { "$it MB".asStringSource() },
                                    range = 1L..10_000_000L,
                                ),
                                LongConfigurable(
                                    title = "Period (minutes)".asStringSource(),
                                    description = "Length of each period, 60 means per hour".asStringSource(),
                                    backedBy = extrasSettings.quotaPeriodMinutes,
                                    describe = { "every $it minutes".asStringSource() },
                                    range = 1L..10_080L,
                                ),
                            )
                        ),
                    )
                }

                BrowserIntegration -> listOf(
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.browserIntegrationEnabled(appRepository),
                            CommonSettings.browserIntegrationPort(appRepository),
                            CommonSettings.apiAuthEnabled(appRepository),
                            CommonSettings.apiAuthKey(appRepository),
                        )
                    )
                )

                DownloadEngine -> listOf(
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.defaultDownloadFolderConfig(appSettings),
                            CommonSettings.useCategoryByDefault(appSettings),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.useSpeedLimitConfig(appRepository),
                            CommonSettings.speedLimitConfig(appRepository),
                            CommonSettings.threadCountConfig(appRepository),
                            CommonSettings.maxConcurrentDownloads(appRepository),
                            CommonSettings.maxDownloadRetryCount(appRepository),
                            CommonSettings.dynamicPartDownloadConfig(appRepository),
                            CommonSettings.minPartSize(appRepository),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.perHostSettings(perHostSettingsPageManager),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.proxyConfig(proxyManager),
                            CommonSettings.dnsConfig(dNSStorage),
                            CommonSettings.userAgent(appSettings),
                            CommonSettings.ignoreSSLCertificates(appSettings),
                            CommonSettings.useServerLastModified(appRepository),
                        )
                    ),
                    ConfigurableGroup(
                        nestedConfigurable = listOf(
                            CommonSettings.trackDeletedFilesOnDisk(appRepository),
                            CommonSettings.appendExtensionToIncompleteDownloads(appRepository),
                            CommonSettings.deletePartialFileOnDownloadCancellation(appSettings),
                            CommonSettings.useSparseFileAllocation(appRepository),
                        )
                    ),
                )
            }
        }
    }

    fun toFront() {
        sendEffect(Effects.BringToFront)
    }

    val settingsPageStateToPersist = MutableStateFlow(pageStorage.settingsPageStorage.value)
    private val _windowSize = settingsPageStateToPersist.mapTwoWayStateFlow(
        map = {
            it.windowSize.let { (x, y) ->
                DpSize(x.dp, y.dp)
            }
        },
        unMap = {
            copy(
                windowSize = it.width.value to it.height.value
            )
        }
    )
    val windowSize = _windowSize.asStateFlow()
    fun setWindowSize(dpSize: DpSize) {
        _windowSize.value = dpSize
    }

    init {
        settingsPageStateToPersist
            .debounce(500.milliseconds)
            .onEach { newValue ->
                pageStorage.settingsPageStorage.update { newValue }
            }.launchIn(scope)
    }

    var pages = listOf(
        Appearance,
//        Network,
        DownloadEngine,
        BrowserIntegration,
        SettingSection.VideoExtras,
    )
    private val _currentPage: MutableStateFlow<SettingSection> = MutableStateFlow(Appearance)
    val currentPage: StateFlow<SettingSection> = _currentPage.asStateFlow()
    fun setCurrentPage(section: SettingSection) {
        _currentPage.value = section
        _configurables.value = allConfigs[section]
    }

    private val _configurables = MutableStateFlow(
        allConfigs[currentPage.value]
    )
    override val configurables = _configurables.asStateFlow()

    sealed interface Effects : BaseSettingsComponent.Effects.Platform {
        data object BringToFront : Effects
    }
}
