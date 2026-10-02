package cg.creamgod45.localization.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.EventObject
import javax.swing.AbstractAction
import javax.swing.AbstractCellEditor
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.table.TableCellEditor
import javax.swing.text.DefaultEditorKit

/**
 * In-place cell editing like the AI source preview table: double-click (or F2) edits the cell directly in the table
 * and never opens a dialog.
 */
internal object InlineTableEditing {
    private val LOG = Logger.getInstance(InlineTableEditing::class.java)
    private const val MAX_TOOLTIP_TEXT = 2_000
    private val lineBreak = Regex("\r\n|\r|\n")

    /**
     * @param isInlineCell whether a view cell belongs to the editable value columns, editable or not.
     * @param onNotEditable called when a value cell is double-clicked but cannot be edited in place.
     */
    fun install(
        table: JTable,
        isInlineCell: (viewRow: Int, viewColumn: Int) -> Boolean = { _, _ -> true },
        onNotEditable: (viewRow: Int, viewColumn: Int) -> Unit = { _, _ -> },
        quickEditEnabled: Boolean = true,
    ) {
        // The native expanded-cell preview covers truncated cells with an overlay that swallows the double-click,
        // so it is turned off while quick editing is on; callers show the full text in a tooltip instead.
        (table as? JBTable)?.setExpandableItemsEnabled(!quickEditEnabled)
        // Editing starts only by double-click or F2 so typing never overwrites a value by accident.
        table.putClientProperty("JTable.autoStartsEdit", false)
        table.setDefaultEditor(String::class.java, MultiLineCellEditor())
        val starter = EditStarter(table, isInlineCell, onNotEditable)
        table.addMouseListener(DoubleClickEditStarter(table, starter))
        // F2 is bound to "Next Highlighted Error" in the default keymap, so the IDE consumes it before Swing's own
        // startEditing binding. A component shortcut on the table wins over the global keymap while the table has focus.
        object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(event: AnActionEvent) {
                starter.startAtLead()
            }
        }.registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), table)
    }

    /** Starts in-place editing of one view cell for both the mouse and the keyboard entry points. */
    internal class EditStarter(
        private val table: JTable,
        private val isInlineCell: (viewRow: Int, viewColumn: Int) -> Boolean,
        private val onNotEditable: (viewRow: Int, viewColumn: Int) -> Unit,
    ) {
        fun start(
            viewRow: Int,
            viewColumn: Int,
        ): Boolean {
            if (viewRow !in 0 until table.rowCount || viewColumn !in 0 until table.columnCount) return false
            if (!isInlineCell(viewRow, viewColumn)) return false
            if (table.isEditing && table.editingRow == viewRow && table.editingColumn == viewColumn) return true
            if (!table.isCellEditable(viewRow, viewColumn)) {
                LOG.info("In-place edit refused for view cell ($viewRow, $viewColumn): the model reports it as not editable")
                onNotEditable(viewRow, viewColumn)
                return false
            }
            // No event is passed so the editor's own click-count check cannot veto a redispatched press.
            if (!table.editCellAt(viewRow, viewColumn)) {
                LOG.info("In-place edit could not start for view cell ($viewRow, $viewColumn): editCellAt returned false")
                return false
            }
            table.editorComponent?.requestFocusInWindow()
            return true
        }

        /** The focused (lead) cell, which is what F2 edits. */
        fun startAtLead(): Boolean = start(table.selectionModel.leadSelectionIndex, table.columnModel.selectionModel.leadSelectionIndex)
    }

    /** Text of the active in-place editor, if any. */
    fun editorText(table: JTable): String? = (table.editorComponent as? MultiLineEditorComponent)?.textArea?.text

    fun setEditorText(
        table: JTable,
        text: String,
    ) {
        (table.editorComponent as? MultiLineEditorComponent)?.textArea?.let { area ->
            area.text = text
            area.requestFocusInWindow()
        }
    }

    /** Scroll pane that hands focus to its text area, so F2 and double-click both put the caret in the value. */
    internal class MultiLineEditorComponent(
        val textArea: JBTextArea,
    ) : JBScrollPane(textArea, VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
        init {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }

        override fun requestFocus() = textArea.requestFocus()

        override fun requestFocusInWindow(): Boolean = textArea.requestFocusInWindow()
    }

    /**
     * Wrapping text-area editor so long and multi-line values can be edited in place. While editing, the row grows to
     * show the wrapped text (up to [MAX_EDITOR_LINES] lines, then it scrolls) and shrinks back afterwards.
     * Enter saves, Shift+Enter inserts a line break, Esc cancels (JTable's own cancel binding).
     */
    internal class MultiLineCellEditor :
        AbstractCellEditor(),
        TableCellEditor {
        private val textArea =
            JBTextArea().apply {
                lineWrap = true
                wrapStyleWord = true
                border = JBUI.Borders.empty(1, 2)
            }
        private val component = MultiLineEditorComponent(textArea)
        private var table: JTable? = null
        private var resizedRow = -1
        private var originalRowHeight = 0

        init {
            textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "languageManager.stopEditing")
            textArea.actionMap.put(
                "languageManager.stopEditing",
                object : AbstractAction() {
                    override fun actionPerformed(event: ActionEvent?) {
                        stopCellEditing()
                    }
                },
            )
            textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), DefaultEditorKit.insertBreakAction)
        }

        override fun isCellEditable(event: EventObject?): Boolean = event !is MouseEvent || event.clickCount >= 2

        override fun getCellEditorValue(): Any = textArea.text

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int,
        ): Component {
            restoreRowHeight()
            this.table = table
            textArea.text = value?.toString().orEmpty()
            textArea.font = table.font
            textArea.caretPosition = textArea.document.length
            growRow(table, row, column)
            return component
        }

        override fun stopCellEditing(): Boolean = super.stopCellEditing().also { if (it) restoreRowHeight() }

        override fun cancelCellEditing() {
            super.cancelCellEditing()
            restoreRowHeight()
        }

        private fun growRow(
            table: JTable,
            row: Int,
            column: Int,
        ) {
            val width =
                table.columnModel
                    .getColumn(column)
                    .width
                    .coerceAtLeast(JBUI.scale(40))
            textArea.setSize(width, Short.MAX_VALUE.toInt())
            val lineHeight = textArea.getFontMetrics(textArea.font).height
            val insets = textArea.insets
            val wanted = textArea.preferredSize.height.coerceAtMost(lineHeight * MAX_EDITOR_LINES + insets.top + insets.bottom)
            val current = table.getRowHeight(row)
            if (wanted > current) {
                resizedRow = row
                originalRowHeight = current
                table.setRowHeight(row, wanted)
            }
        }

        private fun restoreRowHeight() {
            val table = table ?: return
            if (resizedRow in 0 until table.rowCount) table.setRowHeight(resizedRow, originalRowHeight)
            resizedRow = -1
        }
    }

    /**
     * Detects a double-click from two left presses on the same cell within the platform multi-click interval instead of
     * trusting [MouseEvent.getClickCount]. AWT counts clicks per window, so when the second press lands on an IDE popup
     * above the cell (the expanded-cell preview or a tooltip) and is redispatched to the table, it arrives with a click
     * count of 1 and a count-based check never starts editing.
     */
    internal class DoubleClickEditStarter(
        private val table: JTable,
        private val starter: EditStarter,
        private val multiClickInterval: () -> Long = ::platformMultiClickInterval,
    ) : MouseAdapter() {
        private var lastCell: Pair<Int, Int>? = null
        private var lastPressAt = 0L

        override fun mousePressed(event: MouseEvent) {
            if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger) return reset()
            val viewRow = table.rowAtPoint(event.point)
            val viewColumn = table.columnAtPoint(event.point)
            if (viewRow < 0 || viewColumn < 0) return reset()
            val cell = viewRow to viewColumn
            val isDoubleClick = cell == lastCell && event.`when` - lastPressAt in 0..multiClickInterval()
            lastCell = cell
            lastPressAt = event.`when`
            if (!isDoubleClick) return
            reset()
            starter.start(viewRow, viewColumn)
        }

        private fun reset() {
            lastCell = null
            lastPressAt = 0L
        }
    }

    private fun platformMultiClickInterval(): Long =
        (
            java.awt.Toolkit
                .getDefaultToolkit()
                .getDesktopProperty("awt.multiClickInterval") as? Int
        )?.toLong()
            ?: DEFAULT_MULTI_CLICK_INTERVAL_MS

    private const val DEFAULT_MULTI_CLICK_INTERVAL_MS = 500L
    private const val MAX_EDITOR_LINES = 8

    /** Full cell text when the rendered value does not fit its column (or spans lines), otherwise null. */
    fun truncatedCellText(
        table: JTable,
        viewRow: Int,
        viewColumn: Int,
    ): String? {
        val value = table.getValueAt(viewRow, viewColumn)?.toString().orEmpty()
        if (value.isEmpty()) return null
        if (lineBreak.containsMatchIn(value)) return value
        val renderer = table.prepareRenderer(table.getCellRenderer(viewRow, viewColumn), viewRow, viewColumn)
        return value.takeIf { renderer.preferredSize.width > table.getCellRect(viewRow, viewColumn, false).width }
    }

    /**
     * Escaped HTML tooltip that wraps only when a line would be wider than [maxWidth] pixels, so short texts keep their
     * natural width and long ones never run past the window or screen. Returns null when there is nothing to show.
     */
    fun tooltipHtml(
        fullText: String?,
        hint: String?,
        maxWidth: Int = FALLBACK_TOOLTIP_WIDTH,
        textWidth: (String) -> Int = { it.length * FALLBACK_CHAR_WIDTH },
    ): String? {
        if (fullText == null && hint == null) return null
        val limited = fullText?.let { text -> if (text.length > MAX_TOOLTIP_TEXT) text.take(MAX_TOOLTIP_TEXT) + "…" else text }
        val body = limited?.let { StringUtil.escapeXmlEntities(it).replace(lineBreak, "<br>") }
        val hintHtml = hint?.let(StringUtil::escapeXmlEntities)
        val content = listOfNotNull(body, hintHtml?.let { if (body != null) "<hr>$it" else it }).joinToString("")
        val widestLine = listOfNotNull(limited, hint).flatMap { it.split(lineBreak) }.maxOfOrNull(textWidth) ?: 0
        val style = if (widestLine > maxWidth) " style='width: ${maxWidth}px'" else ""
        return "<html><body$style>$content</body></html>"
    }

    /**
     * Wrap width for a tooltip shown over [table]: half of the smaller of its window and its screen, never below a
     * readable minimum. Falls back to a fixed width when the table is not on screen yet.
     */
    fun tooltipMaxWidth(table: JTable): Int {
        val windowWidth = SwingUtilities.getWindowAncestor(table)?.width?.takeIf { it > 0 }
        val screenWidth =
            table.graphicsConfiguration
                ?.bounds
                ?.width
                ?.takeIf { it > 0 }
        val available = listOfNotNull(windowWidth, screenWidth).minOrNull() ?: return JBUI.scale(FALLBACK_TOOLTIP_WIDTH)
        return (available / 2).coerceAtLeast(JBUI.scale(MIN_TOOLTIP_WIDTH))
    }

    private const val FALLBACK_TOOLTIP_WIDTH = 480
    private const val MIN_TOOLTIP_WIDTH = 280
    private const val FALLBACK_CHAR_WIDTH = 7
}
