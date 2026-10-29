package com.abdownloadmanager.desktop.pages.addDownload.single

import ir.amirab.util.compose.trs
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberDialogState
import arrow.core.Some
import com.abdownloadmanager.desktop.pages.addDownload.shared.*
import com.abdownloadmanager.desktop.window.custom.BaseOptionDialog
import com.abdownloadmanager.desktop.window.moveSafe
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.downloaderinui.add.CanAddResult
import com.abdownloadmanager.shared.pages.adddownload.shared.LastUsedLocationsProp
import com.abdownloadmanager.shared.pages.adddownload.shared.RememberFolderProp
import com.abdownloadmanager.shared.pages.adddownload.single.BaseAddSingleDownloadComponent
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.downloaderror.DownloadErrorReason
import com.abdownloadmanager.shared.util.mvi.HandleEffects
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.downloader.connection.IResponseInfo
import ir.amirab.downloader.utils.OnDuplicateStrategy
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import java.awt.MouseInfo

@Composable
fun AddDownloadPage(
    component: BaseAddSingleDownloadComponent,
) {
    val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp)
    ) {
        val credentials by component.credentials.collectAsState()
        fun setLink(link: String) {
            component.setCredentials(
                credentials.copy(link = Some(link))
            )
        }

        HandleEffects(component) {
            when (it) {
                is BaseAddSingleDownloadComponent.Effects.Common -> {
                    when (it) {
                        is BaseAddSingleDownloadComponent.Effects.Common.SuggestUrl -> {
                            setLink(it.link)
                        }
                    }
                }

                is BaseAddSingleDownloadComponent.Effects.Platform -> {
                    // support platform effects if any
                }
            }
        }
        IdmLabeledRow(trs("URL", "الرابط")) {
            UrlTextField(
                text = credentials.link,
                setText = {
                    setLink(it)
                },
                modifier = Modifier
            )
        }
        Row {
            val canAddResult by component.canAddResult.collectAsState()
            Column(Modifier.weight(1f)) {
                val useCategory by component.useCategory.collectAsState()
                Spacer(Modifier.size(12.dp))
                IdmLabeledRow(trs("Category", "الفئة")) {
                Row(
                    modifier = Modifier.height(IntrinsicSize.Max),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategorySelect(
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        categories = component.categories.collectAsState().value,
                        selectedCategory = component.selectedCategory.collectAsState().value,
                        onCategorySelected = {
                            component.setUseCategory(true)
                            component.setSelectedCategory(it)
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    CategoryAddButton(
                        enabled = true,
                        modifier = Modifier.fillMaxHeight(),
                        onClick = {
                            component.addNewCategory()
                        },
                    )
                }
                }
                val isFolderChanged by component.isFolderChangedResult.collectAsState()
                val rememberFolderAsDefault by component.rememberFolderAsDefault.collectAsState()
                Spacer(Modifier.size(12.dp))
                IdmLabeledRow(trs("Save As", "حفظ باسم")) {
                LocationTextField(
                    modifier = Modifier.fillMaxWidth(),
                    text = component.folder.collectAsState().value,
                    setText = {
                        component.setFolder(it)
                    },
                    errorText = when (canAddResult) {
                        CanAddResult.CantWriteInThisFolder -> myStringResource(Res.string.cant_write_to_this_folder)
                        else -> null
                    },
                    lastUsedLocations = LastUsedLocationsProp(
                        locations = component.lastUsedLocations.collectAsState().value,
                        onRequestRemove = component::removeFromLastDownloadLocation,
                    ),
                    rememberFolderAsDefault = RememberFolderProp(
                        isEnabled = isFolderChanged.isChanged,
                        value = rememberFolderAsDefault,
                        onValueChange = component::setRememberFolderAsDefault,
                        categoryName = isFolderChanged.categoryOrNull()?.name,
                    ),
                )
                }
                // IDM: Remember this path for "<category>" category
                val categoryName = isFolderChanged.categoryOrNull()?.name
                Spacer(Modifier.size(8.dp))
                IdmLabeledRow("") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.onClick {
                            if (isFolderChanged.isChanged) component.setRememberFolderAsDefault(!rememberFolderAsDefault)
                        },
                    ) {
                        CheckBox(
                            size = 16.dp,
                            value = rememberFolderAsDefault,
                            enabled = isFolderChanged.isChanged,
                            onValueChange = { component.setRememberFolderAsDefault(it) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (categoryName != null) trs("Remember this path for \"$categoryName\" category", "تذكّر هذا المسار لفئة \"$categoryName\"")
                            else trs("Remember this path", "تذكّر هذا المسار"),
                            fontSize = myTextSizes.sm,
                        )
                    }
                }
                val name by component.name.collectAsState()
                Spacer(Modifier.size(12.dp))
                IdmLabeledRow(trs("File name", "اسم الملف")) {
                NameTextField(
                    text = name,
                    setText = {
                        component.setName(it)
                    },
                    errorText = when (canAddResult) {
                        is CanAddResult.DownloadAlreadyExists -> {
                            if (onDuplicateStrategy == null) {
                                myStringResource(Res.string.download_already_exists)
                            } else {
                                null
                            }
                        }

                        CanAddResult.InvalidFileName -> myStringResource(Res.string.invalid_file_name)
                        else -> null
                    }.takeIf { name.isNotEmpty() }
                )
                }
            }
            Spacer(Modifier.size(20.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Top)
                    .width(IntrinsicSize.Max)
            ) {
                RenderFileTypeAndSize(component)
                Spacer(Modifier.size(10.dp))
                RenderResumeSupport(component)
                Spacer(Modifier.size(10.dp))
                ConfigActionsButtons(component)
            }
        }
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.weight(1f))
        MainActionButtons(component)
        if (component.showSolutionsOnDuplicateDownloadUi) {
            ShowSolutionsOnDuplicateDownload(component)
        }
        ShowAddToQueueDialog(
            queueComponent = component.selectQueueComponent,
        )
        if (component.showMoreSettings) {
            ExtraConfig(
                onDismiss = { component.showMoreSettings = false },
                configurables = component.configurables,
            )
        }
    }
}

