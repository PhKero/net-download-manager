package com.abdownloadmanager.shared.ui.widget

import ir.amirab.util.compose.IconSource
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.abdownloadmanager.shared.util.div
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import ir.amirab.util.compose.StringSource
import ir.amirab.util.ifThen


@Composable
fun MyTabRow(content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    ) {
        content()
    }
}

@Composable
fun MyTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: IconSource,
    title: StringSource,
    selectionBackground: Color = myColors.background,
) {
    // classic Windows tab: framed on top and sides, the chosen one is lighter and a bit taller
    val line = myColors.onBackground / 0.25f
    Box(
        Modifier
            .padding(top = if (selected) 0.dp else 2.dp)
            .background(if (selected) selectionBackground else myColors.surface)
            .drawBehind {
                drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                drawLine(line, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f)
            }
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            title.rememberString(),
            maxLines = 1,
            fontSize = myTextSizes.base,
        )
    }
}
