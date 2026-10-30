package com.abdownloadmanager.desktop.pages.settings

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.desktop.window.custom.WindowIcon
import ir.amirab.util.compose.IconSource
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.Handle
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.ui.configurable.RenderConfigurableGroup
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.MultiplatformVerticalScrollbar
import com.abdownloadmanager.shared.util.ui.needScroll
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import ir.amirab.util.ifThen

@Composable
private fun SideBar(
    settingsComponent: DesktopSettingsComponent,
    modifier: Modifier = Modifier,
) {
    val shape = myShapes.defaultRounded
    Column(
        modifier
            .fillMaxHeight()
            .border(1.dp, myColors.surface / 0.5f, shape)
            .clip(shape)
    ) {
//        var searchText by remember { mutableStateOf("") }
//        SearchBox(
//            searchText,
//            onTextChange = { searchText = it },
//            modifier = Modifier.height(38.dp),
//        )
        val collectAsState by settingsComponent.currentPage.collectAsState()
        for (i in settingsComponent.pages) {
            SideBarItem(
                icon = i.icon,
                name = i.name.rememberString(),
                isSelected = collectAsState == i,
                onClick = {
                    settingsComponent.setCurrentPage(i)
                }
            )
        }
    }
}

@Composable
private fun SideBarItem(icon: IconSource, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(IntrinsicSize.Max)
            .ifThen(isSelected) {
                background(myColors.onBackground / 0.05f)
            }
            .selectable(
                selected = isSelected,
                onClick = onClick
            )
    ) {
        Row(
            Modifier
                .padding(vertical = 8.dp)
                .padding(start = 16.dp)
                .padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WithContentAlpha(if (isSelected) 1f else 0.75f) {
                MyIcon(
                    icon,
                    null,
                    Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    name,
                    Modifier.weight(1f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = myTextSizes.lg,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
        AnimatedVisibility(
            isSelected,
            modifier = Modifier
                .align(Alignment.CenterStart),
            enter = scaleIn(),
            exit = scaleOut(),
        ) {
            Spacer(
                Modifier
                    .height(16.dp)
                    .width(3.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 12.dp,
                            topEnd = 12.dp,
                        )
                    )
                    .background(myColors.primary)
            )
        }
        if (isSelected) {
            listOf(
                Alignment.TopCenter,
                Alignment.BottomCenter,
            ).forEach {
                Spacer(
                    Modifier
                        .align(it)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    myColors.onBackground / 0.1f,
                                    myColors.onBackground / 0.1f,
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun OptionsTabs(settingsComponent: DesktopSettingsComponent) {
    val current by settingsComponent.currentPage.collectAsState()
    val lineColor = myColors.onBackground / 0.15f
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .drawBehind {
                drawLine(lineColor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            }
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (page in settingsComponent.pages) {
            val isSelected = page == current
            val shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
            Row(
                Modifier
                    .padding(end = 2.dp)
                    .clip(shape)
                    .ifThen(isSelected) {
                        background(myColors.surface)
                            .border(1.dp, lineColor, shape)
                    }
                    .selectable(selected = isSelected, onClick = { settingsComponent.setCurrentPage(page) })
                    .padding(horizontal = 12.dp, vertical = if (isSelected) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WithContentAlpha(if (isSelected) 1f else 0.7f) {
                    MyIcon(page.icon, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        page.name.rememberString(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = myTextSizes.base,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPage(
    settingsComponent: DesktopSettingsComponent,
    onDismissRequest: () -> Unit,
) {
//    WindowIcon(MyIcons.settings)
    WindowIcon(MyIcons.appIcon)
    Column {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(myColors.surface)
        )
        // IDM Options: the pages are tabs across the top
        Column {
            OptionsTabs(settingsComponent)
            val currentConfigurables by settingsComponent.configurables.collectAsState()
            AnimatedContent(currentConfigurables) { configurableGroups ->
                val scrollState = rememberScrollState()
                val scrollbarAdapter = rememberScrollbarAdapter(scrollState)
                Row {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (cfgGroup in configurableGroups) {
                            RenderConfigurableGroup(
                                cfgGroup,
                                Modifier,
                                itemPadding = PaddingValues(
                                    vertical = 8.dp,
                                    horizontal = 16.dp
                                )
                            )
                        }
                    }
                    if (scrollbarAdapter.needScroll()) {
                        MultiplatformVerticalScrollbar(
                            adapter = scrollbarAdapter,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .padding(end = 2.dp),
                        )
                    }
                }
            }

        }
    }
}



