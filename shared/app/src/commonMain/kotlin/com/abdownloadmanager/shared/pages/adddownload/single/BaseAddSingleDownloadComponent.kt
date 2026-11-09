package com.abdownloadmanager.shared.pages.adddownload.single

import com.abdownloadmanager.shared.pages.adddownload.AddDownloadPreferences
import com.abdownloadmanager.shared.pages.adddownload.EarlyStartedDownloads
import com.arkivanov.essenty.lifecycle.doOnDestroy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.add.CanAddResult
import com.abdownloadmanager.shared.pagemanager.DownloadErrorDialogManager
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadComponent
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.pages.adddownload.FolderChangeResult
import com.abdownloadmanager.shared.pages.adddownload.ImportOptions
import com.abdownloadmanager.shared.pages.adddownload.SilentImportOptions
import com.abdownloadmanager.shared.repository.BaseAppRepository
import com.abdownloadmanager.shared.storage.appsettings.BaseAppSettingsStorage
import com.abdownloadmanager.shared.storage.ILastSavedLocationsStorage
import com.abdownloadmanager.shared.storage.ISelectQueueStorage
import com.abdownloadmanager.shared.util.DownloadItemOpener
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.FileIconProvider
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.CategoryItem
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.mvi.ContainsEffects
import com.abdownloadmanager.shared.util.mvi.supportEffects
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsManager
import com.abdownloadmanager.shared.util.perhostsettings.getSettingsForURL
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.utils.OnDuplicateStrategy
import ir.amirab.downloader.utils.orDefault
import ir.amirab.util.compose.StringSource
import ir.amirab.util.flow.combineStateFlows
import ir.amirab.util.flow.mapStateFlow
import ir.amirab.util.flow.onEachLatest
import ir.amirab.util.toSingleLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class BaseAddSingleDownloadComponent(
    ctx: ComponentContext,
    val onRequestClose: () -> Unit,
    val onRequestDownload: OnRequestDownloadSingleItem,
    private val onRequestAddToQueue: OnRequestAddSingleItem,
    val openExistingDownload: (Long) -> Unit,
    val updateExistingDownloadCredentials: (Long, IDownloadCredentials, DownloadJobExtraConfig?) -> Unit,
    protected val downloadItemOpener: DownloadItemOpener,
    protected val downloadErrorDialogManager: DownloadErrorDialogManager,
    protected val lastSavedLocationsStorage: ILastSavedLocationsStorage,
    protected val appScope: CoroutineScope,
    protected val appSettings: BaseAppSettingsStorage,
    appRepository: BaseAppRepository,
    protected val perHostSettingsManager: PerHostSettingsManager,
    categoryManager: CategoryManager,
    val downloadSystem: DownloadSystem,
    val iconProvider: FileIconProvider,
    selectQueueStorage: ISelectQueueStorage,
    queueManager: QueueManager,
    importOptions: ImportOptions,
    id: String,
    downloaderInUi: DownloaderInUi<IDownloadCredentials, *, *, *, *, *, *, *, *, *>,
    initialCredentials: AddDownloadCredentialsInUiProps,
) : AddDownloadComponent(
    ctx = ctx,
    id = id,
    lastSavedLocationsStorage = lastSavedLocationsStorage,
    queueManager = queueManager,
    selectQueueStorage = selectQueueStorage,
    appRepository = appRepository,
    categoryManager = categoryManager,
),
    ContainsEffects<BaseAddSingleDownloadComponent.Effects> by supportEffects() {
    private val _shouldShowWindow = MutableStateFlow(importOptions.silentImport == null)
    override val shouldShowWindow: StateFlow<Boolean> = _shouldShowWindow.asStateFlow()
    val downloadInputsComponent = downloaderInUi.createNewDownloadInputs(
        initialFolder = AddDownloadPreferences.lastFolder(lastUsedLocations.value) ?: appRepository.saveLocation.value,
        initialName = initialCredentials.extraConfig.getAndFixSuggestedName().orEmpty(),
        downloadSystem = downloadSystem,
        scope = scope,
        initialCredentials = initialCredentials.credentials,
    )
    val downloadChecker = downloadInputsComponent.newDownloadUiChecker

    val categories = categoryManager.categoriesFlow
    private val _selectedCategory: MutableStateFlow<Category?> = MutableStateFlow(categories.value.firstOrNull())
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _useCategory = MutableStateFlow(false)
    val useCategory = _useCategory.asStateFlow()
    fun setUseCategory(useCategory: Boolean) {
        _useCategory.update { useCategory }
        if (useCategory) {
            val usedCategoryFolder = useCategoryFolder(_useCategory.value)
            if (!usedCategoryFolder) {
                useDefaultFolder()
            }
        } else {
            useDefaultFolder()
        }
    }

    fun openDownloadErrorDialog() {
        downloadChecker.lastErrorReason.value
            ?.let { reason ->
                downloadErrorDialogManager.openDownloadErrorDialog(
                    downloadItem.value,
                    reason,
                )
            }
    }

    private fun useCategoryFolder(
        useCategory: Boolean,
    ): Boolean {
        // IDM: the last folder used wins over the category folder
        AddDownloadPreferences.lastFolder(lastUsedLocations.value)?.let {
            setFolder(it)
            return true
        }
        val category = selectedCategory.value
        if (useCategory && category != null) {
            category.getDownloadPath()?.let {
                setFolder(it)
                return true
            }
        }
        return false
    }

    private fun useDefaultFolder() {
        setFolder(AddDownloadPreferences.lastFolder(lastUsedLocations.value) ?: appRepository.saveLocation.value)
    }


    fun setSelectedCategory(category: Category) {
        _selectedCategory.update { category }
        val useCategory = useCategory.value
        if (useCategory) {
            val used = useCategoryFolder(useCategory)
            if (!used) {
                useDefaultFolder()
            }
        }
    }


    //inputs
    val credentials = downloadChecker.credentials.asStateFlow()
    val name = downloadChecker.name.asStateFlow()
    val folder = downloadChecker.folder.asStateFlow()
    val onDuplicateStrategy: MutableStateFlow<OnDuplicateStrategy?> = MutableStateFlow(null)

    override val isFolderChangedResult: StateFlow<FolderChangeResult> = combine(
        folder,
        useCategory,
        selectedCategory,
        categories,
        appRepository.saveLocation,
    ) { newSelectedFolder, useCategory, selectedCat, allCategories, defaultLocation ->
        val category = if (useCategory) {
            selectedCat?.let { cat -> allCategories.find { it.id == cat.id } ?: cat }
        } else null
        val categoryOrDefaultFolder = category?.getDownloadPath() ?: defaultLocation
        FolderChangeResult.of(
            category = category,
            userSelectedFolder = newSelectedFolder,
            currentFolder = categoryOrDefaultFolder,
        )
    }.stateIn(scope, SharingStarted.Eagerly, FolderChangeResult.default())

    fun setCredentials(downloadCredentials: IDownloadCredentials) {
        downloadChecker.credentials.update { downloadCredentials }
    }

    fun setFolder(folder: String) {
        downloadChecker.folder.update { folder }
    }

    fun setName(name: String) {
        val refinedName = name
            .toSingleLine()
        downloadChecker.name.update { refinedName }
    }

    fun setOnDuplicateStrategy(onDuplicateStrategy: OnDuplicateStrategy) {
        this.onDuplicateStrategy.update { onDuplicateStrategy }
    }

    fun getLengthString(): StringSource {
        return downloadInputsComponent.getLengthString()
    }

    init {
        credentials
            .map { it.link }
            .distinctUntilChanged()
            .debounce(250.milliseconds)
            .onEachLatest { link ->
                perHostSettingsManager
                    .getSettingsForURL(link)
                    ?.let(downloadInputsComponent::applyHostSettingsToExtraConfig)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
        merge(
            credentials.mapStateFlow { it.link },
            name,
            folder,
        )
            .onEachLatest { onDuplicateStrategy.update { null } }
            .launchIn(scope)
        combine(
            name,
            credentials.map { it.link },
        ) { name, link ->
            val category = categoryManager.getCategoryOf(
                CategoryItem(
                    fileName = name,
                    url = link,
                )
            )
            val globalUseCategoryByDefault = appSettings.useCategoryByDefault.value
            val suggestedUseCategory: Boolean
            if (category == null) {
                suggestedUseCategory = false
            } else {
                setSelectedCategory(category)
                suggestedUseCategory = true
            }
            if (globalUseCategoryByDefault) {
                setUseCategory(suggestedUseCategory)
            }
        }.launchIn(scope)
    }


    val canAddResult = downloadChecker.canAddToDownloadResult.asStateFlow()
    private val canAdd = downloadChecker.canAdd
    private val isDuplicate = downloadChecker.isDuplicate

    val isLinkLoading = downloadChecker.gettingResponseInfo
    val checkResponseResult = downloadChecker.responseResult
    val lastError = downloadChecker.lastErrorReason

    val canAddToDownloads = combineStateFlows(
        canAdd, isDuplicate, onDuplicateStrategy, isLinkLoading
    ) { canAdd, isDuplicate, onDuplicateStrategy, isLinkLoading ->
        if (isLinkLoading) {
            // link is loading wait for it...
            return@combineStateFlows false
        }
        if (canAdd) {
            true
        } else if (isDuplicate && onDuplicateStrategy != null) {
            true
        } else {
            false
        }
    }

    val downloadItem = downloadInputsComponent.downloadItem
    val downloadJobConfig = downloadInputsComponent.downloadJobConfig


    var showMoreSettings by mutableStateOf(false)


    val configurables = downloadInputsComponent.configurableList

    val queues = queueManager.queues
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            emptyList()
        )

    fun refresh() {
        downloadChecker.refresh()
    }

    // ---- IDM "start downloading while Download File Info is open" (setting, off by default) ----
    // the file starts downloading as soon as the link checks out; Start Download keeps it (moving it
    // to the chosen name / folder), Download Later and Cancel delete it with its partial file
    private var earlyJob: Job? = null
    private var earlyId: Long? = null
    private var earlyLink: String? = null

    // set once Start / Later / Cancel decided what happens to the early download
    private var earlyHandled = false

    private fun maybeStartEarly() {
        if (!AddDownloadPreferences.startWhileDialogOpen.value || !shouldShowWindow.value) return
        if (earlyJob != null || earlyHandled) return
        val info = checkResponseResult.value?.getOrNull() ?: return
        // only when it can really continue later: resumable, a real file, nothing in the way
        if (!info.isSuccessFul || !info.resumeSupport || info.isWebPage || !canAdd.value) return
        val item = downloadItem.value
        val extra = downloadJobConfig.value
        earlyJob = appScope.launch {
            runCatching {
                val id = downloadSystem.addDownload(
                    NewDownloadItemProps(item, extra, OnDuplicateStrategy.AddNumbered, EmptyContext),
                    queueId = null,
                    categoryId = null,
                )
                EarlyStartedDownloads.add(id)
                earlyId = id
                earlyLink = item.link
                downloadSystem.userManualResume(id)
            }
        }
    }

    private suspend fun discardEarly() {
        earlyJob?.join()
        val id = earlyId ?: return
        earlyId = null
        runCatching { downloadSystem.removeDownload(id, alsoRemoveFile = true, context = EmptyContext) }
        EarlyStartedDownloads.remove(id)
    }

    init {
        combine(checkResponseResult, canAdd) { _, _ -> maybeStartEarly() }.launchIn(scope)
        lifecycle.doOnDestroy {
            // the dialog was closed without Start / Later: the early download goes away
            if (earlyJob != null && !earlyHandled) {
                earlyHandled = true
                appScope.launch { discardEarly() }
            }
        }
    }

    fun onRequestDownload() {
        val downloadItem = this@BaseAddSingleDownloadComponent.downloadItem.value
        val downloadJobExtraConfig = downloadJobConfig.value
        if (earlyJob != null && !earlyHandled) {
            consumeDialog {
                earlyHandled = true
                saveLocationIfNecessary(downloadItem.folder)
                val categoryId = getCategoryIfUseCategoryIsOn()?.id
                val strategy = onDuplicateStrategy.value.orDefault()
                appScope.launch {
                    earlyJob?.join()
                    val id = earlyId
                    if (id == null || earlyLink != downloadItem.link) {
                        // the early start failed or the link was changed: the normal way
                        discardEarly()
                        onRequestDownload(
                            item = NewDownloadItemProps(downloadItem, downloadJobExtraConfig, strategy, EmptyContext),
                            categoryId = categoryId,
                        )
                    } else {
                        // keep what is downloaded: give it the name / folder chosen in the dialog
                        downloadSystem.editDownload(
                            id,
                            {
                                it.name = downloadItem.name
                                it.folder = downloadItem.folder
                            },
                            downloadJobExtraConfig,
                        )
                        categoryId?.let { categoryManager.addItemsToCategory(it, listOf(id)) }
                        EarlyStartedDownloads.confirm(id)
                    }
                }
                onRequestClose()
            }
            return
        }
        consumeDialog {
            saveLocationIfNecessary(downloadItem.folder)
            onRequestDownload(
                item = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    extraConfig = downloadJobExtraConfig,
                    onDuplicateStrategy = onDuplicateStrategy.value.orDefault(),
                    context = EmptyContext
                ),
                categoryId = getCategoryIfUseCategoryIsOn()?.id
            )
            onRequestClose()
        }
    }

    private fun getCategoryIfUseCategoryIsOn(): Category? {
        return if (useCategory.value)
            selectedCategory.value
        else
            null
    }

    private fun saveLocationIfNecessary(folder: String) {
        val category = getCategoryIfUseCategoryIsOn()
        if (rememberFolderAsDefault.value && isFolderChangedResult.value.isChanged) {
            persistFolder(folder, category)
        }
        // always first in the list: it is the folder the next download opens in
        // (when "remember the last folder" is on); before, a category's own folder was left out
        addToLastUsedLocations(folder)
    }


    override fun onRequestAddToQueue(
        queueId: Long?,
        startQueue: Boolean,
    ) {
        val downloadItem = downloadItem.value
        val downloadJobConfig = downloadJobConfig.value
        if (earlyJob != null && !earlyHandled) {
            // "Download Later": the early download is removed first, then it is added the normal way
            consumeDialog {
                earlyHandled = true
                saveLocationIfNecessary(downloadItem.folder)
                val categoryId = getCategoryIfUseCategoryIsOn()?.id
                val strategy = onDuplicateStrategy.value.orDefault()
                appScope.launch {
                    discardEarly()
                    onRequestAddToQueue(
                        item = NewDownloadItemProps(downloadItem, downloadJobConfig, strategy, EmptyContext),
                        queueId = queueId,
                        categoryId = categoryId,
                    ).invokeOnCompletion {
                        if (queueId != null && startQueue) {
                            GlobalScope.launch { downloadSystem.startQueue(queueId) }
                        }
                    }
                }
                onRequestClose()
            }
            return
        }
        consumeDialog {
            saveLocationIfNecessary(downloadItem.folder)
            onRequestAddToQueue(
                item = NewDownloadItemProps(
                    downloadItem = downloadItem,
                    extraConfig = downloadJobConfig,
                    onDuplicateStrategy = onDuplicateStrategy.value.orDefault(),
                    context = EmptyContext,
                ),
                queueId = queueId,
                categoryId = getCategoryIfUseCategoryIsOn()?.id,
            ).invokeOnCompletion {
                if (queueId != null && startQueue) {
                    GlobalScope.launch {
                        downloadSystem.startQueue(queueId)
                    }
                }
            }
            onRequestClose()
        }
    }

    fun openDownloadFileForCurrentLink() {
        (canAddResult.value as? CanAddResult.DownloadAlreadyExists)
            ?.itemId
            ?.let {
                openExistingDownload(it)
                onRequestClose()
            }
    }

    fun updateDownloadCredentialsOfOriginalDownload() {
        (canAddResult.value as? CanAddResult.DownloadAlreadyExists)
            ?.itemId
            ?.let {
                updateExistingDownloadCredentials(it, downloadItem.value, downloadJobConfig.value)
                onRequestClose()
            }
    }

    var showSolutionsOnDuplicateDownloadUi by mutableStateOf(false)

    val shouldShowOpenFile = combine(
        onDuplicateStrategy, canAddResult,
    ) { onDuplicateStrategy, result ->
        if (result is CanAddResult.DownloadAlreadyExists && onDuplicateStrategy == null) {
            val item = downloadSystem.getDownloadItemById(result.itemId) ?: return@combine false
            if (item.status != DownloadStatus.Completed) {
                return@combine false
            }
            downloadSystem.getDownloadFile(item).exists()
        } else false
    }.stateIn(scope, SharingStarted.WhileSubscribed(), false)

    fun openExistingFile(
        useAppChooser: Boolean = false
    ) {
        val itemId = (canAddResult.value as? CanAddResult.DownloadAlreadyExists)?.itemId ?: return
        consumeDialog {
            appScope.launch {
                if (useAppChooser) {
                    downloadItemOpener.openDownloadItemWith(itemId)
                } else {
                    downloadItemOpener.openDownloadItem(itemId)
                }
            }
            onRequestClose()
        }
    }

    fun addNewCategory() {
        onRequestAddCategory()
    }

    init {
        importOptions.silentImport?.let {
            handleSilentImport(it)
        }
    }

    fun handleSilentImport(silentImport: SilentImportOptions) {
        scope.launch {
            // a video page needs yt-dlp to ask the site first (several seconds) before the name is known
            val isVideo = credentials.value is ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
            val waitFor = if (isVideo) 45.seconds else 2.seconds
            try {
                withTimeout(waitFor) {
                    // ensure all values are set!
                    credentials.map { it.link }.first { it.isNotEmpty() }
                    folder.first { it.isNotEmpty() }
                    name.first { it.isNotEmpty() }
                }
            } catch (_: Exception) {
                // never drop it silently: let the user finish it in the window
                _shouldShowWindow.value = true
                return@launch
            }
            val failAutoAdd = async {
                try {
                    // although we don't need timeout, but I add this timeout here maybe there is a bug
                    // and I don't want this coroutine to be halted infinitely
                    withTimeout(if (isVideo) 45.seconds else 10.seconds) {
                        canAddToDownloads.first { it }
                        if (silentImport.silentDownload) {
                            onRequestDownload()
                        } else {
                            selectQueueComponent.fastConfirm()
                        }
                    }
                    false
                } catch (_: Exception) {
                    true
                }
            }

            val errorDuringWait = async {
                val channel = canAddResult.produceIn(this)
                try {
                    val startTime = System.currentTimeMillis()
                    for (i in channel) {
                        when (i) {
                            is CanAddResult.DownloadAlreadyExists,
                            CanAddResult.CantWriteInThisFolder -> {
                                return@async true
                            }

                            CanAddResult.InvalidUrl,
                            CanAddResult.InvalidFileName -> {
                                // we may get invalid filename/invalid url at the beginning! because the name is empty
                                if (System.currentTimeMillis() - startTime >= 1000) {
                                    return@async true
                                }
                            }

                            CanAddResult.CanAdd -> {
                                // we must not break here because it cancels [failAutoAdd]
                                // instead we wait for [failAutoAdd] to be finished and we will be cancelled automatically after select is done!
                            }

                            null -> {}
                        }
                    }
                    return@async true
                } finally {
                    channel.cancel()
                }
            }
            val failedToAutoAdd = try {
                select {
                    failAutoAdd.onAwait { failed ->
                        failed
                    }
                    errorDuringWait.onAwait { errorDuringWait ->
                        errorDuringWait
                    }
                }
            } catch (_: Exception) {
                true
            } finally {
                runCatching {
                    failAutoAdd.cancelAndJoin()
                    errorDuringWait.cancelAndJoin()
                }
            }
            if (failedToAutoAdd) {
                // needs adjustments by user!
                _shouldShowWindow.value = true
            }
        }
    }

    sealed interface Effects {
        sealed interface Common : Effects {
            data class SuggestUrl(val link: String) : Common
        }

        interface Platform : Effects
    }
}

fun interface OnRequestAddSingleItem {
    operator fun invoke(
        item: NewDownloadItemProps,
        queueId: Long?,
        categoryId: Long?,
    ): Deferred<Long>
}

fun interface OnRequestDownloadSingleItem {
    operator fun invoke(
        item: NewDownloadItemProps,
        categoryId: Long?,
    )
}
