package cg.creamgod45.localization.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.MouseEvent
import javax.swing.table.AbstractTableModel

private val MULTI_LINE = "multi" + System.lineSeparator() + "line"

class InlineTableEditingTest : BasePlatformTestCase() {
    private class Model : AbstractTableModel() {
        val values = mutableListOf(mutableListOf("messages", "title", "Title"), mutableListOf("messages", "body", "Body"))
        var edited: Triple<Int, Int, Any?>? = null

        override fun getRowCount() = values.size

        override fun getColumnCount() = 3

        override fun getValueAt(
            row: Int,
            column: Int,
        ): Any = values[row][column]

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        override fun isCellEditable(
            rowIndex: Int,
            columnIndex: Int,
        ) = columnIndex == 2 && values[rowIndex][2] != MULTI_LINE

        override fun setValueAt(
            aValue: Any?,
            rowIndex: Int,
            columnIndex: Int,
        ) {
            edited = Triple(rowIndex, columnIndex, aValue)
        }
    }

    private val notEditable = mutableListOf<Pair<Int, Int>>()

    private fun table(model: Model) =
        RowHighlightTable(model).apply {
            autoCreateRowSorter = true
            cellSelectionEnabled = true
            rowSelectionAllowed = true
            columnSelectionAllowed = true
            InlineTableEditing.install(
                this,
                isInlineCell = { _, column -> column == 2 },
                onNotEditable = { row, column -> notEditable += row to column },
            )
            setSize(600, 200)
            doLayout()
        }

