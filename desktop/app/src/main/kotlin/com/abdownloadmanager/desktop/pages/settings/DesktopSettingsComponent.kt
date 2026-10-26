package com.abdownloadmanager.desktop.pages.settings

import ir.amirab.util.compose.tr
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
        SettingSection(MyIcons.videoFile, tr("Video & Extras", "الفيديو والإضافات"))
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
                            groupTitle = MutableStateFlow(tr("Video downloads (yt-dlp)", "تحميل الفيديو (yt-dlp)")),
                            nestedConfigurable = listOf(
                                BooleanConfigurable(
                                    title = tr("Download subtitles", "تحميل الترجمة"),
                                    description = tr("Embed subtitles (including auto-generated ones) into downloaded videos", "دمج الترجمة (ومنها التلقائية) داخل الفيديوهات المحمّلة"),
                                    backedBy = extrasSettings.downloadSubtitles,
                                    describe = ::onOff,
                                ),
                                StringConfigurable(
                                    title = tr("Subtitle languages", "لغات الترجمة"),
                                    description = tr("Comma separated language codes, for example: en, ar", "رموز اللغات مفصولة بفاصلة، مثلًا: en, ar"),
                                    backedBy = extrasSettings.subtitleLanguages,
                                    describe = { "".asStringSource() },
                                    enabled = extrasSettings.downloadSubtitles,
                                ),
                                BooleanConfigurable(
                                    title = tr("Save subtitles as .srt files", "حفظ الترجمة كملفات .srt"),
                                    description = tr("Keep subtitles as separate files next to the video instead of inside it", "حفظ الترجمة في ملفات منفصلة بجانب الفيديو بدلًا من داخله"),
                                    backedBy = extrasSettings.subtitlesAsFile,
                                    describe = ::onOff,
                                    enabled = extrasSettings.downloadSubtitles,
                                ),
                                BooleanConfigurable(
                                    title = tr("Update yt-dlp automatically", "تحديث yt-dlp تلقائيًا"),
                                    description = tr("Check once a day; sites change often and old versions stop working", "يتحقق مرة يوميًا، لأن المواقع تتغير كثيرًا والنسخ القديمة تتوقف عن العمل"),
                                    backedBy = extrasSettings.ytDlpAutoUpdate,
                                    describe = ::onOff,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow(tr("Extras", "إضافات")),
                            nestedConfigurable = listOf(
                                BooleanConfigurable(
                                    title = tr("Monitor clipboard", "مراقبة الحافظة"),
                                    description = tr("When you copy a link to a file or a video page, offer to download it", "عند نسخ رابط ملف أو صفحة فيديو، يعرض البرنامج تحميله"),
                                    backedBy = extrasSettings.clipboardMonitor,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = tr("Scan downloads with antivirus", "فحص التحميلات بمضاد الفيروسات"),
                                    description = tr("Scan each finished file with Microsoft Defender", "فحص كل ملف مكتمل باستخدام Microsoft Defender"),
                                    backedBy = extrasSettings.antivirusScan,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = tr("Floating speed window", "نافذة السرعة العائمة"),
                                    description = tr("A small always-on-top window with the total speed; drag it anywhere, click it to open the app", "نافذة صغيرة فوق كل النوافذ تعرض السرعة الكلية، اسحبها لأي مكان واضغط عليها لفتح البرنامج"),
                                    backedBy = extrasSettings.miniWindow,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = tr("Extract archives after download", "فك ضغط الملفات بعد التحميل"),
                                    description = tr("Unpack zip, rar and 7z files into a folder with the same name (uses 7-Zip)", "فك ضغط ملفات zip و rar و 7z في مجلد بنفس الاسم (باستخدام 7-Zip)"),
                                    backedBy = extrasSettings.autoExtract,
                                    describe = ::onOff,
                                ),
                                BooleanConfigurable(
                                    title = tr("Delete the archive after extracting", "حذف الملف المضغوط بعد فكه"),
                                    description = tr("Remove the zip / rar file once it was extracted successfully", "حذف ملف zip / rar بعد فك ضغطه بنجاح"),
                                    backedBy = extrasSettings.deleteArchiveAfterExtract,
                                    describe = ::onOff,
                                    enabled = extrasSettings.autoExtract,
                                ),
                                BooleanConfigurable(
                                    title = tr("Mark downloads as from the internet", "تمييز التحميلات بأنها من الإنترنت"),
                                    description = tr("Windows warns before running downloaded programs, like files saved by a browser", "ويندوز ينبّه قبل تشغيل البرامج المحمّلة، مثل الملفات المحفوظة من المتصفح"),
                                    backedBy = extrasSettings.markOfTheWeb,
                                    describe = ::onOff,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow(tr("Speed schedule", "جدولة السرعة")),
                            mainConfigurable = BooleanConfigurable(
                                title = tr("Limit speed during certain hours", "تحديد السرعة في ساعات معينة"),
                                description = tr("For example slower while you work, full speed at night", "مثلًا أبطأ أثناء عملك، وبالسرعة الكاملة ليلًا"),
                                backedBy = extrasSettings.speedScheduleEnabled,
                                describe = ::onOff,
                            ),
                            nestedEnabled = extrasSettings.speedScheduleEnabled,
                            nestedConfigurable = listOf(
                                StringConfigurable(
                                    title = tr("From", "من"),
                                    description = tr("Start time, 24 hour format (HH:MM)", "وقت البدء، بنظام 24 ساعة (HH:MM)"),
                                    backedBy = extrasSettings.speedScheduleFrom,
                                    validate = { it.matches(Regex("""\d{1,2}:\d{2}""")) },
                                    describe = { "".asStringSource() },
                                ),
                                StringConfigurable(
                                    title = tr("To", "إلى"),
                                    description = tr("End time, 24 hour format (HH:MM)", "وقت الانتهاء، بنظام 24 ساعة (HH:MM)"),
                                    backedBy = extrasSettings.speedScheduleTo,
                                    validate = { it.matches(Regex("""\d{1,2}:\d{2}""")) },
                                    describe = { "".asStringSource() },
                                ),
                                LongConfigurable(
                                    title = tr("Speed limit (KB/s)", "حد السرعة (KB/s)"),
                                    description = tr("Maximum total speed inside these hours", "أقصى سرعة كلية في هذه الساعات"),
                                    backedBy = extrasSettings.speedScheduleLimitKb,
                                    describe = { "$it KB/s".asStringSource() },
                                    range = 1L..10_000_000L,
                                ),
                            )
                        ),
                        ConfigurableGroup(
                            groupTitle = MutableStateFlow(tr("Download quota", "حد التحميل")),
                            mainConfigurable = BooleanConfigurable(
                                title = tr("Limit how much is downloaded", "تحديد كمية التحميل"),
                                description = tr("Pause all downloads when the amount is reached, and resume them when the period ends", "إيقاف كل التحميلات عند الوصول للكمية، واستئنافها عند انتهاء الفترة"),
                                backedBy = extrasSettings.quotaEnabled,
                                describe = ::onOff,
                            ),
                            nestedEnabled = extrasSettings.quotaEnabled,
                            nestedConfigurable = listOf(
                                LongConfigurable(
                                    title = tr("Megabytes", "ميجابايت"),
                                    description = tr("Maximum amount to download in each period", "أقصى كمية تحميل في كل فترة"),
                                    backedBy = extrasSettings.quotaMegabytes,
                                    describe = { "$it MB".asStringSource() },
                                    range = 1L..10_000_000L,
                                ),
                                LongConfigurable(
                                    title = tr("Period (minutes)", "الفترة (بالدقائق)"),
                                    description = tr("Length of each period, 60 means per hour", "طول كل فترة، 60 تعني كل ساعة"),
                                    backedBy = extrasSettings.quotaPeriodMinutes,
                                    describe = { tr("every $it minutes", "كل $it دقيقة") },
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
