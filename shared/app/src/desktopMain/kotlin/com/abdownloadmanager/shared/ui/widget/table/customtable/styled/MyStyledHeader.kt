package com.abdownloadmanager.shared.ui.widget.table.customtable.styled

import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableScope
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.div
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * classic Windows list header (IDM style): flat grey strip with a line under it,
 * no rounded card or shadow
 */
@Composable
fun TableScope.MyStyledTableHeader(
    itemHorizontalPadding: Dp,
    content: @Composable () -> Unit,
) {
    val line = myColors.onBackground / 0.2f
    WithContentAlpha(0.9f) {
        Box(
            Modifier
                .widthIn(getTableSize().visibleWidth)
                .background(myColors.surface)
                .drawBehind {
                    drawLine(line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
                }
                .padding(vertical = 3.dp, horizontal = itemHorizontalPadding)
        ) {
            content()
        }
    }
}