    private fun RowHighlightTable.doubleClick(
        row: Int,
        column: Int,
    ) {
        val point = getCellRect(row, column, true).let { java.awt.Point(it.centerX.toInt(), it.centerY.toInt()) }
        listOf(1, 2).forEach { clicks ->
            listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED).forEach { id ->
                val modifiers = if (id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED) 0 else MouseEvent.BUTTON1_DOWN_MASK
                dispatchEvent(
                    MouseEvent(this, id, System.currentTimeMillis(), modifiers, point.x, point.y, clicks, false, MouseEvent.BUTTON1),
                )
            }
        }
    }

    fun testDoubleClickStartsInPlaceEditingOfTheValueCell() {
        val model = Model()
        val table = table(model)

        table.doubleClick(0, 2)

        assertTrue("Double-click must start in-place editing", table.isEditing)
        assertEquals(0, table.editingRow)
        assertEquals(2, table.editingColumn)
    }

    fun testDoubleClickOnReadOnlyColumnDoesNotEdit() {
        val table = table(Model())

        table.doubleClick(0, 1)

        assertFalse(table.isEditing)
    }

    private fun RowHighlightTable.press(
        row: Int,
        column: Int,
        at: Long,
    ) {
        val cell = getCellRect(row, column, true)
        val x = cell.centerX.toInt()
        val y = cell.centerY.toInt()
        // clickCount stays 1: this is what the table receives when the second press hit an IDE popup window above the cell
        // (expanded-cell preview or tooltip) and was redispatched, because AWT counts clicks per window.
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_PRESSED, at, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1))
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_RELEASED, at + 1, 0, x, y, 1, false, MouseEvent.BUTTON1))
        dispatchEvent(MouseEvent(this, MouseEvent.MOUSE_CLICKED, at + 1, 0, x, y, 1, false, MouseEvent.BUTTON1))
    }

    fun testRedispatchedSecondPressWithClickCountOneStillStartsEditing() {
        val table = table(Model())
        val start = System.currentTimeMillis()

        table.press(1, 2, start)
        assertFalse("A single press only selects", table.isEditing)
        table.press(1, 2, start + 150)

        assertTrue("Two quick presses on the same cell edit in place even with clickCount 1", table.isEditing)
        assertEquals(1, table.editingRow)
        assertEquals(2, table.editingColumn)
    }

    fun testSlowPressesOrPressesOnDifferentCellsDoNotEdit() {
        val table = table(Model())
        val start = System.currentTimeMillis()

        table.press(0, 2, start)
        table.press(0, 2, start + 5_000)
        assertFalse("Presses slower than the multi-click interval are two single clicks", table.isEditing)

        table.press(0, 2, start + 10_000)
        table.press(1, 2, start + 10_100)
        assertFalse("Quick presses on different cells are not a double-click", table.isEditing)
    }

    fun testCellThatCannotBeEditedInPlaceNeverOpensADialogAndReportsInstead() {
        val model = Model().apply { values[0][2] = MULTI_LINE }
        val table = table(model)

        table.doubleClick(0, 2)

        assertFalse(table.isEditing)
        assertEquals(listOf(0 to 2), notEditable)
    }

    fun testEnterCommitsTheEditedValueToTheModel() {
        val model = Model()
        val table = table(model)
        table.doubleClick(0, 2)
        InlineTableEditing.setEditorText(table, "New title")

        assertTrue(table.cellEditor.stopCellEditing())

        assertEquals(Triple(0, 2, "New title"), model.edited)
        assertTrue(notEditable.isEmpty())
    }

    fun testQuickEditTurnsOffTheExpandedCellPreviewThatSwallowsDoubleClicks() {
        // The headless test environment installs a no-op expandable handler, so record what the table was asked to do.
        class RecordingTable : com.intellij.ui.table.JBTable(Model()) {
            var expandable: Boolean? = null

            override fun setExpandableItemsEnabled(enabled: Boolean) {
                expandable = enabled
                super.setExpandableItemsEnabled(enabled)
            }
        }
        val enabled = RecordingTable().also { InlineTableEditing.install(it, quickEditEnabled = true) }
        val disabled = RecordingTable().also { InlineTableEditing.install(it, quickEditEnabled = false) }

        assertEquals("Expanded preview overlay must be off while quick editing is on", false, enabled.expandable)
        assertEquals("Native expanded preview is restored when quick editing is off", true, disabled.expandable)
    }

    fun testTooltipWrapsOnlyWhenWiderThanTheAvailableWidth() {
        val charWidth: (String) -> Int = { it.length * 10 }

        val short = InlineTableEditing.tooltipHtml("Short value", null, maxWidth = 400, textWidth = charWidth)!!
        val long = InlineTableEditing.tooltipHtml("x".repeat(100), null, maxWidth = 400, textWidth = charWidth)!!
        val longHint = InlineTableEditing.tooltipHtml(null, "h".repeat(60), maxWidth = 400, textWidth = charWidth)!!

        assertFalse("Short text keeps its natural width", short.contains("width:"))
        assertTrue(long, long.contains("style='width: 400px'"))
        assertTrue(longHint, longHint.contains("style='width: 400px'"))
    }

    fun testTooltipWidthFollowsTheWindowAndFallsBackWhenOffScreen() {
        val table = table(Model())
        assertEquals(
            "Off-screen tables use the fixed fallback width",
            com.intellij.util.ui.JBUI
                .scale(480),
            InlineTableEditing.tooltipMaxWidth(table),
        )
    }

    fun testLongValueDoubleClickStillEditsAndItsFullTextIsAvailableAsTooltip() {
        val longValue = "Long translation ".repeat(40).trim()
        val model = Model().apply { values[0][2] = longValue }
        val table = table(model)

        assertEquals(longValue, InlineTableEditing.truncatedCellText(table, 0, 2))
        assertNull("Short values need no full-text tooltip", InlineTableEditing.truncatedCellText(table, 1, 2))

        table.doubleClick(0, 2)

        assertTrue("A truncated long value must still enter in-place editing", table.isEditing)
        assertEquals(longValue, InlineTableEditing.editorText(table))
    }

    fun testTooltipEscapesMarkupAndKeepsLineBreaks() {
        val html = InlineTableEditing.tooltipHtml("<b>a</b> & b" + MULTI_LINE, "Hint")!!

        assertTrue(html, html.contains("&lt;b&gt;a&lt;/b&gt; &amp; b"))
        assertTrue(html, html.contains("multi<br>line"))
        assertTrue(html, html.contains("<hr>Hint"))
        assertNull(InlineTableEditing.tooltipHtml(null, null))
    }

    fun testCellTooltipTakesPrecedenceOverColumnTooltip() {
        val table =
            RowHighlightTable(Model(), tooltipForCell = { row, _ -> "cell $row".takeIf { row == 0 } }) { "column" }.apply {
                setSize(600, 200)
                doLayout()
            }

        fun tooltipAt(row: Int): String? {
            val cell = table.getCellRect(row, 2, true)
            return table.getToolTipText(
                MouseEvent(table, MouseEvent.MOUSE_MOVED, 0L, 0, cell.centerX.toInt(), cell.centerY.toInt(), 0, false),
            )
        }

        assertEquals("cell 0", tooltipAt(0))
        assertEquals("column", tooltipAt(1))

        val cell = table.getCellRect(0, 2, true)
        val location =
            table.getToolTipLocation(
                MouseEvent(table, MouseEvent.MOUSE_MOVED, 0L, 0, cell.centerX.toInt(), cell.centerY.toInt(), 0, false),
            )!!
        assertTrue("Cell tooltip opens below the row so it never sits under the cursor", location.y >= cell.y + cell.height)
    }

    private fun javax.swing.JComponent.runKey(stroke: String) {
        val key = inputMap.get(javax.swing.KeyStroke.getKeyStroke(stroke)) ?: error("No binding for $stroke")
        actionMap.get(key).actionPerformed(java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, key.toString()))
    }

    fun testMultiLineValueIsEditedInPlaceAndTheRowGrowsWhileEditing() {
        val multiLine = listOf("first line", "second line", "third line").joinToString("\n")
        val model = Model().apply { values[0][2] = multiLine }
        val table = table(model)
        val defaultHeight = table.getRowHeight(0)

        table.doubleClick(0, 2)

        assertTrue("Multi-line values are edited in place", table.isEditing)
        assertEquals(multiLine, InlineTableEditing.editorText(table))
        assertTrue("The row grows to show the wrapped lines", table.getRowHeight(0) > defaultHeight)

        val area = (table.editorComponent as InlineTableEditing.MultiLineEditorComponent).textArea
        area.caretPosition = area.document.length
        area.runKey("shift ENTER")
        area.document.insertString(area.document.length, "fourth line", null)
        area.runKey("ENTER")

        assertFalse("Enter saves and leaves editing", table.isEditing)
        assertEquals(Triple(0, 2, multiLine + "\nfourth line"), model.edited)
        assertEquals("Row height is restored after editing", defaultHeight, table.getRowHeight(0))
    }

    fun testF2IsATableShortcutThatEditsTheSelectedCell() {
        val table = table(Model())
        table.changeSelection(1, 2, false, false)
        val f2 = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F2, 0)
        // Registered on the table itself so it wins over the keymap's F2 ("Next Highlighted Error").
        val action =
            com.intellij.openapi.actionSystem.ex.ActionUtil
                .getActions(table)
                .single { action ->
                    action.shortcutSet.shortcuts.any { (it as? com.intellij.openapi.actionSystem.KeyboardShortcut)?.firstKeyStroke == f2 }
                }

        action.actionPerformed(
            com.intellij.testFramework.TestActionEvent
                .createTestEvent(action),
        )

        assertTrue("F2 edits the selected value cell", table.isEditing)
        assertEquals(1, table.editingRow)
        assertEquals("Body", InlineTableEditing.editorText(table))
    }

    fun testCancelRestoresRowHeightWithoutSaving() {
        val model = Model().apply { values[0][2] = "word ".repeat(200).trim() }
        val table = table(model)
        val defaultHeight = table.getRowHeight(0)

        table.doubleClick(0, 2)
        InlineTableEditing.setEditorText(table, "changed")
        table.cellEditor.cancelCellEditing()

        assertFalse(table.isEditing)
        assertNull("Cancel never writes", model.edited)
        assertEquals(defaultHeight, table.getRowHeight(0))
    }
}
