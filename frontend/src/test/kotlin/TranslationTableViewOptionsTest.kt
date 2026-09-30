package cg.creamgod45.localization.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Point
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

/** Issue #21 (show or hide language columns) and issue #22 (row context menu targeting). */
class TranslationTableViewOptionsTest : BasePlatformTestCase() {
    private fun table(): JTable {
        val model = DefaultTableModel(arrayOf(arrayOf<Any>("ns", "key", "A", "B", "C", 1)), arrayOf("Namespace", "Key", "en", "ja", "zh_TW", "Usage"))
        return RowHighlightTable(model).apply {
            cellSelectionEnabled = true
            rowSelectionAllowed = true
            columnSelectionAllowed = true
            selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            setSize(800, rowHeight * 3)
            doLayout()
        }
    }

    private fun JTable.visibleModelColumns() = (0 until columnModel.columnCount).map { columnModel.getColumn(it).modelIndex }

    fun testHiddenLanguageColumnsLeaveTheOtherColumnsAndWidthsUntouched() {
        val table = table()
        val visibility = TableColumnVisibility(table)
        table.columnModel.getColumn(4).preferredWidth = 333

        visibility.apply(setOf(2, 3))

        assertEquals(listOf(0, 1, 4, 5), table.visibleModelColumns())
        assertTrue(visibility.isHidden(2))
        assertEquals("Width of a visible column is kept", 333, table.columnModel.getColumn(2).preferredWidth)
    }

    fun testShownColumnReturnsToItsModelPositionWithItsWidth() {
        val table = table()
        val visibility = TableColumnVisibility(table)
        table.columnModel.getColumn(3).preferredWidth = 222

        visibility.apply(setOf(3))
        visibility.apply(setOf(3))
        assertEquals("Applying the same set twice is stable", listOf(0, 1, 2, 4, 5), table.visibleModelColumns())

        visibility.apply(emptySet())

        assertEquals(listOf(0, 1, 2, 3, 4, 5), table.visibleModelColumns())
        assertEquals(222, table.columnModel.getColumn(3).preferredWidth)
        assertFalse(visibility.isHidden(3))
    }

    fun testHiddenLocalesAreIsolatedPerScheme() {
        val store = HiddenLocaleColumns(project)
        store.showAll("scheme-a")
        store.showAll("scheme-b")

        store.setHidden("scheme-a", "ja", hidden = true)
        store.setHidden("scheme-a", "zh_TW", hidden = true)

        assertEquals(setOf("ja", "zh_TW"), store.hidden("scheme-a"))
        assertEquals("Another scheme is not affected", emptySet<String>(), store.hidden("scheme-b"))

        store.setHidden("scheme-a", "ja", hidden = false)
        assertEquals(setOf("zh_TW"), HiddenLocaleColumns(project).hidden("scheme-a"))

        store.showAll("scheme-a")
        assertEquals(emptySet<String>(), store.hidden("scheme-a"))
    }

    fun testKeepOpenPopupTogglesStayOpenAndDriveTheirState() {
        // Shared by Visible Columns and the analysis naming-format exclusions.
        val selected = mutableSetOf("camelCase")
        var cleared = false
        val group =
            KeepOpenTogglePopup.group(
                listOf("camelCase", "snake_case").map { name ->
                    KeepOpenTogglePopup.Toggle(name, { name in selected }, { on -> if (on) selected += name else selected -= name })
                },
                listOf(KeepOpenTogglePopup.Command("Clear") { cleared = true }),
            )
        val actions = group.getChildren(com.intellij.testFramework.TestActionEvent.createTestEvent())
        val items = actions.filterNot { it is com.intellij.openapi.actionSystem.Separator }

        assertEquals(3, items.size)
        assertTrue("A separator divides toggles from commands", actions.any { it is com.intellij.openapi.actionSystem.Separator })
        items.forEach { action ->
            assertEquals(
                "Every item keeps the popup open",
                com.intellij.openapi.actionSystem.KeepPopupOnPerform.Always,
                action.templatePresentation.keepPopupOnPerform,
            )
        }
        val snake = items[1] as com.intellij.openapi.actionSystem.ToggleAction
        val event = com.intellij.testFramework.TestActionEvent.createTestEvent(snake)
        assertFalse(snake.isSelected(event))
        snake.setSelected(event, true)
        assertEquals(setOf("camelCase", "snake_case"), selected)

        items[2].actionPerformed(com.intellij.testFramework.TestActionEvent.createTestEvent(items[2]))
        assertTrue(cleared)
    }

    private fun JTable.centerOf(
        row: Int,
        column: Int,
    ) = getCellRect(row, column, true).let { Point(it.centerX.toInt(), it.centerY.toInt()) }

    fun testContextMenuSelectsThePointedCellSoRowActionsTargetThatRow() {
        val model = DefaultTableModel(arrayOf(arrayOf<Any>("ns", "a", "A"), arrayOf<Any>("ns", "b", "B"), arrayOf<Any>("ns", "c", "C")), arrayOf("Namespace", "Key", "en"))
        val table =
            RowHighlightTable(model).apply {
                cellSelectionEnabled = true
                selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                setSize(600, rowHeight * 4)
                doLayout()
            }
        table.changeSelection(0, 1, false, false)

        val target = TableContextMenuTarget.select(table, table.centerOf(2, 2))

        assertEquals(2 to 2, target)
        assertEquals(listOf(2), table.selectedRows.toList())
        assertEquals(listOf(2), table.selectedColumns.toList())
    }

    fun testContextMenuInsideAMultiRowSelectionKeepsItForBulkActions() {
        val model = DefaultTableModel(arrayOf(arrayOf<Any>("ns", "a", "A"), arrayOf<Any>("ns", "b", "B"), arrayOf<Any>("ns", "c", "C")), arrayOf("Namespace", "Key", "en"))
        val table =
            RowHighlightTable(model).apply {
                cellSelectionEnabled = true
                selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                setSize(600, rowHeight * 4)
                doLayout()
            }
        table.changeSelection(0, 1, false, false)
        table.changeSelection(1, 1, false, true)

        TableContextMenuTarget.select(table, table.centerOf(1, 1))

        assertEquals(listOf(0, 1), table.selectedRows.toList())
        assertNull("Outside the rows nothing is targeted", TableContextMenuTarget.select(table, Point(5, table.rowHeight * 3 + 5)))
    }
}