/** IDM "Download File Info" style: a fixed width label on the left of each field */
@Composable
private fun IdmLabeledRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(88.dp).padding(start = 4.dp, end = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun ShowSolutionsOnDuplicateDownload(component: BaseAddSingleDownloadComponent) {
    val state = rememberDialogState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Unconstrained
        )
    )
    val close = {
        component.showSolutionsOnDuplicateDownloadUi = false
    }
    val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
    BaseOptionDialog(
        onCloseRequest = close,
        state = state,
        resizeable = false,
    ) {
        LaunchedEffect(window) {
            window.moveSafe(
                MouseInfo.getPointerInfo().location.run {
                    DpOffset(
                        x = x.dp,
                        y = y.dp
                    )
                }
            )
        }


        val shape = myShapes.defaultRounded
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            myColors.surface,
                            myColors.background,
                        )
                    )
                )
        ) {
            WithContentColor(myColors.onBackground) {
                Column(
                    Modifier.widthIn(max = 300.dp)
                ) {
                    WindowDraggableArea(Modifier) {
                        Column(
                            Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                myStringResource(Res.string.select_a_solution),
                                Modifier,
                                fontSize = myTextSizes.base
                            )
                            Spacer(Modifier.height(8.dp))
                            WithContentAlpha(0.75f) {
                                Text(
                                    myStringResource(Res.string.select_download_strategy_description),
                                    Modifier,
                                    fontSize = myTextSizes.sm,
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))
                        Divider()
                        Spacer(Modifier.height(4.dp))
                        Column {
                            OnDuplicateStrategySolutionItem(
                                isSelected = onDuplicateStrategy == OnDuplicateStrategy.AddNumbered,
                                title = myStringResource(Res.string.download_strategy_add_a_numbered_file),
                                description = myStringResource(Res.string.download_strategy_add_a_numbered_file_description),
                            ) {
                                component.setOnDuplicateStrategy(OnDuplicateStrategy.AddNumbered)
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = onDuplicateStrategy == OnDuplicateStrategy.OverrideDownload,
                                title = myStringResource(Res.string.download_strategy_override_existing_file),
                                description = myStringResource(Res.string.download_strategy_override_existing_file_description),
                            ) {
                                component.setOnDuplicateStrategy(OnDuplicateStrategy.OverrideDownload)
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = null,
                                title = myStringResource(Res.string.download_strategy_update_download_link),
                                description = myStringResource(Res.string.download_strategy_update_download_link_description),
                            ) {
                                component.updateDownloadCredentialsOfOriginalDownload()
                                close()
                            }
                            OnDuplicateStrategySolutionItem(
                                isSelected = null,
                                title = myStringResource(Res.string.download_strategy_show_downloaded_file),
                                description = myStringResource(Res.string.download_strategy_show_downloaded_file_description),
                            ) {
                                component.openDownloadFileForCurrentLink()
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnDuplicateStrategySolutionItem(
    title: String,
    description: String,
    isSelected: Boolean?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        isSelected?.let {
            CheckBox(isSelected, { onClick() }, size = 12.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                fontSize = myTextSizes.base,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            WithContentAlpha(0.7f) {
                Text(
                    text = description,
                    fontSize = myTextSizes.sm,
                    modifier = Modifier
                )
            }
        }

    }
}


@Composable
private fun Divider() {
    Spacer(
        Modifier.fillMaxWidth()
            .height(1.dp)
            .background(myColors.onBackground / 10),
    )
}


@Composable
fun RenderResumeSupport(component: BaseAddSingleDownloadComponent) {
    val responseResult by component.checkResponseResult.collectAsState()
    val fileInfo = responseResult?.getOrNull()?.takeIf { it.isSuccessFul }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(16.dp)

    ) {
        val lineModifier = Modifier.weight(1f)
            .height(1.dp)
            .background(myColors.onBackground / 10)
        Box(lineModifier)
        val canAddToDownloads by component.canAddToDownloads.collectAsState()
        AnimatedVisibility(
            visible = canAddToDownloads && fileInfo != null,
        ) {
            fileInfo?.let { fileInfo ->
                val iconModifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp)
                if (fileInfo.resumeSupport) {
                    MyIcon(
                        icon = MyIcons.check,
                        contentDescription = null,
                        modifier = iconModifier,
                        tint = myColors.success
                    )
                } else {
                    MyIcon(
                        icon = MyIcons.clear,
                        contentDescription = null,
                        modifier = iconModifier,
                        tint = myColors.error,
                    )
                }
            }
        }
        Box(lineModifier)


    }
}


@Composable
fun ConfigActionsButtons(component: BaseAddSingleDownloadComponent) {
    val responseResult by component.checkResponseResult.collectAsState()
    Row {
        IconActionButton(MyIcons.refresh, Res.string.refresh.asStringSource()) {
            component.refresh()
        }
        Spacer(Modifier.width(8.dp))
        IconActionButton(
            MyIcons.settings,
            Res.string.settings.asStringSource(),
            indicateActive = component.showMoreSettings,
            requiresAttention = responseResult?.getOrNull()?.requireBasicAuth ?: false
        ) {
            component.showMoreSettings = true
        }
        Spacer(Modifier.width(6.dp))
        component.lastError.collectAsState().value?.let { downloadErrorReason ->
            DownloadErrorInfoButton(
                onClick = component::openDownloadErrorDialog,
                responseInfo = responseResult?.getOrNull(),
                reason = downloadErrorReason,
            )
        }
    }
}

@Composable
private fun MainActionButtons(component: BaseAddSingleDownloadComponent) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        val onDuplicateStrategy by component.onDuplicateStrategy.collectAsState()
        val canAddResult by component.canAddResult.collectAsState()
        if (canAddResult is CanAddResult.DownloadAlreadyExists && onDuplicateStrategy == null) {
            ActionButton(
                text = myStringResource(Res.string.show_solutions),
                modifier = Modifier,
                onClick = { component.showSolutionsOnDuplicateDownloadUi = true },
            )
            if (component.shouldShowOpenFile.collectAsState().value) {
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = myStringResource(Res.string.open_file),
                    modifier = Modifier,
                    onClick = { component.openExistingFile() },
                    onLongClick = { component.openExistingFile(useAppChooser = true) },
                )
            }
        } else {
            val canAddToDownloads by component.canAddToDownloads.collectAsState()
            ActionButton(
                // IDM button names
                text = trs("Download Later", "تحميل لاحقًا"),
                modifier = Modifier,
                enabled = canAddToDownloads,
                onClick = {
                    component.selectQueueComponent.openAddToQueueDialog()
                },
                onLongClick = {
                    component.selectQueueComponent.fastConfirm()
                }

            )
            Spacer(Modifier.width(12.dp))
            PrimaryMainActionButton(
                text = trs("Start Download", "بدء التحميل"),
                modifier = Modifier,
                enabled = canAddToDownloads,
                onClick = {
                    component.onRequestDownload()
                },
            )
            if (onDuplicateStrategy != null) {
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = myStringResource(Res.string.change_solution),
                    modifier = Modifier,
                    onClick = { component.showSolutionsOnDuplicateDownloadUi = true },
                )
            }

        }
        // IDM keeps the three buttons together in the middle
        Spacer(Modifier.width(12.dp))

        ActionButton(
            text = myStringResource(Res.string.cancel),
            modifier = Modifier,
            onClick = {
                component.onRequestClose()
            },
        )
    }
}

