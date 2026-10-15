package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.EditorKeyMatchDto
import cg.creamgod45.localization.JoinedTranslationRow
import cg.creamgod45.localization.LanguageEntryDto
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants

/** What the hover popup's links do; the service supplies them so the popup itself stays free of RPC and tool windows. */
internal interface EditorKeyHoverActions {
    fun openInTable(row: JoinedTranslationRow)

    fun edit(row: JoinedTranslationRow)

    fun create(match: EditorKeyMatchDto)
}

/** Builds the experimental editor key hover popup (issue #38). */
internal object EditorKeyHoverPopup {
    fun create(
        match: EditorKeyMatchDto,
        entries: List<LanguageEntryDto>,
        anchor: RelativePoint,
        actions: EditorKeyHoverActions,
    ): JBPopup {
        var popup: JBPopup? = null
        val run: (() -> Unit) -> Unit = { action ->
            popup?.cancel()
            action()
        }
        val rows = EditorKeyHoverSupport.rowsFor(match, entries)
        val locales = EditorKeyHoverSupport.locales(entries)
        val title =
            JBLabel(match.candidate).apply {
                font = font.deriveFont(Font.BOLD)
                border = JBUI.Borders.emptyBottom(6)
            }
        val body = ScrollableColumn()
        val valueAreas = mutableListOf<JBTextArea>()
        val localeLabels = mutableListOf<JBLabel>()
        if (rows.isEmpty()) {
            body.add(hint(message("editor.hover.missing.key")))
            body.add(linkRow(ActionLink(message("editor.hover.action.create")) { run { actions.create(match) } }))
        } else {
            rows.take(EditorKeyHoverSupport.MAX_POPUP_ROWS).forEachIndexed { index, row ->
                if (index > 0) body.add(JBLabel(" "))
                if (rows.size > 1) body.add(JBLabel(rowIdentity(row)).apply { font = font.deriveFont(Font.BOLD) })
                body.add(
                    linkRow(
                        ActionLink(message("editor.hover.action.open.table")) { run { actions.openInTable(row) } },
                        ActionLink(message("editor.hover.action.edit")) { run { actions.edit(row) } },
                    ),
                )
                if (rows.size == 1 && row.namespace.isNotEmpty()) body.add(hint(message("editor.hover.namespace", row.namespace)))
                EditorKeyHoverSupport.translations(row, locales).forEach { (locale, value) ->
                    val localeLabel = JBLabel(locale).apply { verticalAlignment = SwingConstants.TOP }
                    val area = valueArea(value?.let(EditorKeyHoverSupport::displayValue), value == null)
                    localeLabels += localeLabel
                    valueAreas += area
                    body.add(
                        JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                            isOpaque = false
                            add(localeLabel, BorderLayout.WEST)
                            add(area, BorderLayout.CENTER)
                        },
                    )
                }
            }
            if (rows.size > EditorKeyHoverSupport.MAX_POPUP_ROWS) {
                body.add(hint(message("editor.hover.more.rows", rows.size - EditorKeyHoverSupport.MAX_POPUP_ROWS)))
            }
        }
        val scrollPane =
            JBScrollPane(body).apply {
                border = JBUI.Borders.empty()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                isOpaque = false
                viewport.isOpaque = false
            }
        val root =
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 10)
                add(title, BorderLayout.NORTH)
                add(scrollPane, BorderLayout.CENTER)
            }
        layOut(root, title, scrollPane, body, localeLabels, valueAreas, ScreenUtil.getScreenRectangle(anchor.screenPoint))
        popup =
            JBPopupFactory
                .getInstance()
                .createComponentPopupBuilder(root, null)
                .setRequestFocus(false)
                .setFocusable(true)
                .setResizable(false)
                .setMovable(false)
                .setCancelOnClickOutside(true)
                .setCancelOnWindowDeactivation(true)
                .setCancelKeyEnabled(true)
                .createPopup()
        return popup
    }

    /** Fixes the popup size from the screen limits, wrapping long translations at the final width so nothing is clipped. */
    private fun layOut(
        root: JPanel,
        title: JComponent,
        scrollPane: JBScrollPane,
        body: JComponent,
        localeLabels: List<JBLabel>,
        valueAreas: List<JBTextArea>,
        screen: Rectangle,
    ) {
        val insets = root.insets
        val scrollBarWidth = scrollPane.verticalScrollBar.preferredSize.width
        val localeWidth = (localeLabels.maxOfOrNull { it.preferredSize.width } ?: 0).coerceIn(0, JBUI.scale(140))
        localeLabels.forEach { it.preferredSize = Dimension(localeWidth, it.preferredSize.height) }
        // Unwrapped width of the longest translation line, so short values give a compact popup and long ones the widest.
        val valueTextWidth =
            valueAreas.maxOfOrNull { area ->
                val metrics = area.getFontMetrics(area.font)
                area.text.lineSequence().maxOfOrNull { metrics.stringWidth(it.take(400)) } ?: 0
            } ?: 0
        val naturalWidth =
            maxOf(title.preferredSize.width, body.preferredSize.width, localeWidth + JBUI.scale(8) + valueTextWidth) +
                scrollBarWidth + insets.left + insets.right
        val width = EditorKeyHoverSupport.popupSize(Dimension(naturalWidth, 1), screen, JBUI::scale).width
        val contentWidth = (width - insets.left - insets.right - scrollBarWidth).coerceAtLeast(JBUI.scale(80))
        val valueWidth = (contentWidth - localeWidth - JBUI.scale(8)).coerceAtLeast(JBUI.scale(60))
        valueAreas.forEach { area ->
            area.setSize(valueWidth, Short.MAX_VALUE.toInt())
            area.preferredSize = Dimension(valueWidth, area.preferredSize.height)
        }
        val bodyHeight = body.preferredSize.height
        val size =
            EditorKeyHoverSupport.popupSize(
                Dimension(width, title.preferredSize.height + bodyHeight + insets.top + insets.bottom),
                screen,
                JBUI::scale,
            )
        val scrollHeight = (size.height - title.preferredSize.height - insets.top - insets.bottom).coerceAtLeast(JBUI.scale(24))
        scrollPane.preferredSize = Dimension(contentWidth + scrollBarWidth, minOf(scrollHeight, bodyHeight.coerceAtLeast(1)))
        root.preferredSize =
            Dimension(size.width, title.preferredSize.height + scrollPane.preferredSize.height + insets.top + insets.bottom)
    }

    private fun rowIdentity(row: JoinedTranslationRow): String =
        if (row.namespace.isEmpty()) row.key else message("editor.hover.row.identity", row.namespace, row.key)

    private fun hint(text: String) =
        JBLabel(text).apply {
            foreground = UIUtil.getContextHelpForeground()
        }

    private fun linkRow(vararg links: ActionLink) =
        JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
            links.forEachIndexed { index, link ->
                if (index > 0) add(JBLabel("  ·  ").apply { foreground = UIUtil.getContextHelpForeground() })
                add(link)
            }
        }

    private fun valueArea(
        value: String?,
        missing: Boolean,
    ) = JBTextArea(value ?: message("editor.hover.value.missing")).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = JBUI.Borders.empty()
        font = UIUtil.getLabelFont()
        if (missing) foreground = UIUtil.getContextHelpForeground()
    }

    /** A vertical stack that follows the viewport width, so long translations wrap instead of scrolling sideways. */
    private class ScrollableColumn :
        JPanel(VerticalLayout(JBUI.scale(2))),
        Scrollable {
        init {
            isOpaque = false
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int,
        ): Int = JBUI.scale(16)

        override fun getScrollableBlockIncrement(
            visibleRect: Rectangle,
            orientation: Int,
            direction: Int,
        ): Int = visibleRect.height

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false
    }
}
