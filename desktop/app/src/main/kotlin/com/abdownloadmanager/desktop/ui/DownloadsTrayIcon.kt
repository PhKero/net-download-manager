package com.abdownloadmanager.desktop.ui

import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.util.compose.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.JDialog
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * Downloads whose window was "sent to tray": they stay in the downloads tray icon
 * even when paused, until they finish, are reopened or closed with X / Cancel.
 */
object SentToTray {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    val ids: StateFlow<Set<Long>> = _ids

    fun add(id: Long) = _ids.update { it + id }
    fun remove(id: Long) = _ids.update { it - id }
    fun keepOnly(existing: Set<Long>) = _ids.update { it.intersect(existing) }
}

/**
 * IDM style: while something downloads, a second tray icon sits next to the app icon.
 * One icon for all downloads (not one per download). Its arrow fills with the total progress,
 * hovering shows how many downloads and the total speed, clicking lists them and opens
 * the chosen download's window. It goes away when nothing downloads any more.
 */
class DownloadsTrayIcon(
    private val onOpenDownload: (Long) -> Unit,
    private val onOpenList: () -> Unit,
) {
    private var trayIcon: TrayIcon? = null
    private var items: List<ProcessingDownloadItemState> = emptyList()
    private var lastPercent = -1
    private val supported = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    // a JPopupMenu needs a window to belong to; this one is never seen
    private val popupOwner by lazy {
        JDialog().apply {
            isUndecorated = true
            type = java.awt.Window.Type.UTILITY
            setSize(1, 1)
        }
    }

    fun update(active: List<ProcessingDownloadItemState>) {
        if (!supported) return
        SwingUtilities.invokeLater {
            items = active
            if (active.isEmpty()) {
                remove()
                return@invokeLater
            }
            val icon = trayIcon ?: createIcon().also { trayIcon = it }
            val percent = totalPercent(active)
            if (percent != lastPercent) {
                icon.image = drawImage(percent)
                lastPercent = percent
            }
            icon.toolTip = tooltipOf(active)
        }
    }

    fun dispose() {
        SwingUtilities.invokeLater { remove() }
    }

    private fun remove() {
        trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        trayIcon = null
        lastPercent = -1
    }

    private fun createIcon(): TrayIcon {
        val icon = TrayIcon(drawImage(0), tooltipOf(items))
        icon.isImageAutoSize = true
        icon.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                // left or right click: the list of downloads; with only one, a left click opens it straight away
                if (SwingUtilities.isLeftMouseButton(e) && items.size == 1) {
                    onOpenDownload(items.first().id)
                } else {
                    showMenu(e.xOnScreen, e.yOnScreen)
                }
            }
        })
        runCatching { SystemTray.getSystemTray().add(icon) }
        return icon
    }

    private fun showMenu(x: Int, y: Int) {
        val menu = JPopupMenu()
        val arabic = isArabic()
        if (arabic) menu.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        for (item in items.take(15)) {
            val line = buildString {
                append(item.name.take(45))
                item.percent?.let { append("   ").append(it).append('%') }
                if (!item.isRunning()) append("   ").append(tr("Paused", "متوقف مؤقتًا").getString())
                else if (item.speed > 0) append("   ").append(humanSpeed(item.speed))
            }
            menu.add(JMenuItem(line).apply {
                if (arabic) componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
                addActionListener { onOpenDownload(item.id) }
            })
        }
        menu.addSeparator()
        menu.add(JMenuItem(tr("Show all downloads", "عرض كل التحميلات").getString()).apply {
            addActionListener { onOpenList() }
        })
        popupOwner.setLocation(x, y)
        popupOwner.isVisible = true
        menu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {}
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                popupOwner.isVisible = false
            }

            override fun popupMenuCanceled(e: PopupMenuEvent) {
                popupOwner.isVisible = false
            }
        })
        // open upwards, the taskbar is usually at the bottom
        menu.show(popupOwner, 0, -menu.preferredSize.height)
    }

    private fun tooltipOf(active: List<ProcessingDownloadItemState>): String {
        val running = active.filter { it.isRunning() }
        val speed = running.sumOf { it.speed }
        val count = running.size
        val paused = active.size - count
        var text = tr("$count downloading", "$count قيد التحميل").getString()
        if (paused > 0) text += tr(", $paused paused", "، $paused متوقف").getString()
        val withSpeed = if (speed > 0) "$text - ${humanSpeed(speed)}" else text
        val percent = totalPercent(active)
        return "$withSpeed - $percent%"
    }

    private fun totalPercent(active: List<ProcessingDownloadItemState>): Int {
        val total = active.sumOf { it.contentLength.coerceAtLeast(0) }
        val done = active.sumOf { it.progress.coerceAtLeast(0) }
        return if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100)
        else active.mapNotNull { it.percent }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    }

    /** our own drawing: a blue rounded square with a white arrow, filled green from the bottom by the progress */
    private fun drawImage(percent: Int): BufferedImage {
        val size = runCatching { SystemTray.getSystemTray().trayIconSize.width }.getOrDefault(16).coerceAtLeast(16) * 2
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val s = size.toFloat()
        val box = RoundRectangle2D.Float(s * 0.04f, s * 0.04f, s * 0.92f, s * 0.92f, s * 0.3f, s * 0.3f)
        g.color = Color(0x1E, 0x6F, 0xD9)
        g.fill(box)
        // progress fills the box from the bottom
        val filled = s * 0.92f * percent / 100f
        g.clip = box
        g.color = Color(0x2E, 0xB8, 0x5C)
        g.fillRect(0, (s * 0.96f - filled).toInt(), size, filled.toInt() + 1)
        g.clip = null
        // white down arrow
        g.composite = AlphaComposite.SrcOver
        g.color = Color.WHITE
        g.stroke = BasicStroke(s * 0.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.drawLine((s * 0.5f).toInt(), (s * 0.2f).toInt(), (s * 0.5f).toInt(), (s * 0.66f).toInt())
        val head = Path2D.Float().apply {
            moveTo(s * 0.28f, s * 0.48f)
            lineTo(s * 0.5f, s * 0.7f)
            lineTo(s * 0.72f, s * 0.48f)
        }
        g.draw(head)
        g.drawLine((s * 0.26f).toInt(), (s * 0.82f).toInt(), (s * 0.74f).toInt(), (s * 0.82f).toInt())
        g.dispose()
        return img
    }

    private fun ProcessingDownloadItemState.isRunning() = status is DownloadJobStatus.IsActive

    private fun isArabic() = runCatching {
        ir.amirab.util.compose.localizationmanager.LanguageManager.instance.isRtl.value
    }.getOrDefault(false)

    private fun humanSpeed(bytesPerSecond: Long): String {
        val kb = bytesPerSecond / 1024.0
        return when {
            kb >= 1024 * 1024 -> "%.1f GB/s".format(kb / (1024 * 1024))
            kb >= 1024 -> "%.1f MB/s".format(kb / 1024)
            else -> "%.0f KB/s".format(kb)
        }
    }
}
