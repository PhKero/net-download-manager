package com.abdownloadmanager.desktop.pages.home

import ir.amirab.util.compose.tr
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import ir.amirab.util.ifThen
import com.abdownloadmanager.shared.ui.widget.menu.custom.MyDropDown
import com.abdownloadmanager.shared.ui.widget.menu.custom.SubMenu
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import ir.amirab.util.compose.action.MenuItem
import com.abdownloadmanager.shared.util.div
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.ui.widget.Tooltip
import com.abdownloadmanager.shared.util.ui.LocalMultiplatformScrollbarStyle
import com.abdownloadmanager.shared.util.ui.MultiplatformHorizontalScrollbar
import com.abdownloadmanager.shared.util.ui.needScroll
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.StringSource
import androidx.compose.ui.graphics.Color
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp

@Composable
fun Actions(
    list: List<MenuItem>,
    showLabels: Boolean,
) {
    val scrollState = rememberScrollState()
    Column {
        Row(
            Modifier
                .height(IntrinsicSize.Max)
                .horizontalScroll(scrollState)
        ) {
            for (a in list) {
                when (a) {
                    MenuItem.Separator -> {
                        Spacer(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .width(1.dp)
                                .background(myColors.onBackground / 5)
                        )
                    }

                    is MenuItem.SingleItem -> {
                        ActionButton(Modifier, a, showLabels)
                    }

                    is MenuItem.SubMenu -> {
                        GroupActionButton(Modifier, a, showLabels)
                    }
                }
            }
        }
        val adapter = rememberScrollbarAdapter(scrollState)
        if (adapter.needScroll()) {
            MultiplatformHorizontalScrollbar(
                adapter = adapter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    action: MenuItem.SingleItem,
    showLabels: Boolean,
) {
    val enabled by action.isEnabled.collectAsState()
    Column(modifier) {
        ActionIconWithLabel(
            title = action.title.collectAsState().value,
            icon = action.icon.collectAsState().value,
            showLabels = showLabels,
            onClick = {
                action()
            },
            enabled = enabled
        )
    }
}

@Composable
private fun GroupActionButton(
    modifier: Modifier = Modifier,
    action: MenuItem.SubMenu,
    showLabels: Boolean,
) {
    val enabled by action.isEnabled.collectAsState()
    var showSubMenu by remember { mutableStateOf(false) }
    Column(modifier) {
        ActionIconWithLabel(
            title = action.title.collectAsState().value,
            icon = action.icon.collectAsState().value,
            showLabels = showLabels,
            onClick = {
                showSubMenu = !showSubMenu
            },
            enabled = enabled
        )
        val close = {
            showSubMenu = false
        }
        if (enabled && showSubMenu) {
            MyDropDown(onDismissRequest = close) {
                val items by action.items.collectAsState()
                SubMenu(subMenu = items, onRequestClose = close)
            }
        }
    }
}

/** IDM button names on the toolbar (the menus keep the full names) */
private fun toolbarLabelOf(icon: IconSource?, title: String): String {
    return when (icon) {
        MyIcons.add -> tr("Add URL", "إضافة رابط").getString()
        MyIcons.pause -> tr("Stop", "إيقاف").getString()
        MyIcons.settings -> tr("Options", "الخيارات").getString()
        MyIcons.queue -> tr("Scheduler", "المجدول").getString()
        MyIcons.queueStart -> tr("Start Queue", "بدء الطابور").getString()
        MyIcons.queueStop -> tr("Stop Queue", "إيقاف الطابور").getString()
        else -> title
    }
}

/** IDM style colorful toolbar: each kind of action has its own colored tile, the same in every theme */
private fun toolbarColorOf(icon: IconSource): Color {
    return when (icon) {
        MyIcons.add -> Color(0xFF2F80ED)
        MyIcons.resume, MyIcons.queueStart -> Color(0xFF27AE60)
        MyIcons.pause -> Color(0xFFF2994A)
        MyIcons.stop -> Color(0xFFE67E22)
        MyIcons.remove, MyIcons.clear -> Color(0xFFEB5757)
        MyIcons.queueStop -> Color(0xFFC0392B)
        MyIcons.settings -> Color(0xFF607D8B)
        MyIcons.queue -> Color(0xFF9B51E0)
        MyIcons.share -> Color(0xFF00A3A3)
        MyIcons.earth -> Color(0xFF16A085)
        else -> Color(0xFF3F6AD8)
    }
}

/** our own drawing: a raised rounded tile with a light-to-dark gradient and the white icon on it */
@Composable
private fun ToolbarTile(icon: IconSource, size: androidx.compose.ui.unit.Dp) {
    val color = toolbarColorOf(icon)
    val shape = RoundedCornerShape(size * 0.26f)
    Box(
        Modifier
            .size(size)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.18f))))
            .border(1.dp, lerp(color, Color.Black, 0.25f).copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // glossy top half
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))))
        )
        MyIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f),
            tint = Color.White,
        )
    }
}

@Composable
private fun ActionIconWithLabel(
    title: StringSource,
    icon: IconSource?,
    showLabels: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    OptionalTooltip(
        title.takeIf { !showLabels },
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .ifThen(!enabled) {
                    alpha(0.5f)
                }
                .widthIn(min = if (showLabels) 64.dp else 0.dp)
                .padding(horizontal = if (showLabels) 10.dp else 12.dp, vertical = if (showLabels) 6.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WithContentColor(myColors.onBackground) {
                WithContentAlpha(1f) {
                    icon?.let {
                        // large colored tile with the label under it, like the classic IDM toolbar
                        ToolbarTile(it, if (showLabels) 32.dp else 28.dp)
                    }
                    if (showLabels) {
                        Spacer(Modifier.size(4.dp))
                        Text(toolbarLabelOf(icon, title.rememberString()), maxLines = 1, fontSize = myTextSizes.sm)
                    }
                }
            }
        }
    }

}

@Composable
private fun OptionalTooltip(
    tooltip: StringSource?,
    content: @Composable () -> Unit
) {
    if (tooltip != null) {
        Tooltip(tooltip) {
            content()
        }
    } else {
        content()
    }
}