@Composable
fun RenderFileTypeAndSize(
    component: BaseAddSingleDownloadComponent,
) {
    val isLinkLoading by component.isLinkLoading.collectAsState()
    val linkCheckResult by component.checkResponseResult.collectAsState()
    val fileIconProvider = component.iconProvider
    val iconModifier = Modifier.size(mySpacings.iconSize)
    Box(Modifier.padding(top = 16.dp)) {
        AnimatedContent(
            targetState = isLinkLoading,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { loading ->
            if (loading) {
                LoadingIndicator(iconModifier)
            } else {
//                val extension = getExtension(fileInfo?.fileName ?: usersSetFileName) ?: "unknown"
                val downloadItem by component.downloadItem.collectAsState()
                val icon = fileIconProvider.rememberIcon(downloadItem.name)

//                val bitmap = FileIconProvider.getIconOfFileExtension(extension)

                AnimatedContent(
                    linkCheckResult,
                ) { linkCheckResult ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WithContentAlpha(1f) {
                            when (linkCheckResult) {
                                null -> {
                                    MyIcon(
                                        icon = MyIcons.question,
                                        contentDescription = null,
                                        modifier = iconModifier,
                                    )
                                }

                                else -> {
                                    MyIcon(
                                        icon,
                                        null,
                                        iconModifier
                                    )
                                    val size = component.getLengthString()
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        size.rememberString(),
                                        fontSize = myTextSizes.sm,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getExtension(s: String): String? {
    if (s.isBlank()) return null
    return s.substringAfterLast(".", "")
        .takeIf { it.isNotBlank() }
}


@Composable
private fun DownloadErrorInfoButton(
    onClick: () -> Unit,
    reason: DownloadErrorReason,
    responseInfo: IResponseInfo?,
) {
    val tooltipStringSource = reason.title.asStringSource()
    Tooltip(tooltipStringSource) {
        IconActionButton(
            onClick = {
                onClick()
            },
            contentDescription = tooltipStringSource,
            icon = if (responseInfo?.requireBasicAuth == true) {
                MyIcons.lock
            } else {
                MyIcons.question
            },
            contentColor = myColors.error,
        )
    }
}

@Composable
private fun UrlTextField(
    text: String,
    setText: (String) -> Unit,
    errorText: String? = null,
    modifier: Modifier = Modifier,
) {
    MyTextFieldWithIcons(
        text,
        setText,
        myStringResource(Res.string.download_link),
        modifier = modifier.fillMaxWidth(),
        end = {
            MyTextFieldIcon(MyIcons.paste) {
                setText(
                    ClipboardUtil.read()
                        .orEmpty()
                )
            }
        },
        errorText = errorText
    )
}

@Composable
private fun NameTextField(
    text: String,
    setText: (String) -> Unit,
    errorText: String? = null,
) {
    MyTextFieldWithIcons(
        text,
        setText,
        myStringResource(Res.string.name),
        modifier = Modifier.fillMaxWidth(),
        errorText = errorText,
    )
}

