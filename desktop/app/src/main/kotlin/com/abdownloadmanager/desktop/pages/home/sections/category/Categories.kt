package com.abdownloadmanager.desktop.pages.home.sections.category

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.ui.widget.ExpandableItem
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.abdownloadmanager.shared.ui.widget.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.home.dropDownloadItemsHere
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.home.category.DownloadStatusCategoryFilter
import com.abdownloadmanager.shared.ui.widget.DelayedTooltipPopup
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.rememberIconPainter
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.ifThen
import sh.calvin.reorderable.ReorderableColumn
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import ir.amirab.util.compose.IconSource
import sh.calvin.reorderable.ReorderableListItemScope


@Composable
private fun ReorderableListItemScope.CategoryFilterItem(
    modifier: Modifier,
    category: Category,
    isSelected: Boolean,
    onItemsDropped: (ids: List<Long>) -> Unit,
    onClick: () -> Unit,
    isDragging: Boolean,
) {
    var isDraggingOnMe by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val shouldShowDragIcon = isHovered && !isDraggingOnMe || isDragging
    Box(
        modifier
            .dropDownloadItemsHere(
                onDragIn = { isDraggingOnMe = true },
                onDragDone = { isDraggingOnMe = false },
                onItemsDropped = onItemsDropped,
            )
            .hoverable(interactionSource)
            .background(
                if (isSelected) {
                    myColors.onBackground / 0.05f
                } else Color.Transparent
            )
            .ifThen(isDraggingOnMe) {
                val infiniteTransition = rememberInfiniteTransition()
                val color by infiniteTransition.animateColor(
                    initialValue = myColors.primary,
                    targetValue = myColors.secondary,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                border(1.dp, color)
            }
            .selectable(
                selected = isSelected,
                onClick = onClick
            ),
    ) {
        if (isDraggingOnMe) {
            DelayedTooltipPopup(
                {},
                myStringResource(Res.string.move_to_this_category),
            )
        }
        // IDM tree: a guide line from the parent down to this folder
        val guideColor = myColors.onBackground / 0.25f
        Canvas(Modifier.matchParentSize().padding(start = 21.dp)) {
            val x = 0.5f
            drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f)))
            drawLine(guideColor, Offset(x, size.height / 2), Offset(10.dp.toPx(), size.height / 2), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f)))
        }
        Row(
            modifier = Modifier
                .padding(start = 32.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WithContentAlpha(if (isSelected) 1f else 0.85f) {
                val iconPainter = category.rememberIconPainter()
                TreeFolderIcon(iconPainter ?: MyIcons.folder)
                Spacer(Modifier.width(4.dp))
                Text(
                    category.name,
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = myTextSizes.base
                )
                AnimatedVisibility(
                    visible = shouldShowDragIcon,
                ) {
                    MyIcon(
                        MyIcons.grip,
                        null,
                        Modifier
                            .draggableHandle()
                            .size(16.dp)
                            .alpha(
                                if (isDragging) {
                                    1f
                                } else {
                                    0.5f
                                }
                            )
                    )
                }
            }
        }
        AnimatedVisibility(
            isSelected,
            modifier = Modifier.align(Alignment.CenterStart),
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
    }
}

/** IDM style tree expander, our own drawing: a small box with + or - */
@Composable
private fun TreeExpander(isExpanded: Boolean, onClick: () -> Unit) {
    val color = myColors.onBackground / 0.6f
    Box(
        Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp)) {
            val s = size.width
            drawRect(color, style = Stroke(1f))
            drawLine(color, Offset(s * 0.25f, s / 2), Offset(s * 0.75f, s / 2), 1.5f)
            if (!isExpanded) {
                drawLine(color, Offset(s / 2, s * 0.25f), Offset(s / 2, s * 0.75f), 1.5f)
            }
        }
    }
}

/** IDM style yellow folder, our own drawing, with the category's own icon small on it */
@Composable
private fun TreeFolderIcon(icon: IconSource, open: Boolean = false) {
    Box(Modifier.size(20.dp, 16.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val dark = Color(0xFFD4A017)
            val light = Color(0xFFFFD866)
            // the tab at the back
            drawRoundRect(dark, Offset(w * 0.04f, h * 0.06f), Size(w * 0.42f, h * 0.3f), CornerRadius(w * 0.06f))
            // the body
            drawRoundRect(
                Brush.verticalGradient(listOf(light, Color(0xFFF2B705))),
                Offset(0f, h * 0.2f),
                Size(w, h * 0.8f),
                CornerRadius(w * 0.08f),
            )
            if (open) {
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.06f, h * 0.28f), Size(w * 0.88f, h * 0.18f), CornerRadius(w * 0.04f))
            }
            drawRoundRect(dark, Offset(0f, h * 0.2f), Size(w, h * 0.8f), CornerRadius(w * 0.08f), style = Stroke(1f))
        }
        MyIcon(
            icon,
            null,
            Modifier.padding(top = 3.dp).size(9.dp),
            tint = Color(0xFF6B4E00),
        )
    }
}

@Composable
fun StatusFilterItem(
    isExpanded: Boolean,
    onRequestExpand: (Boolean) -> Unit,
    currentTypeCategoryFilter: Category?,
    currentStatusCategoryFilter: DownloadStatusCategoryFilter?,
    statusFilter: DownloadStatusCategoryFilter,
    categories: List<Category>,
    onCategoryReorderRequest: (index: Int, delta: Int) -> Unit,
    onItemsDroppedInCategory: (category: Category, downloadIds: List<Long>) -> Unit,
    onFilterChange: (
        typeFilter: Category?,
    ) -> Unit,
    onRequestOpenOptionMenu: (Category?) -> Unit,
) {
    val isStatusSelected = currentStatusCategoryFilter == statusFilter
    val isSelected = isStatusSelected && currentTypeCategoryFilter == null
    ExpandableItem(
        modifier = Modifier
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
            ) {
                onRequestOpenOptionMenu(null)
            },
        isExpanded = isExpanded,
        header = {
            Box(
                Modifier
                    .height(IntrinsicSize.Max)
                    .background(
                        if (isSelected) {
                            myColors.onBackground / 0.05f
                        } else Color.Transparent
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = {
                            if (!isExpanded) {
                                onRequestExpand(true)
                            }
                            onFilterChange(null)
                        }
                    )
            ) {
                Row(
                    Modifier.padding(vertical = 4.dp)
                        .padding(start = 4.dp)
                        .padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WithContentAlpha(if (isSelected) 1f else 0.85f) {
                        // IDM tree: [+] / [-] box to open and close the folder
                        TreeExpander(isExpanded) { onRequestExpand(!isExpanded) }
                        Spacer(Modifier.width(4.dp))
                        TreeFolderIcon(statusFilter.icon, open = isExpanded)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            statusFilter.name.rememberString(),
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
                    modifier = Modifier.align(Alignment.CenterStart),
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
            }
        },
        body = {
            ReorderableColumn(
                list = categories,
                onSettle = { from, to ->
                    onCategoryReorderRequest(from, to - from)
                },
            ) { index, category, isDragging ->
                key(category.id) {
                    ReorderableItem {
                        CategoryFilterItem(
                            modifier = Modifier
                                .onClick(
                                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                ) {
                                    onRequestOpenOptionMenu(category)
                                },
                            category = category,
                            isSelected = isStatusSelected && currentTypeCategoryFilter == category,
                            onItemsDropped = {
                                onItemsDroppedInCategory(category, it)
                            },
                            onClick = {
                                onFilterChange(category)
                            },
                            isDragging = isDragging,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    )
}

